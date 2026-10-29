package com.weaksignal.vpp.backend.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weaksignal.vpp.backend.api.dto.DispatchContextSnapshotResponse;
import com.weaksignal.vpp.backend.api.dto.DispatchPlanItemResponse;
import com.weaksignal.vpp.backend.api.dto.DispatchPlanCreateRequest;
import com.weaksignal.vpp.backend.api.ApiException;
import com.weaksignal.vpp.backend.api.dto.DispatchPlanResponse;
import com.weaksignal.vpp.backend.api.dto.ForecastPointResponse;
import com.weaksignal.vpp.backend.api.dto.ForecastSnapshotResponse;
import com.weaksignal.vpp.backend.api.dto.MarketAgentAnalyzeRequest;
import com.weaksignal.vpp.backend.api.dto.MarketAgentRunResponse;
import com.weaksignal.vpp.backend.api.dto.MarketSignalResponse;
import com.weaksignal.vpp.backend.api.dto.ResourceCapabilityResponse;
import com.weaksignal.vpp.backend.api.dto.StorageConstraintResponse;
import com.weaksignal.vpp.backend.config.AiProperties;
import com.weaksignal.vpp.backend.domain.DispatchPlanStatus;
import com.weaksignal.vpp.backend.domain.LinkState;
import com.weaksignal.vpp.backend.domain.QualityCode;
import com.weaksignal.vpp.backend.domain.ResourceType;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.MarketAgentDecisionRepository;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.MarketAgentRunRepository;
import com.weaksignal.vpp.backend.marketagent.MarketAgentType;
import com.weaksignal.vpp.backend.marketagent.MarketAgentErrorCategory;
import com.weaksignal.vpp.backend.marketagent.MarketAgentDecision;
import com.weaksignal.vpp.backend.marketagent.MarketAgentStageStatus;
import com.weaksignal.vpp.backend.marketagent.MarketSafetyDecision;
import com.weaksignal.vpp.backend.marketdispatch.OptimizationStatus;
import com.weaksignal.vpp.backend.marketdispatch.PowerDirection;
import com.weaksignal.vpp.backend.marketdispatch.ResourceDispatchTarget;
import com.weaksignal.vpp.backend.planning.DispatchContextSnapshotService;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;

/**
 * 市场调度多智能体编排服务测试。
 */
class MarketAgentOrchestrationServiceTest {

    private static final int EXPECTED_ORCHESTRATION_AGENT_COUNT = 8;

    private DispatchContextSnapshotService dispatchContextSnapshotService;
    private MarketDispatchPlanningService marketDispatchPlanningService;
    private MarketAgentRunRepository marketAgentRunRepository;
    private MarketAgentDecisionRepository marketAgentDecisionRepository;
    private MarketAgentOrchestrationService marketAgentOrchestrationService;
    private HttpServer deepSeekServer;

    @BeforeEach
    void setUp() {
        dispatchContextSnapshotService = mock(DispatchContextSnapshotService.class);
        marketDispatchPlanningService = mock(MarketDispatchPlanningService.class);
        marketAgentRunRepository = mock(MarketAgentRunRepository.class);
        marketAgentDecisionRepository = mock(MarketAgentDecisionRepository.class);
        when(marketDispatchPlanningService.getPersistedTargets(any())).thenReturn(List.of(resourceTarget(50.0)));
        Clock clock = Clock.fixed(Instant.parse("2026-07-04T08:00:00Z"), ZoneId.of("Asia/Shanghai"));
        AiProperties properties = new AiProperties(
                false,
                6000,
                new AiProperties.ProviderProperties(false, "https://api.deepseek.com", "", "deepseek-v4-flash", 30)
        );
        marketAgentOrchestrationService = new MarketAgentOrchestrationService(
                dispatchContextSnapshotService,
                marketDispatchPlanningService,
                new ObjectMapper().findAndRegisterModules(),
                properties,
                clock,
                marketAgentRunRepository,
                marketAgentDecisionRepository
        );
    }

    @AfterEach
    void tearDown() {
        if (deepSeekServer != null) {
            deepSeekServer.stop(0);
        }
    }

    @Test
    void shouldFallbackToRuleModeWhenDeepSeekDisabled() {
        when(dispatchContextSnapshotService.createSnapshot()).thenReturn(snapshotResponse());
        when(marketDispatchPlanningService.getLatestPlan()).thenReturn(planResponse());

        MarketAgentRunResponse response = marketAgentOrchestrationService.analyze(new MarketAgentAnalyzeRequest(null, null, false));

        assertEquals(EXPECTED_ORCHESTRATION_AGENT_COUNT, response.decisions().size());
        assertTrue(response.decisions().stream()
                .filter(decision -> decision.agentType() == MarketAgentType.FORECAST
                        || decision.agentType() == MarketAgentType.ENVIRONMENT_ASSESSMENT
                        || decision.agentType() == MarketAgentType.MARKET_DISPATCH)
                .allMatch(decision -> decision.fallbackUsed()
                        && decision.stageStatus() == com.weaksignal.vpp.backend.marketagent.MarketAgentStageStatus.RULE_FALLBACK));
        assertTrue(response.decisions().stream()
                .filter(decision -> decision.agentType() == MarketAgentType.MARKET_INTERFACE
                        || decision.agentType() == MarketAgentType.RESOURCE_CAPABILITY
                        || decision.agentType() == MarketAgentType.CONTROL_DECISION
                        || decision.agentType() == MarketAgentType.SECURITY_AUDIT)
                .allMatch(decision -> !decision.fallbackUsed()
                        && decision.stageStatus() == com.weaksignal.vpp.backend.marketagent.MarketAgentStageStatus.SUCCESS));
        assertTrue(response.decisions().stream().map(decision -> decision.agentType()).toList().contains(MarketAgentType.MARKET_DISPATCH));
        assertTrue(response.decisions().stream().map(decision -> decision.agentType()).toList().contains(MarketAgentType.ENVIRONMENT_ASSESSMENT));
        assertTrue(response.decisions().stream()
                .filter(decision -> decision.agentType() == MarketAgentType.RESOURCE_CAPABILITY)
                .findFirst()
                .orElseThrow()
                .explanation()
                .contains("ENVIRONMENT_ASSESSMENT"));
        assertTrue(response.decisions().stream()
                .filter(decision -> decision.agentType() == MarketAgentType.CONTROL_DECISION)
                .findFirst()
                .orElseThrow()
                .explanation()
                .contains("MARKET_DISPATCH"));
        assertEquals("PASSED_WITH_FALLBACK", response.safetyDecision().decisionLevel());
    }

    @Test
    void shouldQueryStoredRunAndDecisions() {
        when(dispatchContextSnapshotService.createSnapshot()).thenReturn(snapshotResponse());
        when(marketDispatchPlanningService.getLatestPlan()).thenReturn(planResponse());
        AtomicReference<com.weaksignal.vpp.backend.infrastructure.persistence.entity.MarketAgentRunEntity> savedRun = new AtomicReference<>();
        AtomicReference<List<com.weaksignal.vpp.backend.infrastructure.persistence.entity.MarketAgentDecisionEntity>> savedDecisions = new AtomicReference<>(List.of());
        when(marketAgentRunRepository.save(any())).thenAnswer((Answer<com.weaksignal.vpp.backend.infrastructure.persistence.entity.MarketAgentRunEntity>) invocation -> {
            com.weaksignal.vpp.backend.infrastructure.persistence.entity.MarketAgentRunEntity entity = invocation.getArgument(0);
            savedRun.set(entity);
            return entity;
        });
        when(marketAgentDecisionRepository.saveAll(anyList())).thenAnswer((Answer<List<com.weaksignal.vpp.backend.infrastructure.persistence.entity.MarketAgentDecisionEntity>>) invocation -> {
            List<com.weaksignal.vpp.backend.infrastructure.persistence.entity.MarketAgentDecisionEntity> entities = invocation.getArgument(0);
            savedDecisions.set(entities);
            return entities;
        });

        MarketAgentRunResponse response = marketAgentOrchestrationService.analyze(new MarketAgentAnalyzeRequest(null, null, true));
        when(marketAgentRunRepository.findByRunId(response.runId())).thenReturn(Optional.of(savedRun.get()));
        when(marketAgentDecisionRepository.findByRunIdOrderByCreatedAtAsc(response.runId())).thenReturn(savedDecisions.get());

        assertEquals(response.runId(), marketAgentOrchestrationService.getRun(response.runId()).runId());
        assertEquals(EXPECTED_ORCHESTRATION_AGENT_COUNT, marketAgentOrchestrationService.getDecisions(response.runId()).size());
    }

    @Test
    void shouldCreatePlanForCurrentSnapshotWhenLatestPlanUsesAnotherSnapshot() {
        when(dispatchContextSnapshotService.createSnapshot()).thenReturn(snapshotResponse());
        when(marketDispatchPlanningService.getLatestPlan()).thenReturn(planResponse("old-context"));
        when(marketDispatchPlanningService.create(any(DispatchPlanCreateRequest.class))).thenReturn(planResponse("context-1"));

        MarketAgentRunResponse response = marketAgentOrchestrationService.analyze(new MarketAgentAnalyzeRequest(null, null, false));

        assertEquals("PASSED_WITH_FALLBACK", response.safetyDecision().decisionLevel());
        assertTrue(response.decisions().stream().allMatch(decision -> "context-1".equals(decision.snapshotId())));
        verify(marketDispatchPlanningService).create(new DispatchPlanCreateRequest("context-1", false));
    }

    @Test
    void shouldRejectSpecifiedPlanWhenSnapshotDoesNotMatch() {
        when(dispatchContextSnapshotService.createSnapshot()).thenReturn(snapshotResponse());
        when(marketDispatchPlanningService.getPlan("old-plan")).thenReturn(planResponse("old-context"));

        ApiException exception = assertThrows(
                ApiException.class,
                () -> marketAgentOrchestrationService.analyze(new MarketAgentAnalyzeRequest(null, "old-plan", false))
        );

        assertEquals("DISPATCH_PLAN_SNAPSHOT_MISMATCH", exception.getCode());
    }

    @Test
    void shouldClassifyProviderHttpErrorsPrecisely() throws Exception {
        assertEquals(MarketAgentErrorCategory.AUTH_ERROR, classifyHttpError(401));
        assertEquals(MarketAgentErrorCategory.AUTH_ERROR, classifyHttpError(403));
        assertEquals(MarketAgentErrorCategory.PROVIDER_ENDPOINT_NOT_FOUND, classifyHttpError(404));
        assertEquals(MarketAgentErrorCategory.RATE_LIMIT, classifyHttpError(429));
        assertEquals(MarketAgentErrorCategory.PROVIDER_5XX, classifyHttpError(500));
    }

    /**
     * 上游模型服务失败时，应将状态码和脱敏错误信息保存在规则回退决策中。
     */
    @Test
    void shouldPreserveProviderFailureDetailWhenDeepSeekReturnsServiceError() throws Exception {
        startDeepSeekErrorServer(503, "server_overloaded", "模型服务暂时繁忙");
        marketAgentOrchestrationService = deepSeekEnabledService();
        when(dispatchContextSnapshotService.createSnapshot()).thenReturn(snapshotResponse());
        when(marketDispatchPlanningService.getLatestPlan()).thenReturn(planResponse());

        MarketAgentRunResponse response = marketAgentOrchestrationService.analyze(new MarketAgentAnalyzeRequest(null, null, false));
        MarketAgentDecision forecastDecision = decisionOf(response.decisions(), MarketAgentType.FORECAST);

        assertEquals(MarketAgentStageStatus.RULE_FALLBACK, forecastDecision.stageStatus());
        assertEquals(MarketAgentErrorCategory.PROVIDER_5XX, forecastDecision.errorCategory());
        assertEquals(503, forecastDecision.providerStatusCode());
        assertEquals("server_overloaded", forecastDecision.providerErrorCode());
        assertEquals("模型服务暂时繁忙", forecastDecision.providerErrorMessage());
    }

    @Test
    void shouldAllowTrustedS4ResourceForControlDecision() throws Exception {
        assertEquals(
                MarketAgentErrorCategory.NONE,
                validateControlInput(snapshotWithCapabilities(capability(LinkState.S4, true, 70.0)), planResponse())
        );
    }

    @Test
    void shouldBlockUntrustedS4ResourceForControlDecision() throws Exception {
        assertEquals(
                MarketAgentErrorCategory.CONSTRAINT_VIOLATION,
                validateControlInput(snapshotWithCapabilities(capability(LinkState.S4, false, 70.0)), planResponse())
        );
    }

    @Test
    void shouldBlockZeroCapacityS4ResourceForControlDecision() throws Exception {
        assertEquals(
                MarketAgentErrorCategory.CONSTRAINT_VIOLATION,
                validateControlInput(snapshotWithCapabilities(capability(LinkState.S4, true, 0.0)), planResponse())
        );
    }

    @Test
    void shouldAllowTrustedS4ResourceWhenS3ResourceExists() throws Exception {
        assertEquals(
                MarketAgentErrorCategory.NONE,
                validateControlInput(
                        snapshotWithCapabilities(
                                capability(LinkState.S3, false, 0.0),
                                capability(LinkState.S4, true, 70.0)
                        ),
                        planResponse()
                )
        );
    }

    @Test
    void shouldBlockControlDecisionWhenAllResourcesAreLostOrZeroCapacity() throws Exception {
        assertEquals(
                MarketAgentErrorCategory.CONSTRAINT_VIOLATION,
                validateControlInput(
                        snapshotWithCapabilities(
                                capability(LinkState.S3, false, 0.0),
                                capability(LinkState.S4, true, 0.0)
                        ),
                        planResponse()
                )
        );
    }

    @Test
    void shouldBlockEmptyOrZeroPowerCandidatePlan() throws Exception {
        DispatchPlanItemResponse zeroPowerItem = planItem(0.0);
        when(marketDispatchPlanningService.getPersistedTargets(any())).thenReturn(List.of());

        assertEquals(
                MarketAgentErrorCategory.CONSTRAINT_VIOLATION,
                validateControlInput(snapshotResponse(), planResponse(List.of()))
        );
        assertEquals(
                MarketAgentErrorCategory.CONSTRAINT_VIOLATION,
                validateControlInput(snapshotResponse(), planResponse(List.of(zeroPowerItem)))
        );
    }

    @Test
    void shouldBlockCandidatePlanOutsideResourceCapabilityBoundary() throws Exception {
        when(marketDispatchPlanningService.getPersistedTargets(any())).thenReturn(List.of(resourceTarget(160.0)));

        assertEquals(
                MarketAgentErrorCategory.CONSTRAINT_VIOLATION,
                validateControlInput(snapshotResponse(), planResponse(List.of(planItem(160.0))))
        );
        assertEquals(
                MarketAgentErrorCategory.CONSTRAINT_VIOLATION,
                validateControlInput(snapshotResponse(), planResponse(List.of(planItemWithGridImport(10.0))))
        );
    }

    @Test
    void shouldRetryInvalidDeepSeekActionAndUseLegalActionWithoutFallback() throws Exception {
        List<String> requests = new CopyOnWriteArrayList<>();
        startDeepSeekServer(requests, false);
        MarketAgentOrchestrationService deepSeekService = deepSeekEnabledService();
        when(dispatchContextSnapshotService.createSnapshot()).thenReturn(snapshotResponse());
        when(marketDispatchPlanningService.getLatestPlan()).thenReturn(planResponse());

        MarketAgentRunResponse response = deepSeekService.analyze(new MarketAgentAnalyzeRequest(null, null, false));
        MarketAgentDecision environmentDecision = decisionOf(response.decisions(), MarketAgentType.ENVIRONMENT_ASSESSMENT);

        assertFalse(environmentDecision.fallbackUsed());
        assertEquals(MarketAgentStageStatus.SUCCESS, environmentDecision.stageStatus());
        assertEquals(1, environmentDecision.retryCount());
        assertTrue(requests.stream().anyMatch(request -> request.contains("action 必须原样选择其中一个编码")));
    }

    @Test
    void shouldFallbackAfterDeepSeekActionRepairRemainsInvalid() throws Exception {
        startDeepSeekServer(new CopyOnWriteArrayList<>(), true);
        MarketAgentOrchestrationService deepSeekService = deepSeekEnabledService();
        when(dispatchContextSnapshotService.createSnapshot()).thenReturn(snapshotResponse());
        when(marketDispatchPlanningService.getLatestPlan()).thenReturn(planResponse());

        MarketAgentRunResponse response = deepSeekService.analyze(new MarketAgentAnalyzeRequest(null, null, false));
        MarketAgentDecision environmentDecision = decisionOf(response.decisions(), MarketAgentType.ENVIRONMENT_ASSESSMENT);

        assertTrue(environmentDecision.fallbackUsed());
        assertEquals(MarketAgentStageStatus.RULE_FALLBACK, environmentDecision.stageStatus());
        assertEquals(MarketAgentErrorCategory.INVALID_ACTION, environmentDecision.errorCategory());
        assertEquals(1, environmentDecision.retryCount());
    }

    @Test
    void shouldRejectSafetyGateWhenControlDecisionIsBlocked() {
        when(dispatchContextSnapshotService.createSnapshot()).thenReturn(snapshotWithCapabilities(capability(LinkState.S4, false, 70.0)));
        when(marketDispatchPlanningService.getLatestPlan()).thenReturn(planResponse());

        MarketAgentRunResponse response = marketAgentOrchestrationService.analyze(new MarketAgentAnalyzeRequest(null, null, false));
        MarketAgentDecision controlDecision = decisionOf(response.decisions(), MarketAgentType.CONTROL_DECISION);
        MarketAgentDecision securityDecision = decisionOf(response.decisions(), MarketAgentType.SECURITY_AUDIT);

        assertEquals(MarketAgentStageStatus.BLOCKED, controlDecision.stageStatus());
        assertEquals(List.of("BLOCK_UNSAFE_ACTIONS"), securityDecision.candidateActions());
        assertEquals("REJECTED", response.safetyDecision().decisionLevel());
    }

    @Test
    void shouldRejectInconsistentSnapshot() throws Exception {
        MarketSafetyDecision decision = evaluateSafety(
                planResponse(),
                List.of(new com.weaksignal.vpp.backend.marketagent.MarketAgentDecision(
                        "decision-1",
                        MarketAgentType.CONTROL_DECISION,
                        "other-snapshot",
                        OffsetDateTime.parse("2026-07-04T16:00:00+08:00"),
                        OffsetDateTime.parse("2026-07-04T16:30:00+08:00"),
                        0.92,
                        List.of("e1"),
                        "rule-v1",
                        "rule",
                        "rule-fallback",
                        List.of("ISSUE_SAFE_DISPATCH_DECISION"),
                        "LOW",
                        false,
                        "测试快照不一致",
                        false
                ))
        );

        assertFalse(decision.passed());
        assertTrue(decision.explanation().contains("输入快照不一致"));
    }

    @Test
    void shouldRejectDisallowedAction() throws Exception {
        MarketSafetyDecision decision = evaluateSafety(
                planResponse(),
                List.of(new com.weaksignal.vpp.backend.marketagent.MarketAgentDecision(
                        "decision-1",
                        MarketAgentType.FORECAST,
                        "context-1",
                        OffsetDateTime.parse("2026-07-04T16:00:00+08:00"),
                        OffsetDateTime.parse("2026-07-04T16:30:00+08:00"),
                        0.92,
                        List.of("e1"),
                        "rule-v1",
                        "deepseek",
                        "deepseek-v4-flash",
                        List.of("ISSUE_SAFE_DISPATCH_DECISION"),
                        "LOW",
                        false,
                        "测试越权动作",
                        false
                ))
        );

        assertFalse(decision.passed());
        assertTrue(decision.explanation().contains("越权动作"));
    }

    @Test
    void shouldNormalizeLegacyHighRiskApprovalDecision() throws Exception {
        MarketSafetyDecision decision = normalizeSafetyDecision(new MarketSafetyDecision(
                null,
                false,
                true,
                List.of("BLOCK_UNSAFE_ACTIONS"),
                "存在高风险建议，必须人工审批"
        ));

        assertEquals("DEGRADED", decision.decisionLevel());
        assertTrue(decision.passed());
        assertTrue(decision.fallbackUsed());
        assertTrue(decision.explanation().contains("兼容旧版高风险审批记录"));
    }

    @Test
    void shouldAllowNonCriticalFallbackToContinueAndPassSafetyGate() throws Exception {
        List<MarketAgentDecision> decisions = completeDecisions();
        int environmentIndex = decisions.indexOf(decisionOf(decisions, MarketAgentType.ENVIRONMENT_ASSESSMENT));
        decisions.set(environmentIndex, decision(
                MarketAgentType.ENVIRONMENT_ASSESSMENT,
                List.of("APPLY_ENVIRONMENT_CORRECTION"),
                "MEDIUM",
                true,
                MarketAgentStageStatus.RULE_FALLBACK,
                MarketAgentErrorCategory.INVALID_ACTION,
                true
        ));

        MarketSafetyDecision safetyDecision = evaluateSafety(planResponse(), decisions);

        assertTrue(decisionOf(decisions, MarketAgentType.ENVIRONMENT_ASSESSMENT).nextStageAllowed());
        assertTrue(safetyDecision.passed());
        assertEquals("PASSED_WITH_FALLBACK", safetyDecision.decisionLevel());
    }

    @Test
    void shouldDegradeWhenCriticalFallbackProducesValidDeterministicOutput() throws Exception {
        List<MarketAgentDecision> decisions = completeDecisions();
        int marketIndex = decisions.indexOf(decisionOf(decisions, MarketAgentType.MARKET_INTERFACE));
        decisions.set(marketIndex, decision(
                MarketAgentType.MARKET_INTERFACE,
                List.of("VERIFY_MARKET_SIGNAL_VERSION"),
                "MEDIUM",
                true,
                MarketAgentStageStatus.RULE_FALLBACK,
                MarketAgentErrorCategory.TIMEOUT,
                true
        ));

        MarketSafetyDecision safetyDecision = evaluateSafety(planResponse(), decisions);

        assertTrue(safetyDecision.passed());
        assertEquals("DEGRADED", safetyDecision.decisionLevel());
    }

    @Test
    void shouldNotDowngradeSafetyDecisionOnlyBecauseModelReportedHighRisk() throws Exception {
        List<MarketAgentDecision> decisions = completeDecisions();
        int forecastIndex = decisions.indexOf(decisionOf(decisions, MarketAgentType.FORECAST));
        decisions.set(forecastIndex, decision(
                MarketAgentType.FORECAST,
                List.of("USE_P50_FORECAST"),
                "HIGH",
                false,
                MarketAgentStageStatus.SUCCESS,
                MarketAgentErrorCategory.NONE,
                true
        ));

        MarketSafetyDecision safetyDecision = evaluateSafety(planResponse(), decisions);

        assertTrue(safetyDecision.passed());
        assertEquals("PASSED", safetyDecision.decisionLevel());
    }

    @Test
    void shouldRejectWhenCriticalStageIsBlocked() throws Exception {
        List<MarketAgentDecision> decisions = completeDecisions();
        int controlIndex = decisions.indexOf(decisionOf(decisions, MarketAgentType.CONTROL_DECISION));
        decisions.set(controlIndex, decision(
                MarketAgentType.CONTROL_DECISION,
                List.of(),
                "HIGH",
                false,
                MarketAgentStageStatus.BLOCKED,
                MarketAgentErrorCategory.CONSTRAINT_VIOLATION,
                false
        ));

        MarketSafetyDecision safetyDecision = evaluateSafety(planResponse(), decisions);

        assertFalse(safetyDecision.passed());
        assertEquals("REJECTED", safetyDecision.decisionLevel());
    }

    @Test
    void shouldAcceptOnlyStrictOutputContractAndNormalizeKnownAlias() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();

        assertTrue(hasValidOutputContractShape(objectMapper.readTree("""
                {
                  "action":"环境修正",
                  "riskLevel":"LOW",
                  "confidence":0.8,
                  "evidence":["pv-gobi-01"],
                  "reason":"环境因子已校验",
                  "nextStageAllowed":true
                }
                """)));
        assertFalse(hasValidOutputContractShape(objectMapper.readTree("""
                {
                  "action":"APPLY_ENVIRONMENT_CORRECTION",
                  "riskLevel":"LOW",
                  "confidence":0.8,
                  "evidence":["pv-gobi-01"],
                  "reason":"环境因子已校验",
                  "nextStageAllowed":true,
                  "unexpected":"禁止字段"
                }
                """)));
        assertEquals(
                "APPLY_ENVIRONMENT_CORRECTION",
                normalizeAction(MarketAgentType.ENVIRONMENT_ASSESSMENT, "环境修正")
        );
        assertEquals(null, normalizeAction(MarketAgentType.ENVIRONMENT_ASSESSMENT, "自由文本动作"));
    }

    private MarketSafetyDecision evaluateSafety(
            DispatchPlanResponse plan,
            List<com.weaksignal.vpp.backend.marketagent.MarketAgentDecision> decisions
    ) throws Exception {
        Method method = MarketAgentOrchestrationService.class.getDeclaredMethod("evaluateSafety", DispatchPlanResponse.class, List.class);
        method.setAccessible(true);
        return (MarketSafetyDecision) method.invoke(marketAgentOrchestrationService, plan, decisions);
    }

    private MarketSafetyDecision normalizeSafetyDecision(MarketSafetyDecision safetyDecision) throws Exception {
        Method method = MarketAgentOrchestrationService.class.getDeclaredMethod("normalizeSafetyDecision", MarketSafetyDecision.class);
        method.setAccessible(true);
        return (MarketSafetyDecision) method.invoke(marketAgentOrchestrationService, safetyDecision);
    }

    private MarketAgentErrorCategory classifyHttpError(int statusCode) throws Exception {
        Method method = MarketAgentOrchestrationService.class.getDeclaredMethod("classifyHttpError", int.class);
        method.setAccessible(true);
        return (MarketAgentErrorCategory) method.invoke(marketAgentOrchestrationService, statusCode);
    }

    private boolean hasValidOutputContractShape(com.fasterxml.jackson.databind.JsonNode decisionNode) throws Exception {
        Method method = MarketAgentOrchestrationService.class.getDeclaredMethod("hasValidOutputContractShape", com.fasterxml.jackson.databind.JsonNode.class);
        method.setAccessible(true);
        return (boolean) method.invoke(marketAgentOrchestrationService, decisionNode);
    }

    private String normalizeAction(MarketAgentType agentType, String rawAction) throws Exception {
        Method method = MarketAgentOrchestrationService.class.getDeclaredMethod("normalizeAction", MarketAgentType.class, String.class);
        method.setAccessible(true);
        return (String) method.invoke(marketAgentOrchestrationService, agentType, rawAction);
    }

    private MarketAgentErrorCategory validateControlInput(
            DispatchContextSnapshotResponse snapshot,
            DispatchPlanResponse plan
    ) throws Exception {
        Method method = MarketAgentOrchestrationService.class.getDeclaredMethod(
                "validateStageInput",
                MarketAgentType.class,
                DispatchContextSnapshotResponse.class,
                DispatchPlanResponse.class,
                OffsetDateTime.class
        );
        method.setAccessible(true);
        return (MarketAgentErrorCategory) method.invoke(
                marketAgentOrchestrationService,
                MarketAgentType.CONTROL_DECISION,
                snapshot,
                plan,
                OffsetDateTime.parse("2026-07-04T16:00:00+08:00")
        );
    }

    private MarketAgentOrchestrationService deepSeekEnabledService() {
        AiProperties properties = new AiProperties(
                true,
                6000,
                new AiProperties.ProviderProperties(
                        true,
                        "http://127.0.0.1:" + deepSeekServer.getAddress().getPort(),
                        "test-key",
                        "deepseek-test-model",
                        5
                )
        );
        return new MarketAgentOrchestrationService(
                dispatchContextSnapshotService,
                marketDispatchPlanningService,
                new ObjectMapper().findAndRegisterModules(),
                properties,
                Clock.fixed(Instant.parse("2026-07-04T08:00:00Z"), ZoneId.of("Asia/Shanghai")),
                marketAgentRunRepository,
                marketAgentDecisionRepository
        );
    }

    private void startDeepSeekServer(List<String> requests, boolean keepEnvironmentActionInvalid) throws Exception {
        AtomicInteger environmentRequestCount = new AtomicInteger();
        deepSeekServer = HttpServer.create(new InetSocketAddress(0), 0);
        deepSeekServer.createContext("/chat/completions", exchange -> {
            try {
                String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                requests.add(requestBody);
                String action = resolveDeepSeekAction(
                        requestBody,
                        requestBody.contains("ENVIRONMENT_ASSESSMENT") ? environmentRequestCount.incrementAndGet() : 0,
                        keepEnvironmentActionInvalid
                );
                byte[] responseBytes = deepSeekResponse(action).getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, responseBytes.length);
                exchange.getResponseBody().write(responseBytes);
            } catch (Exception exception) {
                exchange.sendResponseHeaders(500, -1);
            } finally {
                exchange.close();
            }
        });
        deepSeekServer.start();
    }

    /**
     * 启动固定返回模型服务错误的本地测试服务。
     *
     * @param statusCode HTTP 状态码
     * @param errorCode 上游错误码
     * @param errorMessage 上游错误摘要
     */
    private void startDeepSeekErrorServer(int statusCode, String errorCode, String errorMessage) throws Exception {
        deepSeekServer = HttpServer.create(new InetSocketAddress(0), 0);
        deepSeekServer.createContext("/chat/completions", exchange -> {
            try {
                String response = new ObjectMapper().writeValueAsString(java.util.Map.of(
                        "error", java.util.Map.of("code", errorCode, "message", errorMessage)
                ));
                byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(statusCode, responseBytes.length);
                exchange.getResponseBody().write(responseBytes);
            } finally {
                exchange.close();
            }
        });
        deepSeekServer.start();
    }

    private String resolveDeepSeekAction(String requestBody, int requestCount, boolean keepEnvironmentActionInvalid) {
        if (requestBody.contains("ENVIRONMENT_ASSESSMENT")) {
            return keepEnvironmentActionInvalid || requestCount == 1
                    ? "UNKNOWN_ACTION"
                    : "APPLY_ENVIRONMENT_CORRECTION";
        }
        if (requestBody.contains("FORECAST")) {
            return "USE_P50_FORECAST";
        }
        return "GENERATE_MARKET_OPTIMAL_PLAN";
    }

    private String deepSeekResponse(String action) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String decision = mapper.writeValueAsString(java.util.Map.of(
                "action", action,
                "riskLevel", "LOW",
                "confidence", 0.9,
                "evidence", List.of("test-evidence"),
                "reason", "测试模型输出",
                "nextStageAllowed", true
        ));
        return mapper.writeValueAsString(java.util.Map.of(
                "choices", List.of(java.util.Map.of("message", java.util.Map.of("content", decision)))
        ));
    }

    private DispatchPlanResponse planResponse() {
        return planResponse("context-1", List.of(planItem(20.0)));
    }

    private List<MarketAgentDecision> completeDecisions() {
        return new java.util.ArrayList<>(List.of(
                decision(MarketAgentType.MARKET_INTERFACE, List.of("VERIFY_MARKET_SIGNAL_VERSION"), "LOW", false, MarketAgentStageStatus.SUCCESS, MarketAgentErrorCategory.NONE, true),
                decision(MarketAgentType.FORECAST, List.of("USE_P50_FORECAST"), "LOW", false, MarketAgentStageStatus.SUCCESS, MarketAgentErrorCategory.NONE, true),
                decision(MarketAgentType.ENVIRONMENT_ASSESSMENT, List.of("APPLY_ENVIRONMENT_CORRECTION"), "LOW", false, MarketAgentStageStatus.SUCCESS, MarketAgentErrorCategory.NONE, true),
                decision(MarketAgentType.RESOURCE_CAPABILITY, List.of("FREEZE_RESOURCE_CAPABILITY_BOUNDARY"), "LOW", false, MarketAgentStageStatus.SUCCESS, MarketAgentErrorCategory.NONE, true),
                decision(MarketAgentType.MARKET_DISPATCH, List.of("GENERATE_MARKET_OPTIMAL_PLAN"), "LOW", false, MarketAgentStageStatus.SUCCESS, MarketAgentErrorCategory.NONE, true),
                decision(MarketAgentType.COMMUNICATION_QUALITY, List.of("APPLY_S0_S4_CAPACITY_FACTOR"), "LOW", false, MarketAgentStageStatus.SUCCESS, MarketAgentErrorCategory.NONE, true),
                decision(MarketAgentType.CONTROL_DECISION, List.of("ISSUE_SAFE_DISPATCH_DECISION"), "LOW", false, MarketAgentStageStatus.SUCCESS, MarketAgentErrorCategory.NONE, true),
                decision(MarketAgentType.SECURITY_AUDIT, List.of("ALLOW_SAFE_ACTIONS"), "LOW", false, MarketAgentStageStatus.SUCCESS, MarketAgentErrorCategory.NONE, true)
        ));
    }

    private MarketAgentDecision decisionOf(List<MarketAgentDecision> decisions, MarketAgentType agentType) {
        return decisions.stream().filter(decision -> decision.agentType() == agentType).findFirst().orElseThrow();
    }

    private MarketAgentDecision decision(
            MarketAgentType agentType,
            List<String> actions,
            String riskLevel,
            boolean fallbackUsed,
            MarketAgentStageStatus stageStatus,
            MarketAgentErrorCategory errorCategory,
            boolean nextStageAllowed
    ) {
        OffsetDateTime now = OffsetDateTime.parse("2026-07-04T16:00:00+08:00");
        return new MarketAgentDecision(
                "decision-" + agentType.name(),
                agentType,
                "context-1",
                now,
                now.plusMinutes(30),
                0.9,
                List.of("evidence:" + agentType.name()),
                "rule-v1",
                fallbackUsed ? "rule" : "deepseek",
                fallbackUsed ? "rule-fallback" : "deepseek-v4-pro",
                actions,
                riskLevel,
                false,
                "测试决策",
                fallbackUsed,
                stageStatus,
                0,
                errorCategory,
                "input-v1",
                "output-v1",
                nextStageAllowed
        );
    }

    private DispatchPlanResponse planResponse(String snapshotId) {
        return planResponse(snapshotId, List.of(planItem(20.0)));
    }

    private DispatchPlanResponse planResponse(List<DispatchPlanItemResponse> items) {
        return planResponse("context-1", items);
    }

    private DispatchPlanResponse planResponse(String snapshotId, List<DispatchPlanItemResponse> items) {
        OffsetDateTime now = OffsetDateTime.parse("2026-07-04T16:00:00+08:00");
        return new DispatchPlanResponse(
                "dispatch-plan-1",
                snapshotId,
                60,
                false,
                DispatchPlanStatus.APPROVED,
                now,
                now,
                now.plusHours(24),
                "TEST_CONTEXT",
                QualityCode.GOOD,
                "dispatch-plan-v1",
                now,
                items
        );
    }

    private DispatchPlanItemResponse planItem(double renewableCurtailment) {
        return planItem(0.0, renewableCurtailment);
    }

    private DispatchPlanItemResponse planItemWithGridImport(double gridImportPower) {
        return planItem(gridImportPower, 0.0);
    }

    private DispatchPlanItemResponse planItem(double gridImportPower, double renewableCurtailment) {
        OffsetDateTime now = OffsetDateTime.parse("2026-07-04T16:00:00+08:00");
        return new DispatchPlanItemResponse(
                "item-1",
                "context-1",
                now,
                now.plusHours(1),
                0.0,
                gridImportPower,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                renewableCurtailment,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "TEST",
                1
        );
    }

    private DispatchContextSnapshotResponse snapshotWithCapabilities(ResourceCapabilityResponse... resourceCapabilities) {
        DispatchContextSnapshotResponse snapshot = snapshotResponse();
        return new DispatchContextSnapshotResponse(
                snapshot.snapshotId(),
                snapshot.generatedAt(),
                snapshot.validFrom(),
                snapshot.validUntil(),
                snapshot.source(),
                snapshot.qualityCode(),
                snapshot.version(),
                snapshot.receivedAt(),
                snapshot.intervalMinutes(),
                snapshot.marketSignal(),
                snapshot.forecastSnapshot(),
                List.of(resourceCapabilities)
        );
    }

    private ResourceCapabilityResponse capability(LinkState linkState, boolean dataTrusted, double maximumPower) {
        OffsetDateTime now = OffsetDateTime.parse("2026-07-04T16:00:00+08:00");
        return new ResourceCapabilityResponse(
                "cap-pv-" + linkState + "-" + maximumPower,
                "cap-group-1",
                "pv-gobi-01",
                ResourceType.PHOTOVOLTAIC,
                70.0,
                maximumPower,
                0.0,
                maximumPower,
                40.0,
                40.0,
                maximumPower,
                null,
                linkState,
                2L,
                dataTrusted ? 0.76 : 0.0,
                dataTrusted,
                dataTrusted ? "" : "测试不可用",
                now,
                now,
                now.plusHours(1),
                "REALTIME_TELEMETRY",
                QualityCode.GOOD,
                "capability-v1",
                now
        );
    }

    private ResourceDispatchTarget resourceTarget(double targetPower) {
        return new ResourceDispatchTarget(
                "pv-gobi-01",
                ResourceType.PHOTOVOLTAIC,
                targetPower,
                PowerDirection.POSITIVE,
                OptimizationStatus.OPTIMAL,
                1,
                "context-1",
                java.util.Map.of("test", true),
                OffsetDateTime.parse("2026-07-04T16:00:00+08:00")
        );
    }

    private DispatchContextSnapshotResponse snapshotResponse() {
        OffsetDateTime now = OffsetDateTime.parse("2026-07-04T16:00:00+08:00");
        return new DispatchContextSnapshotResponse(
                "context-1",
                now,
                now,
                now.plusHours(1),
                "TEST_CONTEXT",
                QualityCode.GOOD,
                "dispatch-context-v1",
                now,
                60,
                new MarketSignalResponse(
                        "market-1",
                        "TEST_MARKET",
                        now,
                        now.plusHours(1),
                        280.0,
                        new BigDecimal("0.452000"),
                        new BigDecimal("0.486000"),
                        new BigDecimal("0.518000"),
                        80.0,
                        20.0,
                        12.0,
                        10.0,
                        now,
                        now,
                        now.plusHours(1),
                        "TEST_MARKET_DATA",
                        QualityCode.GOOD,
                        "market-v1",
                        now
                ),
                new ForecastSnapshotResponse(
                        "forecast-1",
                        60,
                        "forecast-v1",
                        List.of(new ForecastPointResponse(
                                now,
                                now.plusHours(1),
                                250.0,
                                260.0,
                                270.0,
                                60.0,
                                70.0,
                                80.0,
                                50.0,
                                60.0,
                                70.0,
                                new BigDecimal("0.438000"),
                                new BigDecimal("0.486000"),
                                new BigDecimal("0.536000")
                        )),
                        now,
                        now,
                        now.plusHours(1),
                        "TEST_FORECAST_DATA",
                        QualityCode.GOOD,
                        "forecast-v1",
                        now
                ),
                List.of(new ResourceCapabilityResponse(
                        "cap-pv",
                        "cap-group-1",
                        "pv-gobi-01",
                        ResourceType.PHOTOVOLTAIC,
                        70.0,
                        70.0,
                        0.0,
                        70.0,
                        40.0,
                        40.0,
                        70.0,
                        new StorageConstraintResponse(
                                55.0,
                                10.0,
                                90.0,
                                240.0,
                                120.0,
                                120.0,
                                0.95,
                                0.95,
                                new BigDecimal("0.080000"),
                                0.96,
                                12.0,
                                10.0
                        ),
                        LinkState.S0,
                        2L,
                        0.98,
                        true,
                        "",
                        now,
                        now,
                        now.plusHours(1),
                        "REALTIME_TELEMETRY",
                        QualityCode.GOOD,
                        "capability-v1",
                        now
                ))
        );
    }
}

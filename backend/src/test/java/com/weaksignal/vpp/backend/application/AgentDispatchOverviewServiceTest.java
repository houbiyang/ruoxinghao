package com.weaksignal.vpp.backend.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.weaksignal.vpp.backend.api.dto.AgentDispatchOverviewResponse;
import com.weaksignal.vpp.backend.api.dto.DispatchCommandResponse;
import com.weaksignal.vpp.backend.api.dto.DispatchPlanExecutionResponse;
import com.weaksignal.vpp.backend.api.dto.DispatchPlanItemResponse;
import com.weaksignal.vpp.backend.api.dto.DispatchPlanResponse;
import com.weaksignal.vpp.backend.api.dto.DispatchPlanTrackingItemResponse;
import com.weaksignal.vpp.backend.api.dto.DispatchPlanTrackingResponse;
import com.weaksignal.vpp.backend.api.dto.ExperimentComparisonResponse;
import com.weaksignal.vpp.backend.api.dto.ExperimentMetricSnapshotResponse;
import com.weaksignal.vpp.backend.api.dto.ExperimentRunResponse;
import com.weaksignal.vpp.backend.api.dto.ExperimentStartResponse;
import com.weaksignal.vpp.backend.api.dto.ExperimentStopResponse;
import com.weaksignal.vpp.backend.api.dto.ExperimentTimelinePointResponse;
import com.weaksignal.vpp.backend.api.dto.ResourceCapabilityResponse;
import com.weaksignal.vpp.backend.api.dto.ScenarioStatusResponse;
import com.weaksignal.vpp.backend.api.dto.SettlementResultResponse;
import com.weaksignal.vpp.backend.api.dto.StationStateResponse;
import com.weaksignal.vpp.backend.api.dto.StorageConstraintResponse;
import com.weaksignal.vpp.backend.domain.ControlMode;
import com.weaksignal.vpp.backend.domain.DispatchPlanStatus;
import com.weaksignal.vpp.backend.domain.DemoScenario;
import com.weaksignal.vpp.backend.domain.LinkState;
import com.weaksignal.vpp.backend.domain.QualityCode;
import com.weaksignal.vpp.backend.domain.ResourceType;
import com.weaksignal.vpp.backend.marketagent.MarketAgentDecision;
import com.weaksignal.vpp.backend.marketagent.MarketAgentType;
import com.weaksignal.vpp.backend.marketagent.MarketSafetyDecision;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 多智能体调度中心聚合服务测试。
 */
class AgentDispatchOverviewServiceTest {

    private DashboardQueryService dashboardQueryService;
    private ScenarioService scenarioService;
    private DispatchService dispatchService;
    private ExperimentService experimentService;
    private ExperimentAnalysisService experimentAnalysisService;
    private MarketDispatchPlanningService marketDispatchPlanningService;
    private MarketSettlementService marketSettlementService;
    private MarketAgentOrchestrationService marketAgentOrchestrationService;
    private com.weaksignal.vpp.backend.capability.ResourceCapabilityService resourceCapabilityService;
    private Clock clock;
    private AgentDispatchOverviewService service;

    @BeforeEach
    void setUp() {
        dashboardQueryService = mock(DashboardQueryService.class);
        scenarioService = mock(ScenarioService.class);
        dispatchService = mock(DispatchService.class);
        experimentService = mock(ExperimentService.class);
        experimentAnalysisService = mock(ExperimentAnalysisService.class);
        marketDispatchPlanningService = mock(MarketDispatchPlanningService.class);
        marketSettlementService = mock(MarketSettlementService.class);
        marketAgentOrchestrationService = mock(MarketAgentOrchestrationService.class);
        resourceCapabilityService = mock(com.weaksignal.vpp.backend.capability.ResourceCapabilityService.class);
        clock = Clock.fixed(Instant.parse("2026-07-05T10:05:00Z"), ZoneId.of("Asia/Shanghai"));
        service = new AgentDispatchOverviewService(
                dashboardQueryService,
                scenarioService,
                dispatchService,
                experimentService,
                experimentAnalysisService,
                marketDispatchPlanningService,
                marketSettlementService,
                marketAgentOrchestrationService,
                resourceCapabilityService,
                clock
        );

        when(dashboardQueryService.getStations()).thenReturn(stations(LinkState.S0));
        when(scenarioService.getCurrentScenario()).thenReturn(new ScenarioStatusResponse(DemoScenario.DEGRADED, ControlMode.EDGE_COORDINATION));
        when(resourceCapabilityService.getLatestCapabilities()).thenReturn(capabilities());
        when(marketDispatchPlanningService.getLatestPlan()).thenReturn(plan());
        when(marketDispatchPlanningService.getTracking("plan-1")).thenReturn(tracking(304, 298.6, -5.4));
        when(marketDispatchPlanningService.getExecution("plan-1")).thenReturn(execution("EXECUTED", "ACCEPTED"));
        when(marketSettlementService.getLatestSettlement()).thenReturn(settlement());
        when(experimentService.getCurrentRun()).thenReturn(null);
        when(experimentService.listLatestRuns(10)).thenReturn(List.of(stoppedRun("run-stopped", "T1_LATENCY_JITTER")));
        when(experimentAnalysisService.getComparison("run-stopped")).thenReturn(comparison("run-stopped", "T1_LATENCY_JITTER"));
        when(experimentAnalysisService.getTimeline("run-stopped")).thenReturn(timeline());
        when(marketAgentOrchestrationService.getDeepSeekStatus()).thenReturn(Map.of(
                "enabled", true,
                "available", true,
                "providerCode", "deepseek"
        ));
        when(marketAgentOrchestrationService.getLatestRun()).thenReturn(agentRun(true, false));
    }

    @Test
    void shouldReturnExplicitIdleExperimentWhenNoRunningRun() {
        AgentDispatchOverviewResponse response = service.getOverview();

        assertFalse(response.experimentStatus().running());
        assertNull(response.experimentStatus().currentRunId());
        assertEquals("REALTIME", response.systemStatus().dataSourceStatus());
    }

    @Test
    void shouldMapRunningExperimentAndTimeline() {
        when(experimentService.getCurrentRun()).thenReturn(runningRun("run-current", "T5_FULL_LOSS_AUTONOMY"));
        when(experimentAnalysisService.getComparison("run-current")).thenReturn(comparison("run-current", "T5_FULL_LOSS_AUTONOMY"));
        when(experimentAnalysisService.getTimeline("run-current")).thenReturn(timeline());
        AgentDispatchOverviewResponse response = service.getOverview();

        assertTrue(response.experimentStatus().running());
        assertEquals("run-current", response.experimentStatus().currentRunId());
        assertEquals("RESULT_COMPARISON", response.executionTimeline().currentStepCode());
        assertEquals(
                "RESULT_COMPARISON",
                response.executionTimeline().steps().stream()
                        .filter(step -> "RUNNING".equals(step.status()))
                        .findFirst()
                        .orElseThrow()
                        .stepCode()
        );
    }

    @Test
    void shouldUseActualExperimentRequestDuringInjection() {
        ExperimentRunResponse injectingRun = new ExperimentRunResponse(
                "run-injecting", "DEV_INVERTER", "逆变器降额", "INJECTING",
                now().minusSeconds(5), null,
                "{\"durationSeconds\":20,\"targetStationIds\":[\"pv-gobi-01\"]}"
        );
        when(experimentService.getCurrentRun()).thenReturn(injectingRun);
        when(experimentAnalysisService.getComparison("run-injecting")).thenReturn(comparison("run-injecting", "DEV_INVERTER"));
        when(experimentAnalysisService.getTimeline("run-injecting")).thenReturn(timeline());

        AgentDispatchOverviewResponse response = service.getOverview();

        assertTrue(response.experimentStatus().running());
        assertEquals(20, response.experimentStatus().durationSeconds());
        assertEquals(List.of("pv-gobi-01"), response.experimentStatus().affectedStationIds());
    }

    @Test
    void shouldExposeAiClosedWithoutBreakingOverview() {
        when(marketAgentOrchestrationService.getDeepSeekStatus()).thenReturn(Map.of(
                "enabled", false,
                "available", false,
                "providerCode", "deepseek"
        ));

        AgentDispatchOverviewResponse response = service.getOverview();

        assertFalse(response.systemStatus().aiEnabled());
        assertFalse(response.systemStatus().aiAvailable());
        assertNotNull(response.resourceTopology().resourceNodes());
    }

    @Test
    void shouldMarkSafetyRejectedDecisionAsNotAdopted() {
        when(marketAgentOrchestrationService.getLatestRun()).thenReturn(agentRun(false, false));

        AgentDispatchOverviewResponse response = service.getOverview();

        assertTrue(response.agentDecisionSummary().rows().stream().noneMatch(AgentDispatchOverviewResponse.AgentDecisionRowResponse::adopted));
        assertFalse(Boolean.TRUE.equals(response.agentDecisionSummary().safetyGatePassed()));
    }

    @Test
    void shouldFillCompatibleRowsForLegacyAgentRun() {
        when(marketAgentOrchestrationService.getLatestRun()).thenReturn(legacyAgentRun());

        AgentDispatchOverviewResponse response = service.getOverview();

        AgentDispatchOverviewResponse.AgentDecisionRowResponse environmentRow = response.agentDecisionSummary().rows().stream()
                .filter(row -> "ENVIRONMENT_ASSESSMENT".equals(row.agentType()))
                .findFirst()
                .orElseThrow();
        AgentDispatchOverviewResponse.AgentDecisionRowResponse resourceRow = response.agentDecisionSummary().rows().stream()
                .filter(row -> "RESOURCE_CAPABILITY".equals(row.agentType()))
                .findFirst()
                .orElseThrow();

        assertTrue(environmentRow.available());
        assertTrue(environmentRow.adopted());
        assertEquals("APPLY_ENVIRONMENT_CORRECTION", environmentRow.suggestedAction());
        assertEquals("规则兼容输出", environmentRow.finalDecision());
        assertTrue(resourceRow.available());
        assertEquals("FREEZE_RESOURCE_CAPABILITY_BOUNDARY", resourceRow.suggestedAction());
        assertEquals("规则兼容输出", resourceRow.finalDecision());
        assertTrue(response.agentDecisionSummary().overallDecision().contains("统一裁决层"));
        assertFalse(response.agentDecisionSummary().overallDecision().contains("兼容旧版高风险审批记录"));
        assertTrue(response.agentDecisionSummary().rows().stream().noneMatch(row -> "暂无建议动作".equals(row.suggestedAction())));
        assertTrue(response.agentDecisionSummary().rows().stream().noneMatch(row -> "暂无裁决".equals(row.finalDecision())));
    }

    @Test
    void shouldDisableControlFlowForS3Node() {
        when(dashboardQueryService.getStations()).thenReturn(stations(LinkState.S3));

        AgentDispatchOverviewResponse response = service.getOverview();

        AgentDispatchOverviewResponse.ControlLinkResponse storageControlLink = response.resourceTopology().controlLinks().stream()
                .filter(link -> "storage-hub-03".equals(link.targetNodeId()))
                .findFirst()
                .orElseThrow();
        assertFalse(storageControlLink.active());
        assertEquals(LinkState.S3, storageControlLink.linkState());
    }

    @Test
    void shouldReturnUnavailableMetricsWhenSettlementAndComparisonMissing() {
        when(marketSettlementService.getLatestSettlement()).thenThrow(new com.weaksignal.vpp.backend.api.ApiException(
                org.springframework.http.HttpStatus.NOT_FOUND,
                "SETTLEMENT_NOT_FOUND",
                "缺少结算数据",
                Map.of()
        ));
        when(experimentService.listLatestRuns(10)).thenReturn(List.of());

        AgentDispatchOverviewResponse response = service.getOverview();

        assertFalse(response.dispatchSummary().revenueImprovement().available());
        assertTrue(response.comparisonSummary().metrics().stream().noneMatch(AgentDispatchOverviewResponse.ComparisonMetricResponse::available));
    }

    @Test
    void shouldKeepPlanNetPowerTargetWhenTrackingTargetIsZero() {
        when(marketDispatchPlanningService.getTracking("plan-1")).thenReturn(tracking(0, 0, 0));

        AgentDispatchOverviewResponse response = service.getOverview();

        assertEquals(new BigDecimal("304.0"), response.dispatchSummary().plannedTargetPower().value());
        assertEquals(new BigDecimal("-100.00"), response.dispatchSummary().trackingErrorRate().value());
    }

    @Test
    void shouldPreferCurrentRunComparisonWithoutMixingLatestStoppedRun() {
        when(experimentService.getCurrentRun()).thenReturn(runningRun("run-current", "T2_RANDOM_PACKET_LOSS"));
        when(experimentAnalysisService.getComparison("run-current")).thenReturn(comparison("run-current", "T2_RANDOM_PACKET_LOSS"));
        when(experimentAnalysisService.getTimeline("run-current")).thenReturn(timeline());
        AgentDispatchOverviewResponse response = service.getOverview();

        assertEquals("run-current", response.comparisonSummary().runId());
        assertEquals("T2_RANDOM_PACKET_LOSS", response.comparisonSummary().scenarioCode());
    }

    @Test
    void shouldDelegateStartAndStopExperimentProxy() {
        ExperimentStartResponse startResponse = new ExperimentStartResponse("run-1", "T1_LATENCY_JITTER", "RUNNING", now());
        ExperimentStopResponse stopResponse = new ExperimentStopResponse("run-1", "STOPPED", now());
        when(experimentService.startExperiment("T1_LATENCY_JITTER", new com.weaksignal.vpp.backend.api.dto.ExperimentStartRequest(null, null, "agent-dispatch-overview")))
                .thenReturn(startResponse);
        when(experimentService.getCurrentRun()).thenReturn(runningRun("run-1", "T1_LATENCY_JITTER"));
        when(experimentService.stopExperiment("run-1")).thenReturn(stopResponse);

        assertEquals("run-1", service.startExperiment("T1_LATENCY_JITTER").experimentRunId());
        assertEquals("run-1", service.stopCurrentExperiment().runId());
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }

    private List<StationStateResponse> stations(LinkState storageState) {
        return List.of(
                station("pv-gobi-01", "光伏场站", ResourceType.PHOTOVOLTAIC, 126, LinkState.S0),
                station("wind-ridge-02", "风电场站", ResourceType.WIND, 178, LinkState.S0),
                station("grid-link-00", "市电", ResourceType.GRID, 10, LinkState.S0),
                station("storage-hub-03", "储能", ResourceType.STORAGE, 58, storageState),
                station("load-oasis-04", "负荷", ResourceType.LOAD, 122, LinkState.S0),
                station("charge-corridor-05", "充电设施", ResourceType.CHARGING, 24, LinkState.S0)
        );
    }

    private StationStateResponse station(String stationId, String stationName, ResourceType resourceType, double activePower, LinkState linkState) {
        return new StationStateResponse(stationId, stationName, resourceType, activePower, 180, linkState, 20, 0.5, 2, true, null);
    }

    private List<ResourceCapabilityResponse> capabilities() {
        return List.of(new ResourceCapabilityResponse(
                "cap-storage",
                "snapshot-1",
                "storage-hub-03",
                ResourceType.STORAGE,
                58,
                96,
                -60,
                60,
                18,
                18,
                180,
                new StorageConstraintResponse(62, 50, 80, 200, 60, 60, 0.95, 0.95, new BigDecimal("0.080000"), 0.96, 30, 20),
                LinkState.S0,
                2,
                0.95,
                true,
                "",
                now(),
                now(),
                now().plusHours(1),
                "REALTIME_TELEMETRY",
                QualityCode.GOOD,
                "capability-v1",
                now()
        ));
    }

    private DispatchPlanResponse plan() {
        return new DispatchPlanResponse(
                "plan-1",
                "snapshot-1",
                60,
                false,
                DispatchPlanStatus.ACTIVE,
                now().minusMinutes(10),
                now().minusMinutes(5),
                now().plusHours(1),
                "RULE_BASED",
                QualityCode.GOOD,
                "dispatch-plan-v3",
                now().minusMinutes(10),
                List.of(new DispatchPlanItemResponse(
                        "item-1",
                        "snapshot-1",
                        now().minusMinutes(5),
                        now().plusMinutes(55),
                        304,
                        10,
                        0,
                        0,
                        38.9,
                        12.6,
                        30,
                        10,
                        0,
                        new BigDecimal("22.50"),
                        new BigDecimal("31.20"),
                        new BigDecimal("4.80"),
                        "DEFICIT_SUPPORT",
                        0
                ))
        );
    }

    private DispatchPlanTrackingResponse tracking(double plannedPower, double actualPower, double trackingError) {
        return new DispatchPlanTrackingResponse(
                "plan-1",
                "dispatch-plan-v3",
                List.of(new DispatchPlanTrackingItemResponse(
                        "item-1",
                        now().minusMinutes(5),
                        now().plusMinutes(55),
                        plannedPower,
                        actualPower,
                        trackingError,
                        "EXECUTED"
                ))
        );
    }

    private DispatchPlanExecutionResponse execution(String storageStatus, String loadStatus) {
        return new DispatchPlanExecutionResponse(
                "plan-1",
                "dispatch-plan-v3",
                DispatchPlanStatus.ACTIVE,
                "batch-1",
                2,
                1,
                now().minusMinutes(1),
                List.of(
                        new DispatchCommandResponse(
                                "command-storage",
                                "batch-1",
                                "plan-1",
                                "dispatch-plan-v3",
                                "item-1",
                                "snapshot-1",
                                "storage-hub-03",
                                38.9,
                                ControlMode.EDGE_COORDINATION,
                                storageStatus,
                                now().minusMinutes(1),
                                now().plusMinutes(1),
                                "EXECUTED",
                                "ok",
                                now(),
                                List.of()
                        ),
                        new DispatchCommandResponse(
                                "command-load",
                                "batch-1",
                                "plan-1",
                                "dispatch-plan-v3",
                                "item-1",
                                "snapshot-1",
                                "load-oasis-04",
                                -12.6,
                                ControlMode.EDGE_COORDINATION,
                                loadStatus,
                                now().minusMinutes(1),
                                now().plusMinutes(1),
                                "RECEIVED",
                                "ok",
                                now(),
                                List.of()
                        )
                )
        );
    }

    private SettlementResultResponse settlement() {
        return new SettlementResultResponse(
                "settlement-1",
                "plan-1",
                "clearing-1",
                new BigDecimal("116.96"),
                new BigDecimal("5.96"),
                new BigDecimal("1.90"),
                new BigDecimal("1.46"),
                new BigDecimal("0.92"),
                new BigDecimal("0.72"),
                new BigDecimal("0.27"),
                new BigDecimal("1.55"),
                new BigDecimal("111.09"),
                Map.of(),
                true,
                now(),
                List.of()
        );
    }

    private ExperimentRunResponse stoppedRun(String runId, String scenarioCode) {
        return new ExperimentRunResponse(runId, scenarioCode, scenarioCode, "STOPPED", now().minusMinutes(3), now().minusMinutes(1), "summary");
    }

    private ExperimentRunResponse runningRun(String runId, String scenarioCode) {
        return new ExperimentRunResponse(runId, scenarioCode, scenarioCode, "RUNNING", now().minusMinutes(2), null, "summary");
    }

    private ExperimentComparisonResponse comparison(String runId, String scenarioCode) {
        ExperimentRunResponse run = new ExperimentRunResponse(runId, scenarioCode, scenarioCode, "STOPPED", now().minusMinutes(3), now().minusMinutes(1), "summary");
        return new ExperimentComparisonResponse(
                run,
                List.of("pv-gobi-01"),
                new ExperimentMetricSnapshotResponse(10, 150, 182.6, 120, 20, 32, 0.5, 1.0, 2, 3, 100, Map.of("S0", 10L)),
                new ExperimentMetricSnapshotResponse(10, 140, 146.2, 118, 60, 120, 3.2, 8.1, 5, 9, 90, Map.of("S1", 10L)),
                List.of(
                        new ExperimentComparisonResponse.DerivedMetricResponse("peak-load", "峰值负荷", new BigDecimal("182.60"), new BigDecimal("146.20"), "kW", new BigDecimal("36.40"), new BigDecimal("19.93"), false, true),
                        new ExperimentComparisonResponse.DerivedMetricResponse("grid-purchase-cost", "购电成本", new BigDecimal("26.85"), new BigDecimal("19.84"), "元", new BigDecimal("7.01"), new BigDecimal("26.11"), false, true),
                        new ExperimentComparisonResponse.DerivedMetricResponse("curtailment", "弃光弃风", new BigDecimal("38.70"), new BigDecimal("11.20"), "kWh", new BigDecimal("27.50"), new BigDecimal("71.06"), false, true),
                        new ExperimentComparisonResponse.DerivedMetricResponse("soc-swing", "SOC波动", new BigDecimal("28.40"), new BigDecimal("12.70"), "%", new BigDecimal("15.70"), new BigDecimal("55.28"), false, true),
                        new ExperimentComparisonResponse.DerivedMetricResponse("recovery-time", "链路恢复时间", new BigDecimal("0.00"), new BigDecimal("60.00"), "s", new BigDecimal("-60.00"), null, false, true),
                        new ExperimentComparisonResponse.DerivedMetricResponse("execution-success-rate", "执行成功率", new BigDecimal("78.30"), new BigDecimal("96.40"), "%", new BigDecimal("18.10"), new BigDecimal("23.12"), true, true)
                )
        );
    }

    private List<ExperimentTimelinePointResponse> timeline() {
        return List.of(
                new ExperimentTimelinePointResponse(now().minusMinutes(2), "pv-gobi-01", LinkState.S1.name(), 150, 1.5, true, "degraded"),
                new ExperimentTimelinePointResponse(now().minusMinutes(1), "pv-gobi-01", LinkState.S4.name(), 42, 0.2, true, "recovered")
        );
    }

    private com.weaksignal.vpp.backend.api.dto.MarketAgentRunResponse agentRun(boolean safetyPassed, boolean fallbackUsed) {
        return new com.weaksignal.vpp.backend.api.dto.MarketAgentRunResponse(
                "agent-run-1",
                "snapshot-1",
                "plan-1",
                false,
                List.of(
                        decision(MarketAgentType.MARKET_INTERFACE, fallbackUsed, "KEEP_APPROVED_PLAN"),
                        decision(MarketAgentType.FORECAST, fallbackUsed, "ADJUST_WIND_OUTPUT"),
                        decision(MarketAgentType.MARKET_DISPATCH, fallbackUsed, "OPTIMIZE_MARKET_PLAN"),
                        decision(MarketAgentType.RESOURCE_CAPABILITY, fallbackUsed, "FREEZE_RESOURCE_CAPABILITY_BOUNDARY"),
                        decision(MarketAgentType.COMMUNICATION_QUALITY, fallbackUsed, "LIMIT_REMOTE_CONTROL"),
                        decision(MarketAgentType.CONTROL_DECISION, fallbackUsed, "EXECUTE_PLAN"),
                        decision(MarketAgentType.SECURITY_AUDIT, fallbackUsed, "ALLOW_APPROVED_ACTIONS")
                ),
                new MarketSafetyDecision(
                        safetyPassed ? fallbackUsed ? "DEGRADED" : "PASSED" : "REJECTED",
                        safetyPassed,
                        fallbackUsed,
                        safetyPassed
                                ? List.of(
                                "KEEP_APPROVED_PLAN",
                                "ADJUST_WIND_OUTPUT",
                                "OPTIMIZE_MARKET_PLAN",
                                "FREEZE_RESOURCE_CAPABILITY_BOUNDARY",
                                "LIMIT_REMOTE_CONTROL",
                                "EXECUTE_PLAN",
                                "ALLOW_APPROVED_ACTIONS"
                        )
                                : List.of(),
                        safetyPassed ? "通过安全门" : "安全门拒绝"
                ),
                now().minusMinutes(1)
        );
    }

    private com.weaksignal.vpp.backend.api.dto.MarketAgentRunResponse legacyAgentRun() {
        return new com.weaksignal.vpp.backend.api.dto.MarketAgentRunResponse(
                "legacy-agent-run-1",
                "snapshot-1",
                "plan-1",
                false,
                List.of(
                        decision(MarketAgentType.MARKET_INTERFACE, false, "VERIFY_MARKET_SIGNAL_VERSION"),
                        decision(MarketAgentType.FORECAST, false, "USE_P50_FORECAST"),
                        decision(MarketAgentType.MARKET_DISPATCH, false, "GENERATE_MARKET_OPTIMAL_PLAN")
                ),
                new MarketSafetyDecision(
                        "DEGRADED",
                        true,
                        true,
                        List.of(
                                "VERIFY_MARKET_SIGNAL_VERSION",
                                "USE_P50_FORECAST",
                                "GENERATE_MARKET_OPTIMAL_PLAN"
                        ),
                        "DEGRADED：兼容旧版高风险审批记录，已改为观察审计与降级执行"
                ),
                now().minusMinutes(1)
        );
    }

    private MarketAgentDecision decision(MarketAgentType agentType, boolean fallbackUsed, String action) {
        return new MarketAgentDecision(
                agentType.name() + "-1",
                agentType,
                "snapshot-1",
                now().minusMinutes(1),
                now().plusMinutes(10),
                0.92,
                List.of("e1", "e2"),
                "rule-v1",
                fallbackUsed ? "rule" : "deepseek",
                fallbackUsed ? "rule-fallback" : "deepseek-v4-flash",
                List.of(action),
                "LOW",
                false,
                fallbackUsed ? "规则回退" : "结构化结论",
                fallbackUsed,
                fallbackUsed ? com.weaksignal.vpp.backend.marketagent.MarketAgentStageStatus.RULE_FALLBACK
                        : com.weaksignal.vpp.backend.marketagent.MarketAgentStageStatus.SUCCESS,
                0,
                com.weaksignal.vpp.backend.marketagent.MarketAgentErrorCategory.NONE,
                "market=market-v1|forecast=forecast-v1|snapshot=snapshot-1|plan=plan-1",
                "sequential-v2:" + agentType.name(),
                true
        );
    }
}

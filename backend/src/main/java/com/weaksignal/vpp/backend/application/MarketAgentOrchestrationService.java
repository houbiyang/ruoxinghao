package com.weaksignal.vpp.backend.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weaksignal.vpp.backend.api.ApiException;
import com.weaksignal.vpp.backend.api.dto.DispatchContextSnapshotResponse;
import com.weaksignal.vpp.backend.api.dto.DispatchPlanItemResponse;
import com.weaksignal.vpp.backend.api.dto.DispatchPlanResponse;
import com.weaksignal.vpp.backend.api.dto.MarketAgentAnalyzeRequest;
import com.weaksignal.vpp.backend.api.dto.MarketAgentRunResponse;
import com.weaksignal.vpp.backend.api.dto.ResourceCapabilityResponse;
import com.weaksignal.vpp.backend.ai.ChatCompletionsUrlResolver;
import com.weaksignal.vpp.backend.config.AiProperties;
import com.weaksignal.vpp.backend.domain.DispatchPlanStatus;
import com.weaksignal.vpp.backend.domain.LinkState;
import com.weaksignal.vpp.backend.domain.ResourceType;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.MarketAgentDecisionEntity;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.MarketAgentRunEntity;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.MarketAgentDecisionRepository;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.MarketAgentRunRepository;
import com.weaksignal.vpp.backend.marketagent.MarketAgentDecision;
import com.weaksignal.vpp.backend.marketagent.MarketAgentErrorCategory;
import com.weaksignal.vpp.backend.marketagent.MarketAgentStageStatus;
import com.weaksignal.vpp.backend.marketagent.MarketAgentType;
import com.weaksignal.vpp.backend.marketagent.MarketSafetyDecision;
import com.weaksignal.vpp.backend.marketdispatch.ResourceDispatchTarget;
import com.weaksignal.vpp.backend.planning.DispatchContextSnapshotService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 市场调度多智能体编排服务。
 */
@Service
public class MarketAgentOrchestrationService {

    private static final String RULE_VERSION = "market-agent-rule-v1";
    private static final String WORKFLOW_VERSION = "sequential-v2";
    private static final String AGENT_SET_VERSION = "market-agent-set-v2";
    private static final String DECISION_LEVEL_PASSED = "PASSED";
    private static final String DECISION_LEVEL_PASSED_WITH_FALLBACK = "PASSED_WITH_FALLBACK";
    private static final String DECISION_LEVEL_DEGRADED = "DEGRADED";
    private static final String DECISION_LEVEL_REJECTED = "REJECTED";

    /** 调度功率比较容差，单位 kW。 */
    private static final double DISPATCH_POWER_TOLERANCE = 1.0E-6;
    private static final List<MarketAgentType> ORCHESTRATION_AGENT_TYPES = List.of(
            MarketAgentType.MARKET_INTERFACE,
            MarketAgentType.FORECAST,
            MarketAgentType.ENVIRONMENT_ASSESSMENT,
            MarketAgentType.RESOURCE_CAPABILITY,
            MarketAgentType.MARKET_DISPATCH,
            MarketAgentType.COMMUNICATION_QUALITY,
            MarketAgentType.CONTROL_DECISION,
            MarketAgentType.SECURITY_AUDIT
    );
    private static final Map<MarketAgentType, Set<String>> ALLOWED_ACTIONS = allowedActions();
    private static final Set<String> OUTPUT_CONTRACT_FIELDS = Set.of(
            "action",
            "riskLevel",
            "confidence",
            "evidence",
            "reason",
            "nextStageAllowed"
    );
    private static final Map<MarketAgentType, Map<String, String>> ACTION_ALIASES = actionAliases();
    private static final Set<MarketAgentType> CRITICAL_AGENT_TYPES = Set.of(
            MarketAgentType.MARKET_INTERFACE,
            MarketAgentType.RESOURCE_CAPABILITY,
            MarketAgentType.CONTROL_DECISION,
            MarketAgentType.SECURITY_AUDIT
    );

    private final DispatchContextSnapshotService dispatchContextSnapshotService;
    private final MarketDispatchPlanningService marketDispatchPlanningService;
    private final ObjectMapper objectMapper;
    private final AiProperties aiProperties;
    private final Clock clock;
    private final HttpClient httpClient;
    private final MarketAgentRunRepository marketAgentRunRepository;
    private final MarketAgentDecisionRepository marketAgentDecisionRepository;

    /**
     * 注入市场调度多智能体编排依赖。
     *
     * @param dispatchContextSnapshotService 调度快照服务
     * @param marketDispatchPlanningService 计划服务
     * @param objectMapper JSON 组件
     * @param aiProperties AI 配置
     * @param clock 系统时钟
     * @param marketAgentRunRepository 市场智能体运行仓储
     * @param marketAgentDecisionRepository 市场智能体决策仓储
     */
    public MarketAgentOrchestrationService(
            DispatchContextSnapshotService dispatchContextSnapshotService,
            MarketDispatchPlanningService marketDispatchPlanningService,
            ObjectMapper objectMapper,
            AiProperties aiProperties,
            Clock clock,
            MarketAgentRunRepository marketAgentRunRepository,
            MarketAgentDecisionRepository marketAgentDecisionRepository
    ) {
        this.dispatchContextSnapshotService = dispatchContextSnapshotService;
        this.marketDispatchPlanningService = marketDispatchPlanningService;
        this.objectMapper = objectMapper;
        this.aiProperties = aiProperties;
        this.clock = clock;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        this.marketAgentRunRepository = marketAgentRunRepository;
        this.marketAgentDecisionRepository = marketAgentDecisionRepository;
    }

    /**
     * 发起一次市场调度多智能体分析。
     *
     * @param request 分析请求
     * @return 结构化运行结果
     */
    @Transactional
    public MarketAgentRunResponse analyze(MarketAgentAnalyzeRequest request) {
        DispatchContextSnapshotResponse snapshot = resolveSnapshot(request);
        OffsetDateTime now = OffsetDateTime.now(clock);
        DispatchPlanResponse plan = resolvePlan(request, snapshot.snapshotId(), now);
        String runId = UUID.randomUUID().toString();
        List<MarketAgentDecision> decisions = new ArrayList<>();
        boolean aiAvailable = isDeepSeekAvailable();
        AgentChainState chainState = AgentChainState.empty();
        boolean nextStageAllowed = true;
        for (MarketAgentType agentType : ORCHESTRATION_AGENT_TYPES) {
            MarketAgentDecision decision = !nextStageAllowed
                    ? buildBlockedDecision(agentType, snapshot, plan, now, chainState,
                    MarketAgentErrorCategory.CONSTRAINT_VIOLATION, "上游关键阶段未通过，禁止继续执行")
                    : executeStage(agentType, snapshot, plan, now, chainState, aiAvailable);
            decisions.add(decision);
            chainState = appendChainState(chainState, agentType, decision, snapshot, plan);
            nextStageAllowed = decision.nextStageAllowed();
        }
        MarketSafetyDecision safetyDecision = evaluateSafety(plan, decisions);
        MarketAgentRunResponse response = new MarketAgentRunResponse(
                runId,
                snapshot.snapshotId(),
                plan.planId(),
                request.conservativePlan(),
                decisions,
                safetyDecision,
                now,
                WORKFLOW_VERSION,
                AGENT_SET_VERSION,
                decisions.size() == ORCHESTRATION_AGENT_TYPES.size(),
                false
        );
        persistRun(response);
        return response;
    }

    /**
     * 查询指定运行。
     *
     * @param runId 运行标识
     * @return 运行结果
     */
    @Transactional(readOnly = true)
    public MarketAgentRunResponse getRun(String runId) {
        MarketAgentRunEntity entity = marketAgentRunRepository.findByRunId(runId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "MARKET_AGENT_RUN_NOT_FOUND",
                        "市场调度智能体运行不存在",
                        Map.of("runId", runId)
                ));
        List<MarketAgentDecision> decisions = getDecisions(runId);
        return toRunResponse(entity, decisions);
    }

    /**
     * 查询最近一次市场调度智能体运行。
     *
     * @return 最近运行结果，不存在时返回 {@code null}
     */
    @Transactional(readOnly = true)
    public MarketAgentRunResponse getLatestRun() {
        return marketAgentRunRepository.findByLegacyRunFalseOrderByCreatedAtDesc().stream()
                .map(entity -> toRunResponse(entity, getDecisions(entity.getRunId())))
                .filter(response -> !response.legacyRun())
                .findFirst()
                .orElse(null);
    }

    /**
     * 查询运行下的智能体决策列表。
     *
     * @param runId 运行标识
     * @return 决策列表
     */
    @Transactional(readOnly = true)
    public List<MarketAgentDecision> getDecisions(String runId) {
        if (marketAgentRunRepository.findByRunId(runId).isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "MARKET_AGENT_RUN_NOT_FOUND", "市场调度智能体运行不存在", Map.of("runId", runId));
        }
        return marketAgentDecisionRepository.findByRunIdOrderByCreatedAtAsc(runId).stream()
                .map(this::toDecision)
                .toList();
    }

    private MarketAgentRunResponse toRunResponse(
            MarketAgentRunEntity entity,
            List<MarketAgentDecision> decisions
    ) {
        boolean stageRecordsComplete = entity.isStageRecordsComplete()
                && decisions.size() == ORCHESTRATION_AGENT_TYPES.size()
                && decisions.stream().allMatch(decision -> decision.stageStatus() != null);
        boolean legacyRun = entity.isLegacyRun()
                || !stageRecordsComplete
                || entity.getWorkflowVersion() == null
                || entity.getWorkflowVersion().isBlank();
        return new MarketAgentRunResponse(
                entity.getRunId(),
                entity.getSnapshotId(),
                entity.getDispatchPlanId(),
                entity.isConservativePlan(),
                decisions,
                readSafetyDecision(entity.getSafetyDecisionJson()),
                entity.getCreatedAt(),
                entity.getWorkflowVersion(),
                entity.getAgentSetVersion(),
                stageRecordsComplete,
                legacyRun
        );
    }

    /**
     * 查询 DeepSeek Provider 状态。
     *
     * @return Provider 状态
     */
    public Map<String, Object> getDeepSeekStatus() {
        boolean baseUrlValid = ChatCompletionsUrlResolver.isValid(aiProperties.deepseek().baseUrl());
        return Map.of(
                "enabled", aiProperties.enabled() && aiProperties.deepseek().enabled(),
                "available", isDeepSeekAvailable(),
                "providerCode", "deepseek",
                "model", aiProperties.deepseek().model(),
                "baseUrlValid", baseUrlValid,
                "resolvedChatCompletionsUrl", baseUrlValid
                        ? ChatCompletionsUrlResolver.resolve(aiProperties.deepseek().baseUrl())
                        : ""
        );
    }

    private DispatchContextSnapshotResponse resolveSnapshot(MarketAgentAnalyzeRequest request) {
        if (request.dispatchContextSnapshotId() != null && !request.dispatchContextSnapshotId().isBlank()) {
            return dispatchContextSnapshotService.getSnapshot(request.dispatchContextSnapshotId());
        }
        return dispatchContextSnapshotService.createSnapshot();
    }

    private DispatchPlanResponse resolvePlan(MarketAgentAnalyzeRequest request, String snapshotId, OffsetDateTime now) {
        if (request.dispatchPlanId() != null && !request.dispatchPlanId().isBlank()) {
            DispatchPlanResponse specifiedPlan = marketDispatchPlanningService.getPlan(request.dispatchPlanId());
            if (!snapshotId.equals(specifiedPlan.inputSnapshotId())) {
                throw new ApiException(
                        HttpStatus.CONFLICT,
                        "DISPATCH_PLAN_SNAPSHOT_MISMATCH",
                        "指定调度计划与本轮输入快照不一致",
                        Map.of("dispatchPlanId", specifiedPlan.planId(), "snapshotId", snapshotId,
                                "planSnapshotId", specifiedPlan.inputSnapshotId())
                );
            }
            return specifiedPlan;
        }
        try {
            DispatchPlanResponse latestPlan = marketDispatchPlanningService.getLatestPlan();
            if (isPlanCompatible(latestPlan, snapshotId, now)) {
                return latestPlan;
            }
        } catch (ApiException exception) {
            if (!"DISPATCH_PLAN_NOT_FOUND".equals(exception.getCode())) {
                throw exception;
            }
        }
        return marketDispatchPlanningService.create(new com.weaksignal.vpp.backend.api.dto.DispatchPlanCreateRequest(
                snapshotId,
                request.conservativePlan()
        ));
    }

    private boolean isPlanCompatible(DispatchPlanResponse plan, String snapshotId, OffsetDateTime now) {
        return snapshotId.equals(plan.inputSnapshotId())
                && plan.validUntil().isAfter(now)
                && plan.status() != DispatchPlanStatus.REJECTED
                && plan.status() != DispatchPlanStatus.EXPIRED;
    }

    private MarketAgentDecision executeStage(
            MarketAgentType agentType,
            DispatchContextSnapshotResponse snapshot,
            DispatchPlanResponse plan,
            OffsetDateTime now,
            AgentChainState chainState,
            boolean aiAvailable
    ) {
        MarketAgentErrorCategory inputError = validateStageInput(agentType, snapshot, plan, now);
        if (inputError != MarketAgentErrorCategory.NONE) {
            return buildBlockedDecision(agentType, snapshot, plan, now, chainState, inputError, "输入约束未满足：" + inputError.name());
        }
        if (isDeterministicStage(agentType)) {
            return buildDeterministicDecision(agentType, snapshot, plan, now, chainState);
        }
        if (!aiAvailable) {
            return buildFallbackDecision(
                    agentType,
                    snapshot,
                    plan.planId(),
                    now,
                    "DeepSeek 未启用，采用规则输出",
                    chainState,
                    0,
                    MarketAgentErrorCategory.NONE
            );
        }
        return analyzeWithDeepSeek(agentType, snapshot, plan, now, chainState);
    }

    private MarketAgentErrorCategory validateStageInput(
            MarketAgentType agentType,
            DispatchContextSnapshotResponse snapshot,
            DispatchPlanResponse plan,
            OffsetDateTime now
    ) {
        if (snapshot.validUntil().isBefore(now)
                || snapshot.marketSignal() == null
                || snapshot.marketSignal().version() == null
                || snapshot.marketSignal().version().isBlank()
                || snapshot.marketSignal().validUntil().isBefore(now)) {
            return MarketAgentErrorCategory.STALE_INPUT;
        }
        if (agentType == MarketAgentType.FORECAST
                && (snapshot.forecastSnapshot() == null || snapshot.forecastSnapshot().points().isEmpty())) {
            return MarketAgentErrorCategory.CONSTRAINT_VIOLATION;
        }
        if (agentType == MarketAgentType.RESOURCE_CAPABILITY && snapshot.resourceCapabilities().isEmpty()) {
            return MarketAgentErrorCategory.CONSTRAINT_VIOLATION;
        }
        if ((agentType == MarketAgentType.MARKET_DISPATCH || agentType == MarketAgentType.CONTROL_DECISION)
                && (plan.status() == DispatchPlanStatus.REJECTED || plan.status() == DispatchPlanStatus.EXPIRED)) {
            return MarketAgentErrorCategory.CONSTRAINT_VIOLATION;
        }
        if (agentType == MarketAgentType.CONTROL_DECISION) {
            if (!hasExecutableResource(snapshot.resourceCapabilities())
                    || !hasExecutableCandidatePlan(snapshot, plan)) {
                return MarketAgentErrorCategory.CONSTRAINT_VIOLATION;
            }
        }
        return MarketAgentErrorCategory.NONE;
    }

    /**
     * 判断能力快照中是否存在可参与降级调度的资源。
     *
     * @param resourceCapabilities 资源能力快照
     * @return 存在数据可信、非失联自治且最大可调功率大于容差的资源时返回 {@code true}
     */
    private boolean hasExecutableResource(List<ResourceCapabilityResponse> resourceCapabilities) {
        return resourceCapabilities.stream().anyMatch(capability -> capability.linkState() != LinkState.S3
                && capability.dataTrusted()
                && capability.maximumPower() > DISPATCH_POWER_TOLERANCE);
    }

    /**
     * 判断候选计划是否包含可直接落入资源能力边界的真实调度动作。
     *
     * @param snapshot 调度上下文快照
     * @param plan 候选调度计划
     * @return 计划状态安全、包含非零调度功率且全部非零计划项均可执行时返回 {@code true}
     */
    private boolean hasExecutableCandidatePlan(
            DispatchContextSnapshotResponse snapshot,
            DispatchPlanResponse plan
    ) {
        if (plan.status() == DispatchPlanStatus.REJECTED
                || plan.status() == DispatchPlanStatus.EXPIRED
                || plan.items() == null
                || plan.items().isEmpty()) {
            return false;
        }
        List<ResourceDispatchTarget> targets = marketDispatchPlanningService.getPersistedTargets(plan.planId());
        if (targets.isEmpty()) {
            return false;
        }
        Map<String, ResourceCapabilityResponse> capabilitiesByStation = new LinkedHashMap<>();
        snapshot.resourceCapabilities().forEach(capability -> capabilitiesByStation.put(capability.stationId(), capability));
        return targets.stream().allMatch(target -> {
            ResourceCapabilityResponse capability = capabilitiesByStation.get(target.stationId());
            return capability != null
                    && capability.linkState() != LinkState.S3
                    && capability.dataTrusted()
                    && isTargetPowerWithinBounds(target, capability, snapshot);
        });
    }

    /**
     * 判断逐资源目标是否位于对应能力边界内，市电资源同时允许限额内的反向送电。
     *
     * @param target 逐资源调度目标
     * @param capability 资源能力快照
     * @param snapshot 调度上下文快照
     * @return 目标功率可执行时返回 {@code true}
     */
    private boolean isTargetPowerWithinBounds(
            ResourceDispatchTarget target,
            ResourceCapabilityResponse capability,
            DispatchContextSnapshotResponse snapshot
    ) {
        if (target.resourceType() != ResourceType.GRID) {
            return isPowerWithinBounds(target.targetPower(), capability);
        }
        double minimumGridPower = -snapshot.marketSignal().gridExportLimit();
        double maximumGridPower = Math.min(capability.maximumPower(), snapshot.marketSignal().gridImportLimit());
        return Double.isFinite(target.targetPower())
                && target.targetPower() >= minimumGridPower - DISPATCH_POWER_TOLERANCE
                && target.targetPower() <= maximumGridPower + DISPATCH_POWER_TOLERANCE;
    }

    /**
     * 判断计划时间片是否能够映射到对应资源的未裁剪目标功率。
     *
     * @param item 调度计划时间片
     * @param resourceCapabilities 资源能力快照
     * @return 所有非零调度动作均能在对应资源能力边界内执行时返回 {@code true}
     */
    private boolean isPlanItemExecutable(
            DispatchPlanItemResponse item,
            List<ResourceCapabilityResponse> resourceCapabilities
    ) {
        boolean storagePowerValid = (isZeroPower(item.storageChargePower()) && isZeroPower(item.storageDischargePower()))
                || isPowerWithinCapabilities(
                        item.storageDischargePower() - item.storageChargePower(),
                        Set.of(ResourceType.STORAGE),
                        resourceCapabilities
                );
        boolean gridPowerValid = (isZeroPower(item.gridImportPower()) && isZeroPower(item.gridExportPower()))
                || isPowerWithinCapabilities(
                        item.gridImportPower() - item.gridExportPower(),
                        Set.of(ResourceType.GRID),
                        resourceCapabilities
                );
        boolean flexibleLoadPowerValid = isZeroPower(item.flexibleLoadAdjustment())
                || isAdjustedLoadPowerWithinCapabilities(item.flexibleLoadAdjustment(), resourceCapabilities);
        boolean renewablePowerValid = isZeroPower(item.renewableCurtailment())
                || isRenewableCurtailmentWithinCapabilities(item.renewableCurtailment(), resourceCapabilities);
        return storagePowerValid && gridPowerValid && flexibleLoadPowerValid && renewablePowerValid;
    }

    /**
     * 判断调度功率是否落在全部对应、可执行资源的能力边界内。
     *
     * @param targetPower 待校验目标功率
     * @param resourceTypes 对应资源类型
     * @param resourceCapabilities 资源能力快照
     * @return 存在对应可执行资源且目标功率未越界时返回 {@code true}
     */
    private boolean isPowerWithinCapabilities(
            double targetPower,
            Set<ResourceType> resourceTypes,
            List<ResourceCapabilityResponse> resourceCapabilities
    ) {
        List<ResourceCapabilityResponse> matchingCapabilities = resourceCapabilities.stream()
                .filter(capability -> resourceTypes.contains(capability.resourceType()))
                .filter(capability -> capability.linkState() != LinkState.S3 && capability.dataTrusted())
                .toList();
        return !matchingCapabilities.isEmpty()
                && matchingCapabilities.stream().allMatch(capability -> isPowerWithinBounds(targetPower, capability));
    }

    /**
     * 判断柔性负荷调节后的目标功率是否落在负荷与充电资源能力边界内。
     *
     * @param flexibleLoadAdjustment 柔性负荷调节功率
     * @param resourceCapabilities 资源能力快照
     * @return 全部对应可执行资源均未越界时返回 {@code true}
     */
    private boolean isAdjustedLoadPowerWithinCapabilities(
            double flexibleLoadAdjustment,
            List<ResourceCapabilityResponse> resourceCapabilities
    ) {
        List<ResourceCapabilityResponse> matchingCapabilities = resourceCapabilities.stream()
                .filter(capability -> capability.resourceType() == ResourceType.LOAD
                        || capability.resourceType() == ResourceType.CHARGING)
                .filter(capability -> capability.linkState() != LinkState.S3 && capability.dataTrusted())
                .toList();
        return !matchingCapabilities.isEmpty()
                && matchingCapabilities.stream().allMatch(capability -> isPowerWithinBounds(
                        Math.max(0.0, capability.currentPower() - flexibleLoadAdjustment / 2.0),
                        capability
                ));
    }

    /**
     * 判断新能源限发后的目标功率是否落在光伏与风电资源能力边界内。
     *
     * @param renewableCurtailment 新能源限发功率
     * @param resourceCapabilities 资源能力快照
     * @return 全部对应可执行资源均未越界时返回 {@code true}
     */
    private boolean isRenewableCurtailmentWithinCapabilities(
            double renewableCurtailment,
            List<ResourceCapabilityResponse> resourceCapabilities
    ) {
        List<ResourceCapabilityResponse> matchingCapabilities = resourceCapabilities.stream()
                .filter(capability -> capability.resourceType() == ResourceType.PHOTOVOLTAIC
                        || capability.resourceType() == ResourceType.WIND)
                .filter(capability -> capability.linkState() != LinkState.S3 && capability.dataTrusted())
                .toList();
        return !matchingCapabilities.isEmpty()
                && matchingCapabilities.stream().allMatch(capability -> isPowerWithinBounds(
                        capability.currentPower() - renewableCurtailment / 2.0,
                        capability
                ));
    }

    /**
     * 判断单资源目标功率是否位于最小和最大功率之间。
     *
     * @param targetPower 待校验目标功率
     * @param capability 资源能力快照
     * @return 目标功率在容差范围内时返回 {@code true}
     */
    private boolean isPowerWithinBounds(double targetPower, ResourceCapabilityResponse capability) {
        return Double.isFinite(targetPower)
                && targetPower >= capability.minimumPower() - DISPATCH_POWER_TOLERANCE
                && targetPower <= capability.maximumPower() + DISPATCH_POWER_TOLERANCE;
    }

    /**
     * 判断调度功率是否可视为零。
     *
     * @param power 待判断功率
     * @return 绝对值不大于功率容差时返回 {@code true}
     */
    private boolean isZeroPower(double power) {
        return Math.abs(power) <= DISPATCH_POWER_TOLERANCE;
    }

    /**
     * 判断计划时间片是否包含非零的实际调度功率。
     *
     * @param item 调度计划时间片
     * @return 至少一个实际调度功率非零时返回 {@code true}
     */
    private boolean hasNonZeroDispatchPower(DispatchPlanItemResponse item) {
        return !isZeroPower(item.gridImportPower())
                || !isZeroPower(item.gridExportPower())
                || !isZeroPower(item.storageChargePower())
                || !isZeroPower(item.storageDischargePower())
                || !isZeroPower(item.flexibleLoadAdjustment())
                || !isZeroPower(item.renewableCurtailment());
    }

    private boolean isDeterministicStage(MarketAgentType agentType) {
        return switch (agentType) {
            case MARKET_INTERFACE, RESOURCE_CAPABILITY, COMMUNICATION_QUALITY, CONTROL_DECISION, SECURITY_AUDIT -> true;
            default -> false;
        };
    }

    private MarketAgentDecision buildDeterministicDecision(
            MarketAgentType agentType,
            DispatchContextSnapshotResponse snapshot,
            DispatchPlanResponse plan,
            OffsetDateTime now,
            AgentChainState chainState
    ) {
        return buildDecision(
                agentType,
                snapshot,
                now,
                "rule",
                "sequential-deterministic-v2",
                agentType == MarketAgentType.SECURITY_AUDIT
                        ? List.of("ALLOW_SAFE_ACTIONS")
                        : fallbackActions(agentType),
                "LOW",
                resolveStageOutput(agentType, null, snapshot, plan, chainState),
                false,
                MarketAgentStageStatus.SUCCESS,
                0,
                MarketAgentErrorCategory.NONE,
                true,
                plan.planId()
        );
    }

    private MarketAgentDecision buildBlockedDecision(
            MarketAgentType agentType,
            DispatchContextSnapshotResponse snapshot,
            DispatchPlanResponse plan,
            OffsetDateTime now,
            AgentChainState chainState,
            MarketAgentErrorCategory errorCategory,
            String explanation
    ) {
        return buildDecision(
                agentType,
                snapshot,
                now,
                "rule",
                "sequential-blocked-v2",
                agentType == MarketAgentType.SECURITY_AUDIT ? List.of("BLOCK_UNSAFE_ACTIONS") : List.of(),
                "HIGH",
                resolveFallbackExplanation(agentType, snapshot, explanation, chainState),
                false,
                MarketAgentStageStatus.BLOCKED,
                0,
                errorCategory,
                false,
                plan.planId()
        );
    }

    private MarketAgentDecision analyzeWithDeepSeek(
            MarketAgentType agentType,
            DispatchContextSnapshotResponse snapshot,
            DispatchPlanResponse plan,
            OffsetDateTime now,
            AgentChainState chainState
    ) {
        MarketAgentErrorCategory lastErrorCategory = MarketAgentErrorCategory.NONE;
        ProviderFailureDetail lastProviderFailure = ProviderFailureDetail.empty();
        boolean schemaRepairRequested = false;
        boolean actionRepairRequested = false;
        for (int retryCount = 0; retryCount <= 2; retryCount++) {
            try {
                Map<String, Object> context = new LinkedHashMap<>();
                context.put("snapshotId", snapshot.snapshotId());
                context.put("intervalMinutes", snapshot.intervalMinutes());
                context.put("marketSignalVersion", snapshot.marketSignal().version());
                context.put("forecastVersion", snapshot.forecastSnapshot().version());
                context.put("planId", plan.planId());
                context.put("planVersion", plan.version());
                context.put("planStatus", plan.status().name());
                context.put("planItemCount", plan.items().size());
                context.put("resourceCount", snapshot.resourceCapabilities().size());
                context.put("upstreamStageOutputs", chainState.stageOutputs());
                context.put("activeConstraints", chainState.activeConstraints());
                context.put("controlIntent", chainState.controlIntent());
                String userPayload = objectMapper.writeValueAsString(context);
                List<Map<String, Object>> messages = new ArrayList<>();
                messages.add(Map.of("role", "system", "content", buildSystemPrompt(agentType)));
                messages.add(Map.of("role", "user", "content", userPayload));
                if (schemaRepairRequested) {
                    messages.add(Map.of(
                            "role", "user",
                            "content", "上一份输出不符合 JSON 契约。请仅返回一个 JSON 对象，"
                                    + "字段必须且只能为 action、riskLevel、confidence、evidence、reason、nextStageAllowed。"
                                    + "不得使用 candidateActions、evidenceRefs、explanation 或其他字段。"
                    ));
                }
                if (actionRepairRequested) {
                    messages.add(Map.of(
                            "role", "user",
                            "content", "上一份输出的 action 不合法。当前智能体仅允许以下动作编码："
                                    + ALLOWED_ACTIONS.getOrDefault(agentType, Set.of())
                                    + "。action 必须原样选择其中一个编码，不得输出自由文本、解释或未列出的动作。"
                    ));
                }
                Map<String, Object> requestBody = Map.of(
                        "model", aiProperties.deepseek().model(),
                        "temperature", 0,
                        "response_format", Map.of("type", "json_object"),
                        "messages", messages
                );
                HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(ChatCompletionsUrlResolver.resolve(aiProperties.deepseek().baseUrl())))
                        .timeout(Duration.ofSeconds(Math.max(1, aiProperties.deepseek().timeoutSeconds())))
                        .header("Authorization", "Bearer " + aiProperties.deepseek().apiKey())
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody), StandardCharsets.UTF_8))
                        .build();
                HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    lastErrorCategory = classifyHttpError(response.statusCode());
                    lastProviderFailure = readProviderFailureDetail(response);
                    if (isRetryable(lastErrorCategory) && retryCount < 2) {
                        waitBeforeRetry(retryCount);
                        continue;
                    }
                    return buildFallbackDecision(agentType, snapshot, plan.planId(), now,
                            resolveProviderFailureExplanation(lastErrorCategory, response.statusCode()),
                            chainState, retryCount, lastErrorCategory, lastProviderFailure);
                }
                JsonNode content = objectMapper.readTree(response.body()).path("choices").path(0).path("message").path("content");
                if (!content.isTextual()) {
                    return buildFallbackDecision(agentType, snapshot, plan.planId(), now,
                            "DeepSeek 响应缺少结构化内容", chainState, retryCount, MarketAgentErrorCategory.INVALID_SCHEMA);
                }
                JsonNode decisionNode = objectMapper.readTree(content.asText());
                if (!hasValidOutputContractShape(decisionNode)) {
                    if (!schemaRepairRequested) {
                        schemaRepairRequested = true;
                        continue;
                    }
                    return buildFallbackDecision(agentType, snapshot, plan.planId(), now,
                            "DeepSeek 重试后仍未满足结构化契约", chainState, retryCount, MarketAgentErrorCategory.INVALID_SCHEMA);
                }
                String action = normalizeAction(agentType, decisionNode.path("action").asText());
                if (action == null) {
                    if (!actionRepairRequested) {
                        actionRepairRequested = true;
                        continue;
                    }
                    return buildFallbackDecision(agentType, snapshot, plan.planId(), now,
                            "DeepSeek 动作修复后仍不在当前智能体白名单内", chainState, retryCount, MarketAgentErrorCategory.INVALID_ACTION);
                }
                String riskLevel = decisionNode.path("riskLevel").asText().trim().toUpperCase(Locale.ROOT);
                if (!Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL").contains(riskLevel)
                        || !isValidConfidence(decisionNode.path("confidence"))
                        || !isValidEvidence(decisionNode.path("evidence"))
                        || decisionNode.path("reason").asText().isBlank()) {
                    if (!schemaRepairRequested) {
                        schemaRepairRequested = true;
                        continue;
                    }
                    return buildFallbackDecision(agentType, snapshot, plan.planId(), now,
                            "DeepSeek 重试后字段或类型仍不合法", chainState, retryCount, MarketAgentErrorCategory.INVALID_SCHEMA);
                }
                return new MarketAgentDecision(
                        UUID.randomUUID().toString(),
                        agentType,
                        snapshot.snapshotId(),
                        now,
                        snapshot.validUntil(),
                        decisionNode.path("confidence").asDouble(),
                        readStringList(decisionNode.path("evidence")),
                        RULE_VERSION,
                        "deepseek",
                        aiProperties.deepseek().model(),
                        List.of(action),
                        riskLevel,
                        false,
                        decisionNode.path("reason").asText(),
                        false,
                        MarketAgentStageStatus.SUCCESS,
                        retryCount,
                        MarketAgentErrorCategory.NONE,
                        buildInputVersion(snapshot, plan),
                        buildOutputVersion(agentType, now),
                        decisionNode.path("nextStageAllowed").asBoolean()
                );
            } catch (HttpTimeoutException exception) {
                lastErrorCategory = MarketAgentErrorCategory.TIMEOUT;
                lastProviderFailure = ProviderFailureDetail.fromException("HTTP_TIMEOUT", exception);
            } catch (JsonProcessingException exception) {
                if (!schemaRepairRequested) {
                    schemaRepairRequested = true;
                    continue;
                }
                return buildFallbackDecision(agentType, snapshot, plan.planId(), now,
                        "DeepSeek 重试后响应结构仍不合法", chainState, retryCount, MarketAgentErrorCategory.INVALID_SCHEMA);
            } catch (Exception exception) {
                lastErrorCategory = MarketAgentErrorCategory.PROVIDER_TRANSPORT_ERROR;
                lastProviderFailure = ProviderFailureDetail.fromException(exception.getClass().getSimpleName(), exception);
            }
            if (retryCount < 2) {
                waitBeforeRetry(retryCount);
                continue;
            }
            return buildFallbackDecision(agentType, snapshot, plan.planId(), now,
                    "DeepSeek 调用失败，已回退规则模式", chainState, retryCount, lastErrorCategory, lastProviderFailure);
        }
        return buildFallbackDecision(agentType, snapshot, plan.planId(), now,
                "DeepSeek 调用失败，已回退规则模式", chainState, 2, lastErrorCategory, lastProviderFailure);
    }

    private MarketAgentDecision buildFallbackDecision(
            MarketAgentType agentType,
            DispatchContextSnapshotResponse snapshot,
            String planId,
            OffsetDateTime now,
            String explanation,
            AgentChainState chainState,
            int retryCount,
            MarketAgentErrorCategory errorCategory
    ) {
        return buildFallbackDecision(
                agentType,
                snapshot,
                planId,
                now,
                explanation,
                chainState,
                retryCount,
                errorCategory,
                ProviderFailureDetail.empty()
        );
    }

    /**
     * 生成规则回退决策，并保存上游模型调用的可审计诊断信息。
     *
     * @param agentType 智能体类型
     * @param snapshot 输入快照
     * @param planId 调度计划标识
     * @param now 当前时间
     * @param explanation 回退说明
     * @param chainState 已执行阶段状态
     * @param retryCount 已执行重试次数
     * @param errorCategory 错误分类
     * @param providerFailure 上游调用诊断信息
     * @return 规则回退决策
     */
    private MarketAgentDecision buildFallbackDecision(
            MarketAgentType agentType,
            DispatchContextSnapshotResponse snapshot,
            String planId,
            OffsetDateTime now,
            String explanation,
            AgentChainState chainState,
            int retryCount,
            MarketAgentErrorCategory errorCategory,
            ProviderFailureDetail providerFailure
    ) {
        List<String> evidenceRefs = new ArrayList<>();
        evidenceRefs.add("snapshot:" + snapshot.snapshotId());
        evidenceRefs.add("plan:" + planId);
        evidenceRefs.addAll(chainState.stageOutputs().keySet().stream()
                .map(stage -> "upstream:" + stage)
                .toList());
        return new MarketAgentDecision(
                UUID.randomUUID().toString(),
                agentType,
                snapshot.snapshotId(),
                now,
                now.plusMinutes(30),
                0.65,
                evidenceRefs,
                RULE_VERSION,
                "rule",
                "rule-fallback",
                fallbackActions(agentType),
                resolveFallbackRiskLevel(agentType, snapshot),
                false,
                resolveFallbackExplanation(agentType, snapshot, explanation, chainState),
                true,
                MarketAgentStageStatus.RULE_FALLBACK,
                retryCount,
                errorCategory,
                buildInputVersion(snapshot, planId),
                buildOutputVersion(agentType, now),
                hasValidFallbackOutput(agentType),
                providerFailure.statusCode(),
                providerFailure.errorCode(),
                providerFailure.errorMessage()
        );
    }

    private MarketAgentDecision buildDecision(
            MarketAgentType agentType,
            DispatchContextSnapshotResponse snapshot,
            OffsetDateTime now,
            String modelProvider,
            String modelVersion,
            List<String> candidateActions,
            String riskLevel,
            String explanation,
            boolean fallbackUsed,
            MarketAgentStageStatus stageStatus,
            int retryCount,
            MarketAgentErrorCategory errorCategory,
            boolean nextStageAllowed,
            String planId
    ) {
        return new MarketAgentDecision(
                UUID.randomUUID().toString(), agentType, snapshot.snapshotId(), now, snapshot.validUntil(), 1.0,
                List.of("snapshot:" + snapshot.snapshotId(), "plan:" + planId), RULE_VERSION, modelProvider, modelVersion,
                candidateActions, riskLevel, false, explanation, fallbackUsed, stageStatus, retryCount, errorCategory,
                buildInputVersion(snapshot, planId), buildOutputVersion(agentType, now), nextStageAllowed
        );
    }

    private MarketAgentErrorCategory classifyHttpError(int statusCode) {
        if (statusCode == 429) {
            return MarketAgentErrorCategory.RATE_LIMIT;
        }
        if (statusCode == 401 || statusCode == 403) {
            return MarketAgentErrorCategory.AUTH_ERROR;
        }
        if (statusCode == 404) {
            return MarketAgentErrorCategory.PROVIDER_ENDPOINT_NOT_FOUND;
        }
        return statusCode >= 500 ? MarketAgentErrorCategory.PROVIDER_5XX : MarketAgentErrorCategory.INVALID_SCHEMA;
    }

    private String resolveProviderFailureExplanation(MarketAgentErrorCategory errorCategory, int statusCode) {
        return switch (errorCategory) {
            case AUTH_ERROR -> "DeepSeek 鉴权失败，请检查模型服务密钥权限";
            case PROVIDER_ENDPOINT_NOT_FOUND -> "模型服务端点或模型配置不存在";
            case RATE_LIMIT -> "DeepSeek 请求限流，重试后采用规则输出";
            case PROVIDER_5XX -> "DeepSeek 服务异常：" + statusCode;
            case PROVIDER_TRANSPORT_ERROR -> "DeepSeek 连接或传输异常，重试后采用规则输出";
            default -> "DeepSeek 返回非成功状态：" + statusCode;
        };
    }

    private boolean isRetryable(MarketAgentErrorCategory errorCategory) {
        return errorCategory == MarketAgentErrorCategory.TIMEOUT
                || errorCategory == MarketAgentErrorCategory.RATE_LIMIT
                || errorCategory == MarketAgentErrorCategory.PROVIDER_5XX
                || errorCategory == MarketAgentErrorCategory.PROVIDER_TRANSPORT_ERROR;
    }

    /**
     * 解析上游模型服务返回的安全错误摘要。
     *
     * <p>仅提取标准 error.code 与 error.message 字段，不保留请求报文、鉴权头或完整响应正文。</p>
     *
     * @param response 上游 HTTP 响应
     * @return 可持久化的上游调用诊断信息
     */
    private ProviderFailureDetail readProviderFailureDetail(HttpResponse<String> response) {
        String fallbackCode = "HTTP_" + response.statusCode();
        String fallbackMessage = "上游模型服务返回 HTTP " + response.statusCode() + "，未提供可解析错误详情";
        try {
            JsonNode errorNode = objectMapper.readTree(response.body()).path("error");
            String errorCode = sanitizeProviderErrorText(errorNode.path("code").asText());
            String errorMessage = sanitizeProviderErrorText(errorNode.path("message").asText());
            return new ProviderFailureDetail(
                    response.statusCode(),
                    errorCode.isBlank() ? fallbackCode : errorCode,
                    errorMessage.isBlank() ? fallbackMessage : errorMessage
            );
        } catch (JsonProcessingException exception) {
            return new ProviderFailureDetail(response.statusCode(), fallbackCode, fallbackMessage);
        }
    }

    /**
     * 清理外部服务错误文本，防止凭据或过长响应写入审计记录。
     *
     * @param value 外部服务返回的错误文本
     * @return 可安全展示和持久化的错误摘要
     */
    private static String sanitizeProviderErrorText(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String sanitized = value.replaceAll("[\\r\\n\\t]+", " ")
                .replaceAll("(?i)(bearer\\s+)[^\\s,;]+", "$1***")
                .replaceAll("(?i)(api[_-]?key\\s*[:=]\\s*)[^\\s,;]+", "$1***")
                .trim();
        return sanitized.length() <= 512 ? sanitized : sanitized.substring(0, 512);
    }

    /**
     * 上游模型服务调用的可审计诊断信息。
     *
     * @param statusCode 上游 HTTP 状态码；本地传输异常时为 null
     * @param errorCode 上游错误码或异常类型
     * @param errorMessage 脱敏后的错误摘要
     */
    private record ProviderFailureDetail(
            Integer statusCode,
            String errorCode,
            String errorMessage
    ) {

        /**
         * 创建无上游失败信息的诊断对象。
         *
         * @return 空诊断对象
         */
        private static ProviderFailureDetail empty() {
            return new ProviderFailureDetail(null, null, null);
        }

        /**
         * 从本地异常创建脱敏诊断对象。
         *
         * @param errorCode 异常类型
         * @param exception 原始异常
         * @return 本地传输异常诊断对象
         */
        private static ProviderFailureDetail fromException(String errorCode, Exception exception) {
            String message = sanitizeProviderErrorText(exception.getMessage());
            return new ProviderFailureDetail(
                    null,
                    sanitizeProviderErrorText(errorCode),
                    message.isBlank() ? "模型服务调用发生本地传输异常" : message
            );
        }
    }

    private void waitBeforeRetry(int retryCount) {
        try {
            Thread.sleep(100L * (1L << retryCount));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private String buildInputVersion(DispatchContextSnapshotResponse snapshot, DispatchPlanResponse plan) {
        return buildInputVersion(snapshot, plan.planId() + ":" + plan.version());
    }

    private String buildInputVersion(DispatchContextSnapshotResponse snapshot, String planVersion) {
        return "market=" + snapshot.marketSignal().version()
                + "|forecast=" + snapshot.forecastSnapshot().version()
                + "|snapshot=" + snapshot.snapshotId()
                + "|plan=" + planVersion;
    }

    private String buildOutputVersion(MarketAgentType agentType, OffsetDateTime now) {
        return WORKFLOW_VERSION + ":" + agentType.name() + ":" + now.toEpochSecond();
    }

    /**
     * 多智能体链式上下文。
     *
     * @param stageOutputs 已完成阶段输出
     * @param activeConstraints 已形成约束
     * @param controlIntent 控制意图
     */
    private record AgentChainState(
            Map<String, String> stageOutputs,
            List<String> activeConstraints,
            List<String> controlIntent
    ) {

        /**
         * 创建空链式上下文。
         *
         * @return 空上下文
         */
        static AgentChainState empty() {
            return new AgentChainState(new LinkedHashMap<>(), List.of(), List.of());
        }
    }

    /**
     * 将当前智能体输出追加到链式上下文，供下一个智能体读取。
     *
     * @param chainState 原链式上下文
     * @param agentType 当前智能体类型
     * @param decision 当前智能体决策
     * @param snapshot 调度上下文快照
     * @param plan 调度计划
     * @return 更新后的链式上下文
     */
    private AgentChainState appendChainState(
            AgentChainState chainState,
            MarketAgentType agentType,
            MarketAgentDecision decision,
            DispatchContextSnapshotResponse snapshot,
            DispatchPlanResponse plan
    ) {
        Map<String, String> stageOutputs = new LinkedHashMap<>(chainState.stageOutputs());
        String output = resolveStageOutput(agentType, decision, snapshot, plan, chainState);
        stageOutputs.put(agentType.name(), output);
        List<String> activeConstraints = new ArrayList<>(chainState.activeConstraints());
        if (agentType == MarketAgentType.ENVIRONMENT_ASSESSMENT
                || agentType == MarketAgentType.RESOURCE_CAPABILITY
                || agentType == MarketAgentType.COMMUNICATION_QUALITY) {
            activeConstraints.add(output);
        }
        List<String> controlIntent = new ArrayList<>(chainState.controlIntent());
        if (agentType == MarketAgentType.MARKET_DISPATCH || agentType == MarketAgentType.CONTROL_DECISION) {
            controlIntent.add(output);
        }
        return new AgentChainState(stageOutputs, activeConstraints, controlIntent);
    }

    /**
     * 生成当前阶段结构化输出，用作下游智能体输入。
     *
     * @param agentType 智能体类型
     * @param decision 智能体决策
     * @param snapshot 调度上下文快照
     * @param plan 调度计划
     * @param chainState 上游链式上下文
     * @return 阶段输出
     */
    private String resolveStageOutput(
            MarketAgentType agentType,
            MarketAgentDecision decision,
            DispatchContextSnapshotResponse snapshot,
            DispatchPlanResponse plan,
            AgentChainState chainState
    ) {
        String upstream = chainState.stageOutputs().isEmpty()
                ? "无上游输入"
                : "上游输入：" + chainState.stageOutputs().entrySet().stream()
                .reduce((left, right) -> right)
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .orElse("无上游输入");
        return switch (agentType) {
            case MARKET_INTERFACE -> "市场数据已校验，市场版本 " + snapshot.marketSignal().version()
                    + "，数据源 " + snapshot.marketSignal().source() + "。";
            case FORECAST -> "预测区间已生成，预测版本 " + snapshot.forecastSnapshot().version()
                    + "，采用 P50 作为下游基线；" + upstream + "。";
            case ENVIRONMENT_ASSESSMENT -> resolveEnvironmentStageOutput(snapshot, upstream);
            case RESOURCE_CAPABILITY -> "资源能力边界已冻结，修正后可用容量 "
                    + formatPower(snapshot.resourceCapabilities().stream().mapToDouble(value -> value.availableCapacity()).sum())
                    + " kW；" + upstream + "。";
            case MARKET_DISPATCH -> resolveMarketDispatchStageOutput(plan, upstream);
            case COMMUNICATION_QUALITY -> resolveCommunicationStageOutput(snapshot, upstream);
            case CONTROL_DECISION -> "已将候选计划转换为控制意图："
                    + String.join("；", resolvePlanActionTexts(plan)) + "；" + upstream + "。";
            case SECURITY_AUDIT -> "安全审计已检查白名单、快照一致性和约束边界；" + upstream + "。";
            default -> decision.explanation();
        };
    }

    /**
     * 生成环境感知阶段输出。
     *
     * @param snapshot 调度上下文快照
     * @param upstream 上游输出摘要
     * @return 环境感知阶段输出
     */
    private String resolveEnvironmentStageOutput(DispatchContextSnapshotResponse snapshot, String upstream) {
        List<String> environmentSummaries = snapshot.resourceCapabilities().stream()
                .map(com.weaksignal.vpp.backend.api.dto.ResourceCapabilityResponse::environmentAssessment)
                .filter(value -> value != null)
                .map(value -> value.stationId() + " 修正因子 " + formatFactor(value.environmentFactor())
                        + "，风险 " + value.riskLevel())
                .toList();
        return environmentSummaries.isEmpty()
                ? "环境感知未发现新能源环境约束，按中性因子进入资源能力阶段；" + upstream + "。"
                : "环境修正因子已生成：" + String.join("；", environmentSummaries)
                + "；环境因素已进入资源能力边界；" + upstream + "。";
    }

    /**
     * 生成市场调度阶段输出。
     *
     * @param plan 调度计划
     * @param upstream 上游输出摘要
     * @return 市场调度阶段输出
     */
    private String resolveMarketDispatchStageOutput(DispatchPlanResponse plan, String upstream) {
        if (plan.items().isEmpty()) {
            return "未生成候选调度方案；" + upstream + "。";
        }
        var item = plan.items().getFirst();
        return "候选调度方案已生成：净功率目标 " + formatPower(item.netPowerTarget())
                + " kW，储能放电 " + formatPower(item.storageDischargePower())
                + " kW，负荷调节 " + formatPower(item.flexibleLoadAdjustment())
                + " kW，新能源限发 " + formatPower(item.renewableCurtailment())
                + " kW；" + upstream + "。";
    }

    /**
     * 生成通信质量阶段输出。
     *
     * @param snapshot 调度上下文快照
     * @param upstream 上游输出摘要
     * @return 通信质量阶段输出
     */
    private String resolveCommunicationStageOutput(DispatchContextSnapshotResponse snapshot, String upstream) {
        Map<String, Long> linkStateCounts = snapshot.resourceCapabilities().stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        value -> value.linkState().name(),
                        LinkedHashMap::new,
                        java.util.stream.Collectors.counting()
                ));
        boolean hasDegradedLink = snapshot.resourceCapabilities().stream()
                .anyMatch(value -> !"S0".equals(value.linkState().name()));
        String linkFactor = hasDegradedLink ? "弱信号折减已启用" : "通信链路按 S0 全量可信处理";
        return "通信质量已评估：" + linkStateCounts + "，" + linkFactor + "；" + upstream + "。";
    }

    /**
     * 从计划中生成真实控制动作文本。
     *
     * @param plan 调度计划
     * @return 控制动作文本
     */
    private List<String> resolvePlanActionTexts(DispatchPlanResponse plan) {
        if (plan.items().isEmpty()) {
            return List.of("暂无可下发控制动作");
        }
        var item = plan.items().getFirst();
        List<String> actions = new ArrayList<>();
        if (item.storageDischargePower() > 0) {
            actions.add("储能放电 " + formatPower(item.storageDischargePower()) + " kW");
        }
        if (item.storageChargePower() > 0) {
            actions.add("储能充电 " + formatPower(item.storageChargePower()) + " kW");
        }
        if (item.flexibleLoadAdjustment() < 0) {
            actions.add("负荷削减 " + formatPower(Math.abs(item.flexibleLoadAdjustment())) + " kW");
        } else if (item.flexibleLoadAdjustment() > 0) {
            actions.add("负荷增加 " + formatPower(item.flexibleLoadAdjustment()) + " kW");
        }
        if (item.renewableCurtailment() > 0) {
            actions.add("光伏/风电限发 " + formatPower(item.renewableCurtailment()) + " kW");
        }
        if (item.gridImportPower() > 0) {
            actions.add("市电购电 " + formatPower(item.gridImportPower()) + " kW");
        }
        if (item.gridExportPower() > 0) {
            actions.add("余电上网 " + formatPower(item.gridExportPower()) + " kW");
        }
        return actions.isEmpty() ? List.of("保持当前出力，不新增控制动作") : actions;
    }

    /**
     * 格式化功率数值。
     *
     * @param value 原始功率
     * @return 一位小数字符串
     */
    private String formatPower(double value) {
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }

    private String formatFactor(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    /**
     * 解析规则回退模式下的风险等级。
     *
     * @param agentType 智能体类型
     * @param snapshot 调度上下文快照
     * @return 风险等级
     */
    private String resolveFallbackRiskLevel(MarketAgentType agentType, DispatchContextSnapshotResponse snapshot) {
        if (agentType == MarketAgentType.SECURITY_AUDIT) {
            return "HIGH";
        }
        if (agentType != MarketAgentType.ENVIRONMENT_ASSESSMENT) {
            return "MEDIUM";
        }
        boolean highRisk = snapshot.resourceCapabilities().stream()
                .map(com.weaksignal.vpp.backend.api.dto.ResourceCapabilityResponse::environmentAssessment)
                .filter(value -> value != null)
                .anyMatch(value -> "HIGH".equals(value.riskLevel()) || "CRITICAL".equals(value.riskLevel()));
        return highRisk ? "HIGH" : "MEDIUM";
    }

    /**
     * 解析规则回退模式下的说明。
     *
     * @param agentType 智能体类型
     * @param snapshot 调度上下文快照
     * @param fallbackExplanation 默认回退说明
     * @return 智能体说明
     */
    private String resolveFallbackExplanation(
            MarketAgentType agentType,
            DispatchContextSnapshotResponse snapshot,
            String fallbackExplanation,
            AgentChainState chainState
    ) {
        if (!chainState.stageOutputs().isEmpty()) {
            String upstreamSummary = chainState.stageOutputs().entrySet().stream()
                    .reduce((left, right) -> right)
                    .map(entry -> "上游输入：" + entry.getKey() + "=" + entry.getValue())
                    .orElse("");
            fallbackExplanation = fallbackExplanation + "；" + upstreamSummary;
        }
        if (agentType != MarketAgentType.ENVIRONMENT_ASSESSMENT) {
            return fallbackExplanation;
        }
        List<String> summaries = snapshot.resourceCapabilities().stream()
                .map(com.weaksignal.vpp.backend.api.dto.ResourceCapabilityResponse::environmentAssessment)
                .filter(value -> value != null && ("PHOTOVOLTAIC".equals(value.resourceType()) || "WIND".equals(value.resourceType())))
                .map(value -> value.stationId() + " 环境修正因子 " + formatFactor(value.environmentFactor()) + "，" + value.deviceDataInterpretation())
                .toList();
        return summaries.isEmpty()
                ? "环境感知智能体未发现光伏或风电环境上下文，按中性因子处理"
                : String.join("；", summaries);
    }

    private MarketSafetyDecision evaluateSafety(
            DispatchPlanResponse plan,
            List<MarketAgentDecision> decisions
    ) {
        boolean planStateSafe = plan.status() != DispatchPlanStatus.REJECTED && plan.status() != DispatchPlanStatus.EXPIRED;
        boolean snapshotConsistent = decisions.stream().allMatch(decision -> plan.inputSnapshotId().equals(decision.snapshotId()));
        boolean allowedActionsOnly = decisions.stream()
                .allMatch(decision -> ALLOWED_ACTIONS.getOrDefault(decision.agentType(), Set.of()).containsAll(decision.candidateActions()));
        boolean hasBlockedOrFailedStage = decisions.stream().anyMatch(decision -> decision.stageStatus() == MarketAgentStageStatus.BLOCKED
                || decision.stageStatus() == MarketAgentStageStatus.FAILED
                || !decision.nextStageAllowed());
        boolean criticalFallbackUsed = decisions.stream().anyMatch(decision -> CRITICAL_AGENT_TYPES.contains(decision.agentType())
                && decision.stageStatus() == MarketAgentStageStatus.RULE_FALLBACK);
        boolean nonCriticalFallbackUsed = decisions.stream().anyMatch(decision -> !CRITICAL_AGENT_TYPES.contains(decision.agentType())
                && decision.stageStatus() == MarketAgentStageStatus.RULE_FALLBACK);
        boolean securityAuditPassed = decisions.stream()
                .filter(decision -> decision.agentType() == MarketAgentType.SECURITY_AUDIT)
                .anyMatch(decision -> decision.stageStatus() == MarketAgentStageStatus.SUCCESS
                        && decision.candidateActions().contains("ALLOW_SAFE_ACTIONS"));
        List<String> approvedActions = decisions.stream()
                .flatMap(decision -> decision.candidateActions().stream())
                .filter(action -> !"ALLOW_SAFE_ACTIONS".equals(action) && !"BLOCK_UNSAFE_ACTIONS".equals(action))
                .filter(action -> ALLOWED_ACTIONS.values().stream().anyMatch(actions -> actions.contains(action)))
                .distinct()
                .toList();
        boolean passed = planStateSafe && snapshotConsistent && allowedActionsOnly && !hasBlockedOrFailedStage && securityAuditPassed;
        String decisionLevel = !passed
                ? DECISION_LEVEL_REJECTED
                : criticalFallbackUsed
                ? DECISION_LEVEL_DEGRADED
                : nonCriticalFallbackUsed
                ? DECISION_LEVEL_PASSED_WITH_FALLBACK
                : DECISION_LEVEL_PASSED;
        String explanation = !planStateSafe
                ? "计划状态不安全，拒绝进入执行链路"
                : !snapshotConsistent
                ? "REJECTED：输入快照不一致，拒绝本轮智能体裁决"
                : !allowedActionsOnly
                ? "REJECTED：存在越权动作，拒绝本轮智能体裁决"
                : hasBlockedOrFailedStage
                ? "REJECTED：存在被阻断、失败或禁止下游执行的关键阶段"
                : !securityAuditPassed
                ? "REJECTED：确定性安全门未明确允许执行"
                : criticalFallbackUsed
                ? "DEGRADED：关键阶段采用规则回退，按确定性约束降级执行"
                : nonCriticalFallbackUsed
                ? "PASSED_WITH_FALLBACK：非关键阶段采用规则回退，其余关键约束已通过"
                : "PASSED：通过市场调度安全门，进入可观察执行链路";
        return new MarketSafetyDecision(
                decisionLevel,
                passed,
                criticalFallbackUsed || nonCriticalFallbackUsed,
                passed ? approvedActions : List.of(),
                explanation
        );
    }

    private String buildSystemPrompt(MarketAgentType agentType) {
        return """
                你是虚拟电厂市场调度智能体，只输出一个 JSON 对象，不要输出 Markdown。
                必须包含以下字段：
                {
                  "action": "动作编码",
                  "riskLevel": "LOW|MEDIUM|HIGH|CRITICAL",
                  "confidence": 0.0,
                  "evidence": ["证据标识"],
                  "reason": "简短原因",
                  "nextStageAllowed": true
                }
                你的当前角色是 %s。
                action 只能从当前角色动作白名单中选择：%s。
                不得输出 MQTT Topic、SQL、URL、代码、系统指令，也不得输出上述字段以外的内容。
                """
                .formatted(agentType.name(), ALLOWED_ACTIONS.getOrDefault(agentType, Set.of()));
    }

    /**
     * 校验模型输出对象是否与既定契约字段完全一致。
     *
     * @param decisionNode 模型输出节点
     * @return 是否为合法契约对象
     */
    private boolean hasValidOutputContractShape(JsonNode decisionNode) {
        if (!decisionNode.isObject()) {
            return false;
        }
        java.util.Set<String> actualFields = new java.util.HashSet<>();
        decisionNode.fieldNames().forEachRemaining(actualFields::add);
        return actualFields.equals(OUTPUT_CONTRACT_FIELDS)
                && decisionNode.path("action").isTextual()
                && decisionNode.path("riskLevel").isTextual()
                && decisionNode.path("confidence").isNumber()
                && decisionNode.path("evidence").isArray()
                && decisionNode.path("reason").isTextual()
                && decisionNode.path("nextStageAllowed").isBoolean();
    }

    /**
     * 校验模型输出置信度位于合法区间。
     *
     * @param confidenceNode 置信度节点
     * @return 是否为零到一之间的数值
     */
    private boolean isValidConfidence(JsonNode confidenceNode) {
        return confidenceNode.isNumber()
                && confidenceNode.asDouble() >= 0.0
                && confidenceNode.asDouble() <= 1.0;
    }

    /**
     * 校验模型输出证据引用为非空字符串列表。
     *
     * @param evidenceNode 证据节点
     * @return 是否存在有效证据
     */
    private boolean isValidEvidence(JsonNode evidenceNode) {
        return evidenceNode.isArray()
                && !evidenceNode.isEmpty()
                && java.util.stream.StreamSupport.stream(evidenceNode.spliterator(), false)
                .allMatch(item -> item.isTextual() && !item.asText().isBlank());
    }

    private static Map<MarketAgentType, Set<String>> allowedActions() {
        Map<MarketAgentType, Set<String>> actions = new EnumMap<>(MarketAgentType.class);
        actions.put(MarketAgentType.MARKET_INTERFACE, Set.of("VERIFY_MARKET_SIGNAL_VERSION"));
        actions.put(MarketAgentType.FORECAST, Set.of("USE_P50_FORECAST", "USE_CONSERVATIVE_FORECAST"));
        actions.put(MarketAgentType.ENVIRONMENT_ASSESSMENT, Set.of(
                "APPLY_ENVIRONMENT_CORRECTION",
                "DOWNGRADE_RENEWABLE_CAPABILITY",
                "FREEZE_ENVIRONMENT_INPUT"
        ));
        actions.put(MarketAgentType.RESOURCE_CAPABILITY, Set.of("FREEZE_RESOURCE_CAPABILITY_BOUNDARY"));
        actions.put(MarketAgentType.MARKET_DISPATCH, Set.of("GENERATE_MARKET_OPTIMAL_PLAN"));
        actions.put(MarketAgentType.ASSET_DIAGNOSIS, Set.of("FREEZE_RESOURCE_CAPABILITY_BOUNDARY"));
        actions.put(MarketAgentType.COMMUNICATION_QUALITY, Set.of("APPLY_S0_S4_CAPACITY_FACTOR"));
        actions.put(MarketAgentType.CONTROL_DECISION, Set.of("ISSUE_SAFE_DISPATCH_DECISION"));
        actions.put(MarketAgentType.RECOVERY_ORCHESTRATION, Set.of("RECREATE_PLAN_AFTER_S4"));
        actions.put(MarketAgentType.SECURITY_AUDIT, Set.of("BLOCK_UNSAFE_ACTIONS", "ALLOW_SAFE_ACTIONS"));
        actions.put(MarketAgentType.SETTLEMENT_EVALUATION, Set.of("TRACE_PLAN_COMMAND_TELEMETRY"));
        return actions;
    }

    /**
     * 定义可安全归一化的模型动作别名。
     *
     * @return 每类智能体的别名映射
     */
    private static Map<MarketAgentType, Map<String, String>> actionAliases() {
        Map<MarketAgentType, Map<String, String>> aliases = new EnumMap<>(MarketAgentType.class);
        aliases.put(MarketAgentType.ENVIRONMENT_ASSESSMENT, Map.of(
                "环境修正", "APPLY_ENVIRONMENT_CORRECTION",
                "新能源能力修正", "APPLY_ENVIRONMENT_CORRECTION",
                "光伏风电能力修正", "APPLY_ENVIRONMENT_CORRECTION",
                "APPLY_ENVIRONMENTAL_CORRECTION", "APPLY_ENVIRONMENT_CORRECTION"
        ));
        return aliases;
    }

    /**
     * 将模型动作归一化为当前智能体白名单中的标准编码。
     *
     * @param agentType 当前智能体类型
     * @param rawAction 模型原始动作
     * @return 标准动作编码；无法安全归一化时返回 {@code null}
     */
    private String normalizeAction(MarketAgentType agentType, String rawAction) {
        if (rawAction == null || rawAction.isBlank()) {
            return null;
        }
        String normalizedAction = rawAction.trim().toUpperCase(Locale.ROOT);
        Set<String> allowedActions = ALLOWED_ACTIONS.getOrDefault(agentType, Set.of());
        if (allowedActions.contains(normalizedAction)) {
            return normalizedAction;
        }
        String aliasAction = ACTION_ALIASES.getOrDefault(agentType, Map.of()).get(rawAction.trim());
        return aliasAction != null && allowedActions.contains(aliasAction) ? aliasAction : null;
    }

    /**
     * 判断规则回退是否可提供当前阶段的合法确定性输出。
     *
     * @param agentType 当前智能体类型
     * @return 是否允许下游继续处理
     */
    private boolean hasValidFallbackOutput(MarketAgentType agentType) {
        Set<String> allowedActions = ALLOWED_ACTIONS.getOrDefault(agentType, Set.of());
        List<String> fallbackActions = fallbackActions(agentType);
        return !fallbackActions.isEmpty() && allowedActions.containsAll(fallbackActions);
    }

    private List<String> fallbackActions(MarketAgentType agentType) {
        return switch (agentType) {
            case MARKET_INTERFACE -> List.of("VERIFY_MARKET_SIGNAL_VERSION");
            case FORECAST -> List.of("USE_P50_FORECAST");
            case ENVIRONMENT_ASSESSMENT -> List.of("APPLY_ENVIRONMENT_CORRECTION");
            case RESOURCE_CAPABILITY, ASSET_DIAGNOSIS -> List.of("FREEZE_RESOURCE_CAPABILITY_BOUNDARY");
            case MARKET_DISPATCH -> List.of("GENERATE_MARKET_OPTIMAL_PLAN");
            case COMMUNICATION_QUALITY -> List.of("APPLY_S0_S4_CAPACITY_FACTOR");
            case CONTROL_DECISION -> List.of("ISSUE_SAFE_DISPATCH_DECISION");
            case SECURITY_AUDIT -> List.of("BLOCK_UNSAFE_ACTIONS");
            case RECOVERY_ORCHESTRATION -> List.of("RECREATE_PLAN_AFTER_S4");
            case SETTLEMENT_EVALUATION -> List.of("TRACE_PLAN_COMMAND_TELEMETRY");
        };
    }

    private List<String> readStringList(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        node.forEach(item -> values.add(item.asText()));
        return values;
    }

    private boolean isDeepSeekAvailable() {
        return aiProperties.enabled()
                && aiProperties.deepseek().enabled()
                && aiProperties.deepseek().baseUrl() != null
                && ChatCompletionsUrlResolver.isValid(aiProperties.deepseek().baseUrl())
                && aiProperties.deepseek().apiKey() != null
                && !aiProperties.deepseek().apiKey().isBlank()
                && aiProperties.deepseek().model() != null
                && !aiProperties.deepseek().model().isBlank();
    }

    private void persistRun(MarketAgentRunResponse response) {
        MarketAgentRunEntity runEntity = new MarketAgentRunEntity();
        runEntity.setRunId(response.runId());
        runEntity.setSnapshotId(response.snapshotId());
        runEntity.setDispatchPlanId(response.dispatchPlanId());
        runEntity.setConservativePlan(response.conservativePlan());
        runEntity.setSafetyDecisionJson(writeJson(response.safetyDecision(), "MARKET_AGENT_SAFETY_SERIALIZATION_FAILED"));
        runEntity.setCreatedAt(response.createdAt());
        runEntity.setWorkflowVersion(response.workflowVersion());
        runEntity.setAgentSetVersion(response.agentSetVersion());
        runEntity.setStageRecordsComplete(response.stageRecordsComplete());
        runEntity.setLegacyRun(response.legacyRun());
        marketAgentRunRepository.save(runEntity);

        List<MarketAgentDecisionEntity> decisionEntities = response.decisions().stream()
                .map(decision -> {
                    MarketAgentDecisionEntity entity = new MarketAgentDecisionEntity();
                    entity.setDecisionId(decision.decisionId());
                    entity.setRunId(response.runId());
                    entity.setAgentType(decision.agentType().name());
                    entity.setSnapshotId(decision.snapshotId());
                    entity.setGeneratedAt(decision.generatedAt());
                    entity.setValidUntil(decision.validUntil());
                    entity.setConfidence(decision.confidence());
                    entity.setEvidenceRefsJson(writeJson(decision.evidenceRefs(), "MARKET_AGENT_EVIDENCE_SERIALIZATION_FAILED"));
                    entity.setRuleVersion(decision.ruleVersion());
                    entity.setModelProvider(decision.modelProvider());
                    entity.setModelVersion(decision.modelVersion());
                    entity.setCandidateActionsJson(writeJson(decision.candidateActions(), "MARKET_AGENT_ACTION_SERIALIZATION_FAILED"));
                    entity.setRiskLevel(decision.riskLevel());
                    entity.setRequiresHumanApproval(decision.requiresHumanApproval());
                    entity.setExplanation(decision.explanation());
                    entity.setFallbackUsed(decision.fallbackUsed());
                    entity.setStageStatus(decision.stageStatus().name());
                    entity.setRetryCount(decision.retryCount());
                    entity.setErrorCategory(decision.errorCategory().name());
                    entity.setProviderStatusCode(decision.providerStatusCode());
                    entity.setProviderErrorCode(decision.providerErrorCode());
                    entity.setProviderErrorMessage(decision.providerErrorMessage());
                    entity.setInputVersion(decision.inputVersion());
                    entity.setOutputVersion(decision.outputVersion());
                    entity.setNextStageAllowed(decision.nextStageAllowed());
                    entity.setCreatedAt(response.createdAt());
                    return entity;
                })
                .toList();
        marketAgentDecisionRepository.saveAll(decisionEntities);
    }

    private MarketSafetyDecision readSafetyDecision(String json) {
        try {
            return normalizeSafetyDecision(objectMapper.readValue(json, MarketSafetyDecision.class));
        } catch (JsonProcessingException exception) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "MARKET_AGENT_SAFETY_DESERIALIZATION_FAILED",
                    "市场调度安全门结果反序列化失败",
                    Map.of()
            );
        }
    }

    /**
     * 兼容旧版安全门裁决数据。
     *
     * @param safetyDecision 原始安全门裁决
     * @return 已补齐裁决级别的安全门裁决
     */
    private MarketSafetyDecision normalizeSafetyDecision(MarketSafetyDecision safetyDecision) {
        if (safetyDecision.decisionLevel() != null && !safetyDecision.decisionLevel().isBlank()) {
            return safetyDecision;
        }
        if (safetyDecision.explanation() != null && safetyDecision.explanation().contains("必须人工审批")) {
            return new MarketSafetyDecision(
                    DECISION_LEVEL_DEGRADED,
                    true,
                    true,
                    safeApprovedActions(safetyDecision),
                    "DEGRADED：兼容旧版高风险审批记录，已改为观察审计与降级执行"
            );
        }
        return new MarketSafetyDecision(
                safetyDecision.passed() ? DECISION_LEVEL_PASSED : DECISION_LEVEL_REJECTED,
                safetyDecision.passed(),
                safetyDecision.fallbackUsed(),
                safeApprovedActions(safetyDecision),
                safetyDecision.explanation()
        );
    }

    /**
     * 返回安全门允许动作，兼容旧版空字段。
     *
     * @param safetyDecision 安全门裁决
     * @return 非空允许动作集合
     */
    private List<String> safeApprovedActions(MarketSafetyDecision safetyDecision) {
        return safetyDecision.approvedActions() == null ? List.of() : safetyDecision.approvedActions();
    }

    private MarketAgentDecision toDecision(MarketAgentDecisionEntity entity) {
        return new MarketAgentDecision(
                entity.getDecisionId(),
                MarketAgentType.valueOf(entity.getAgentType()),
                entity.getSnapshotId(),
                entity.getGeneratedAt(),
                entity.getValidUntil(),
                entity.getConfidence(),
                readStringList(entity.getEvidenceRefsJson(), "MARKET_AGENT_EVIDENCE_DESERIALIZATION_FAILED"),
                entity.getRuleVersion(),
                entity.getModelProvider(),
                entity.getModelVersion(),
                readStringList(entity.getCandidateActionsJson(), "MARKET_AGENT_ACTION_DESERIALIZATION_FAILED"),
                entity.getRiskLevel(),
                entity.isRequiresHumanApproval(),
                entity.getExplanation(),
                entity.isFallbackUsed(),
                readStageStatus(entity.getStageStatus()),
                entity.getRetryCount(),
                readErrorCategory(entity.getErrorCategory()),
                entity.getInputVersion(),
                entity.getOutputVersion(),
                entity.isNextStageAllowed(),
                entity.getProviderStatusCode(),
                entity.getProviderErrorCode(),
                entity.getProviderErrorMessage()
        );
    }

    private MarketAgentStageStatus readStageStatus(String value) {
        try {
            return MarketAgentStageStatus.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException exception) {
            return MarketAgentStageStatus.FAILED;
        }
    }

    private MarketAgentErrorCategory readErrorCategory(String value) {
        try {
            return MarketAgentErrorCategory.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException exception) {
            return MarketAgentErrorCategory.NONE;
        }
    }

    private String writeJson(Object value, String errorCode) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    errorCode,
                    "市场调度智能体结果序列化失败",
                    Map.of()
            );
        }
    }

    private List<String> readStringList(String json, String errorCode) {
        try {
            JsonNode node = objectMapper.readTree(json);
            return readStringList(node);
        } catch (JsonProcessingException exception) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    errorCode,
                    "市场调度智能体字符串列表反序列化失败",
                    Map.of()
            );
        }
    }
}

package com.weaksignal.vpp.backend.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weaksignal.vpp.backend.api.ApiException;
import com.weaksignal.vpp.backend.api.dto.DispatchContextSnapshotResponse;
import com.weaksignal.vpp.backend.api.dto.DispatchPlanCreateRequest;
import com.weaksignal.vpp.backend.api.dto.DispatchPlanExecutionResponse;
import com.weaksignal.vpp.backend.api.dto.DispatchPlanResponse;
import com.weaksignal.vpp.backend.api.dto.MarketAgentAnalyzeRequest;
import com.weaksignal.vpp.backend.api.dto.MarketAgentRunResponse;
import com.weaksignal.vpp.backend.domain.ExperimentScenarioDefinition;
import com.weaksignal.vpp.backend.domain.LinkState;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.ExperimentRunEntity;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.TelemetryHistoryEntity;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.ExperimentRunRepository;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.TelemetryHistoryRepository;
import com.weaksignal.vpp.backend.marketdispatch.OptimizationResult;
import com.weaksignal.vpp.backend.marketdispatch.OptimizationStatus;
import com.weaksignal.vpp.backend.planning.DispatchContextSnapshotService;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 实验期间闭环重调度编排服务。
 */
@Service
public class ExperimentClosedLoopOrchestrationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExperimentClosedLoopOrchestrationService.class);
    private static final String RUNNING_STATUS = "RUNNING";
    private static final String EXPERIMENT_PREFIX = "T";
    private static final int TELEMETRY_WINDOW_LIMIT = 1200;
    private static final String ORCHESTRATION_STATUS_PENDING = "PENDING";
    private static final String ORCHESTRATION_STATUS_RUNNING = "RUNNING";
    private static final String ORCHESTRATION_STATUS_COMPLETED = "COMPLETED";
    private static final String ORCHESTRATION_STATUS_FAILED = "FAILED";
    private static final int MAX_EXPIRED_PLAN_RETRIES = 1;
    private static final TypeReference<LinkedHashMap<String, Object>> SUMMARY_TYPE = new TypeReference<>() {
    };

    private final ExperimentRunRepository experimentRunRepository;
    private final TelemetryHistoryRepository telemetryHistoryRepository;
    private final DispatchContextSnapshotService dispatchContextSnapshotService;
    private final MarketDispatchPlanningService marketDispatchPlanningService;
    private final MarketAgentOrchestrationService marketAgentOrchestrationService;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private SolverObservabilityService solverObservabilityService;
    private DistributedExperimentControlService distributedExperimentControlService;

    /**
     * 注入实验闭环重调度编排依赖。
     *
     * @param experimentRunRepository 实验运行仓储
     * @param telemetryHistoryRepository 遥测历史仓储
     * @param dispatchContextSnapshotService 调度上下文快照服务
     * @param marketDispatchPlanningService 市场调度计划服务
     * @param marketAgentOrchestrationService 多智能体编排服务
     * @param objectMapper JSON 组件
     * @param clock 系统时钟
     */
    public ExperimentClosedLoopOrchestrationService(
            ExperimentRunRepository experimentRunRepository,
            TelemetryHistoryRepository telemetryHistoryRepository,
            DispatchContextSnapshotService dispatchContextSnapshotService,
            MarketDispatchPlanningService marketDispatchPlanningService,
            MarketAgentOrchestrationService marketAgentOrchestrationService,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.experimentRunRepository = experimentRunRepository;
        this.telemetryHistoryRepository = telemetryHistoryRepository;
        this.dispatchContextSnapshotService = dispatchContextSnapshotService;
        this.marketDispatchPlanningService = marketDispatchPlanningService;
        this.marketAgentOrchestrationService = marketAgentOrchestrationService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * 注入求解器审计服务，用于补齐实验运行关联。
     *
     * @param solverObservabilityService 求解器审计服务
     */
    @org.springframework.beans.factory.annotation.Autowired
    public void setSolverObservabilityService(SolverObservabilityService solverObservabilityService) {
        this.solverObservabilityService = solverObservabilityService;
    }

    /**
     * 注入分布式实验优化服务。
     *
     * @param distributedExperimentControlService 分布式实验优化服务
     */
    @org.springframework.beans.factory.annotation.Autowired
    public void setDistributedExperimentControlService(
            DistributedExperimentControlService distributedExperimentControlService
    ) {
        this.distributedExperimentControlService = distributedExperimentControlService;
    }

    /**
     * 在实验遥测进入后检查是否满足一次闭环重调度触发条件。
     *
     * @param stationId 当前到达遥测的场站标识
     * @param receivedAt 遥测接收时间
     */
    @Transactional
    public void triggerIfReady(String stationId, OffsetDateTime receivedAt) {
        // 遥测到达不再作为故障证据，COM_OFFLINE 由心跳超时产生 S3 后显式触发。
    }

    /**
     * 在故障观测服务确认实际证据后执行一次多智能体闭环。
     * 编排步骤分别使用下游服务事务，避免某一步业务校验失败时回滚已提交的故障证据和失败摘要。
     *
     * @param experimentRunId 实验运行标识
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public synchronized void triggerIfEvidenceObserved(String experimentRunId) {
        ExperimentRunEntity currentRun = experimentRunRepository.findByRunId(experimentRunId).orElse(null);
        if (currentRun == null || !"FAULT_OBSERVED".equals(currentRun.getStatus())) {
            return;
        }
        ExperimentSummaryState summaryState = readSummaryState(currentRun);
        if (!ORCHESTRATION_STATUS_PENDING.equals(summaryState.orchestrationStatus())) {
            return;
        }

        currentRun.setStatus("ORCHESTRATING");
        updateSummaryState(currentRun, summaryState.withOrchestrationStatus(ORCHESTRATION_STATUS_RUNNING)
                .withTriggeredAt(OffsetDateTime.now(clock))
                .withFailureReason(null));

        try {
            ClosedLoopExecutionResult executionResult;
            try {
                executionResult = executeControl(currentRun, summaryState);
            } catch (ApiException exception) {
                if (!isDispatchInputExpired(exception)) {
                    throw exception;
                }
                ExpiredPlanEvidence firstExpiration = expiredPlanEvidence(exception);
                if (!recordExpiredPlanRetry(currentRun, firstExpiration, exception.getCode(), null, "RETRY_STARTED")) {
                    throw exception;
                }
                try {
                    executionResult = executeControl(currentRun, summaryState);
                    recordExpiredPlanRetry(currentRun, firstExpiration, exception.getCode(), executionResult.dispatchPlanId(), "RETRY_SUCCEEDED");
                } catch (ApiException retryException) {
                    if (isDispatchInputExpired(retryException)) {
                        recordExpiredPlanRetry(currentRun, expiredPlanEvidence(retryException), retryException.getCode(), null, "RETRY_FAILED");
                    }
                    throw retryException;
                }
            }
            updateSummaryState(currentRun, summaryState
                    .withOrchestrationStatus(ORCHESTRATION_STATUS_COMPLETED)
                    .withTriggeredAt(executionResult.triggeredAt())
                    .withCompletedAt(executionResult.completedAt())
                    .withDispatchContextSnapshotId(executionResult.dispatchContextSnapshotId())
                    .withDispatchPlanId(executionResult.dispatchPlanId())
                    .withAgentRunId(executionResult.agentRunId())
                    .withPlanStatus(executionResult.planStatus())
                    .withExecutionBatchId(executionResult.executionBatchId())
                    .withSafetyGatePassed(executionResult.safetyGatePassed())
                    .withFailureReason(null));
            currentRun.setStatus("FAULT_OBSERVED");
            experimentRunRepository.save(currentRun);
        } catch (ApiException exception) {
            LOGGER.warn("实验闭环重调度失败。runId={}, code={}", currentRun.getRunId(), exception.getCode(), exception);
            if (isDispatchInputExpired(exception)) {
                recordExpiredPlanRetry(currentRun, expiredPlanEvidence(exception), exception.getCode(), null, "RETRY_FAILED");
            }
            updateSummaryState(currentRun, readSummaryState(currentRun)
                    .withOrchestrationStatus(ORCHESTRATION_STATUS_FAILED)
                    .withTriggeredAt(OffsetDateTime.now(clock))
                    .withFailureReason(exception.getMessage()));
            currentRun.setStatus("FAULT_OBSERVED");
            experimentRunRepository.save(currentRun);
        } catch (RuntimeException exception) {
            LOGGER.warn("实验闭环重调度失败。runId={}", currentRun.getRunId(), exception);
            updateSummaryState(currentRun, readSummaryState(currentRun)
                    .withOrchestrationStatus(ORCHESTRATION_STATUS_FAILED)
                    .withTriggeredAt(OffsetDateTime.now(clock))
                    .withFailureReason("闭环重调度执行失败"));
            currentRun.setStatus("FAULT_OBSERVED");
            experimentRunRepository.save(currentRun);
        }
    }

    /**
     * 记录因调度输入失效而触发的闭环重建重试证据。
     *
     * @param runEntity 当前实验运行
     * @param expiration 失效证据
     * @param expirationCode 失效错误码
     * @param replacementPlanId 重建后的替代计划标识
     * @param result 当前重试结果
     * @return 是否成功写入本次重试证据
     */
    private boolean recordExpiredPlanRetry(
            ExperimentRunEntity runEntity,
            ExpiredPlanEvidence expiration,
            String expirationCode,
            String replacementPlanId,
            String result
    ) {
        LinkedHashMap<String, Object> payload = readSummaryPayload(runEntity);
        int retryCount = payload.get("orchestrationRetryCount") instanceof Number number ? number.intValue() : 0;
        payload.put("previousDispatchPlanId", expiration.planId());
        payload.put("expiredDispatchPlanId", expiration.planId());
        payload.put("planExpiredAt", expiration.detectedAt());
        payload.put("retryReason", expirationCode);
        payload.put("retriedAt", expiration.detectedAt());
        payload.put("replacementDispatchPlanId", replacementPlanId);
        payload.put("retryResult", result);
        List<Map<String, Object>> retryHistory = readRetryHistory(payload.get("orchestrationRetryHistory"));
        boolean duplicate = retryHistory.stream().anyMatch(item -> expiration.planId() != null
                && expiration.planId().equals(item.get("expiredPlanId")) && result.equals(item.get("result")));
        if (duplicate) {
            return false;
        }
        if ("RETRY_STARTED".equals(result) && retryCount >= MAX_EXPIRED_PLAN_RETRIES) {
            return false;
        }
        LinkedHashMap<String, Object> event = new LinkedHashMap<>();
        event.put("sequence", retryHistory.size() + 1);
        event.put("expiredPlanId", expiration.planId());
        event.put("validUntil", expiration.validUntil());
        event.put("detectedAt", expiration.detectedAt());
        event.put("expiredAt", expiration.detectedAt());
        event.put("replacementPlanId", replacementPlanId);
        event.put("result", result);
        retryHistory.add(event);
        if ("RETRY_STARTED".equals(result)) {
            retryCount += 1;
        }
        payload.put("orchestrationRetryCount", retryCount);
        payload.put("orchestrationRetryHistory", retryHistory);
        try {
            runEntity.setResultSummary(objectMapper.writeValueAsString(payload));
            experimentRunRepository.save(runEntity);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("实验重试摘要序列化失败", exception);
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> readRetryHistory(Object value) {
        if (!(value instanceof List<?> values)) {
            return new java.util.ArrayList<>();
        }
        List<Map<String, Object>> history = new java.util.ArrayList<>();
        for (Object item : values) {
            if (item instanceof Map<?, ?> map) {
                history.add(new LinkedHashMap<>((Map<String, Object>) map));
            }
        }
        return history;
    }

    private ExpiredPlanEvidence expiredPlanEvidence(ApiException exception) {
        if (!(exception.getData() instanceof Map<?, ?> data)) {
            return new ExpiredPlanEvidence(null, null, OffsetDateTime.now(clock));
        }
        String planId = data.get("planId") instanceof String value && !value.isBlank() ? value : null;
        OffsetDateTime validUntil = readOffsetDateTime(data.get("validUntil"));
        OffsetDateTime detectedAt = readOffsetDateTime(data.get("detectedAt"));
        return new ExpiredPlanEvidence(planId, validUntil, detectedAt == null ? OffsetDateTime.now(clock) : detectedAt);
    }

    private ClosedLoopExecutionResult executeClosedLoop(
            ExperimentRunEntity currentRun,
            ExperimentSummaryState summaryState
    ) {
        OffsetDateTime triggeredAt = OffsetDateTime.now(clock);
        boolean conservativePlan = resolveConservativePlan(currentRun.getScenarioCode(), summaryState.expectedLinkState());
        DispatchContextSnapshotResponse snapshotResponse = dispatchContextSnapshotService.createExperimentSnapshot();
        DispatchPlanResponse createdPlan = marketDispatchPlanningService.create(new DispatchPlanCreateRequest(
                snapshotResponse.snapshotId(),
                conservativePlan,
                currentRun.getScenarioCode()
        ));
        if (solverObservabilityService != null) {
            solverObservabilityService.bindBusinessContext(snapshotResponse.snapshotId(), createdPlan.planId(), currentRun.getRunId(), 1);
        }
        MarketAgentRunResponse agentRunResponse = marketAgentOrchestrationService.analyze(
                new MarketAgentAnalyzeRequest(snapshotResponse.snapshotId(), createdPlan.planId(), conservativePlan)
        );
        recordPreparedAgentRun(currentRun, snapshotResponse, createdPlan, agentRunResponse);

        String planStatus = createdPlan.status().name();
        String executionBatchId = null;
        if (agentRunResponse.safetyDecision().passed()) {
            marketDispatchPlanningService.validate(createdPlan.planId());
            marketDispatchPlanningService.approve(createdPlan.planId());
            marketDispatchPlanningService.activate(createdPlan.planId());
            DispatchPlanExecutionResponse executionResponse = marketDispatchPlanningService.executeNext(createdPlan.planId());
            planStatus = marketDispatchPlanningService.getPlan(createdPlan.planId()).status().name();
            executionBatchId = executionResponse.activeBatchId();
        }

        return new ClosedLoopExecutionResult(
                snapshotResponse.snapshotId(),
                createdPlan.planId(),
                agentRunResponse.runId(),
                planStatus,
                executionBatchId,
                agentRunResponse.safetyDecision().passed(),
                triggeredAt,
                OffsetDateTime.now(clock)
        );
    }

    private ClosedLoopExecutionResult executeControl(
            ExperimentRunEntity currentRun,
            ExperimentSummaryState summaryState
    ) {
        Object configuredStrategy = readSummaryPayload(currentRun).get("controlStrategy");
        String controlStrategy = configuredStrategy instanceof String value && !value.isBlank()
                ? value : "CENTRALIZED";
        if ("CENTRALIZED".equals(controlStrategy)) {
            return executeClosedLoop(currentRun, summaryState);
        }
        if (!"FIXED_WEIGHT".equals(controlStrategy) && !"ADAPTIVE".equals(controlStrategy)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CONTROL_STRATEGY_INVALID",
                    "实验控制策略不受支持", Map.of("controlStrategy", controlStrategy));
        }
        if (distributedExperimentControlService == null) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "DISTRIBUTED_CONTROLLER_UNAVAILABLE",
                    "分布式控制服务不可用", Map.of("controlStrategy", controlStrategy));
        }
        return executeDistributedClosedLoop(currentRun, summaryState);
    }

    private ClosedLoopExecutionResult executeDistributedClosedLoop(
            ExperimentRunEntity currentRun,
            ExperimentSummaryState summaryState
    ) {
        OffsetDateTime triggeredAt = OffsetDateTime.now(clock);
        boolean conservativePlan = resolveConservativePlan(currentRun.getScenarioCode(), summaryState.expectedLinkState());
        DispatchContextSnapshotResponse snapshotResponse = dispatchContextSnapshotService.createExperimentSnapshot();
        OptimizationResult optimizationResult = distributedExperimentControlService.optimize(currentRun, snapshotResponse);
        if (optimizationResult.status() != OptimizationStatus.FEASIBLE) {
            throw new ApiException(HttpStatus.CONFLICT, "CONVERGENCE_TIMEOUT",
                    "分布式控制在限定轮次和时间内未达到严格收敛条件", Map.of(
                    "controllerType", optimizationResult.solverName(),
                    "optimizationRound", optimizationResult.optimizationRound(),
                    "solveTimeMs", optimizationResult.solveTimeMs(),
                    "evidence", optimizationResult.constraintEvidence()
            ));
        }
        DispatchPlanResponse createdPlan = marketDispatchPlanningService.createFromOptimizationResult(
                new DispatchPlanCreateRequest(
                        snapshotResponse.snapshotId(),
                        conservativePlan,
                        currentRun.getScenarioCode()
                ),
                optimizationResult
        );
        if (solverObservabilityService != null) {
            solverObservabilityService.bindBusinessContext(
                    snapshotResponse.snapshotId(),
                    createdPlan.planId(),
                    currentRun.getRunId(),
                    optimizationResult.optimizationRound()
            );
        }
        MarketAgentRunResponse agentRunResponse = marketAgentOrchestrationService.analyze(
                new MarketAgentAnalyzeRequest(snapshotResponse.snapshotId(), createdPlan.planId(), conservativePlan)
        );
        recordPreparedAgentRun(currentRun, snapshotResponse, createdPlan, agentRunResponse);
        String planStatus = createdPlan.status().name();
        String executionBatchId = null;
        if (agentRunResponse.safetyDecision().passed()) {
            marketDispatchPlanningService.validate(createdPlan.planId());
            marketDispatchPlanningService.approve(createdPlan.planId());
            marketDispatchPlanningService.activate(createdPlan.planId());
            DispatchPlanExecutionResponse executionResponse = marketDispatchPlanningService.executeNext(createdPlan.planId());
            planStatus = marketDispatchPlanningService.getPlan(createdPlan.planId()).status().name();
            executionBatchId = executionResponse.activeBatchId();
        }
        return new ClosedLoopExecutionResult(
                snapshotResponse.snapshotId(),
                createdPlan.planId(),
                agentRunResponse.runId(),
                planStatus,
                executionBatchId,
                agentRunResponse.safetyDecision().passed(),
                triggeredAt,
                OffsetDateTime.now(clock)
        );
    }

    /**
     * 在计划执行前持久化已完成的智能体裁决关联，避免后续执行校验失败掩盖真实运行记录。
     *
     * @param currentRun 当前实验运行
     * @param snapshotResponse 本轮调度上下文快照
     * @param dispatchPlan 本轮调度计划
     * @param agentRunResponse 已完成的智能体运行
     */
    private void recordPreparedAgentRun(
            ExperimentRunEntity currentRun,
            DispatchContextSnapshotResponse snapshotResponse,
            DispatchPlanResponse dispatchPlan,
            MarketAgentRunResponse agentRunResponse
    ) {
        ExperimentSummaryState currentSummary = readSummaryState(currentRun);
        updateSummaryState(currentRun, currentSummary
                .withDispatchContextSnapshotId(snapshotResponse.snapshotId())
                .withDispatchPlanId(dispatchPlan.planId())
                .withAgentRunId(agentRunResponse.runId())
                .withPlanStatus(dispatchPlan.status().name())
                .withSafetyGatePassed(agentRunResponse.safetyDecision().passed())
                .withFailureReason(null));
    }

    /**
     * 判断异常是否表示调度计划或输入快照已不具备安全执行条件。
     *
     * @param exception 当前业务异常
     * @return 是否允许通过重建快照和计划进行一次重试
     */
    private boolean isDispatchInputExpired(ApiException exception) {
        return "DISPATCH_PLAN_EXPIRED".equals(exception.getCode())
                || "DISPATCH_INPUT_EXPIRED".equals(exception.getCode());
    }

    private boolean hasReceivedAllTargetTelemetry(
            List<String> targetStationIds,
            OffsetDateTime startedAt,
            OffsetDateTime receivedAt
    ) {
        Set<String> targetSet = Set.copyOf(targetStationIds);
        Set<String> receivedStations = telemetryHistoryRepository
                .findByReceivedTimestampBetweenOrderByReceivedTimestampAsc(
                        startedAt,
                        receivedAt,
                        PageRequest.of(0, TELEMETRY_WINDOW_LIMIT)
                ).stream()
                .map(TelemetryHistoryEntity::getStationId)
                .filter(targetSet::contains)
                .collect(Collectors.toSet());
        return receivedStations.containsAll(targetSet);
    }

    private boolean resolveConservativePlan(String scenarioCode, LinkState expectedLinkState) {
        if (expectedLinkState == LinkState.S3 || expectedLinkState == LinkState.S2) {
            return true;
        }
        try {
            return ExperimentScenarioDefinition.fromCode(scenarioCode).getExpectedLinkState() == LinkState.S3;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private ExperimentSummaryState readSummaryState(ExperimentRunEntity runEntity) {
        String summaryValue = runEntity.getResultSummary();
        if (summaryValue == null || summaryValue.isBlank()) {
            return new ExperimentSummaryState(List.of(), null, "", ORCHESTRATION_STATUS_PENDING, null, null, null, null, null, null, null, null, null, null);
        }
        String jsonPart = summaryValue.startsWith("{") ? summaryValue.split("；", 2)[0] : "{}";
        try {
            Map<String, Object> payload = objectMapper.readValue(jsonPart, SUMMARY_TYPE);
            return new ExperimentSummaryState(
                    readStringList(payload.get("targetStationIds")),
                    readInteger(payload.get("durationSeconds")),
                    payload.get("remark") instanceof String remark ? remark : "",
                    payload.get("orchestrationStatus") instanceof String orchestrationStatus ? orchestrationStatus : ORCHESTRATION_STATUS_PENDING,
                    readOffsetDateTime(payload.get("orchestrationTriggeredAt")),
                    readOffsetDateTime(payload.get("orchestrationCompletedAt")),
                    payload.get("dispatchContextSnapshotId") instanceof String dispatchContextSnapshotId ? dispatchContextSnapshotId : null,
                    payload.get("dispatchPlanId") instanceof String dispatchPlanId ? dispatchPlanId : null,
                    payload.get("agentRunId") instanceof String agentRunId ? agentRunId : null,
                    payload.get("planStatus") instanceof String planStatus ? planStatus : null,
                    payload.get("executionBatchId") instanceof String executionBatchId ? executionBatchId : null,
                    payload.get("safetyGatePassed") instanceof Boolean safetyGatePassed ? safetyGatePassed : null,
                    payload.get("failureReason") instanceof String failureReason ? failureReason : null,
                    readLinkState(payload.get("expectedLinkState"))
            );
        } catch (JsonProcessingException exception) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "EXPERIMENT_SUMMARY_DESERIALIZATION_FAILED",
                    "实验运行摘要反序列化失败",
                    Map.of("runId", runEntity.getRunId())
            );
        }
    }

    private void updateSummaryState(ExperimentRunEntity runEntity, ExperimentSummaryState summaryState) {
        try {
            LinkedHashMap<String, Object> payload = readSummaryPayload(runEntity);
            payload.put("targetStationIds", summaryState.targetStationIds());
            payload.put("durationSeconds", summaryState.durationSeconds());
            payload.put("remark", summaryState.remark());
            payload.put("orchestrationStatus", summaryState.orchestrationStatus());
            payload.put("orchestrationTriggeredAt", summaryState.orchestrationTriggeredAt());
            payload.put("orchestrationCompletedAt", summaryState.orchestrationCompletedAt());
            payload.put("dispatchContextSnapshotId", summaryState.dispatchContextSnapshotId());
            payload.put("dispatchPlanId", summaryState.dispatchPlanId());
            payload.put("agentRunId", summaryState.agentRunId());
            payload.put("planStatus", summaryState.planStatus());
            payload.put("executionBatchId", summaryState.executionBatchId());
            payload.put("safetyGatePassed", summaryState.safetyGatePassed());
            payload.put("failureReason", summaryState.failureReason());
            payload.put("expectedLinkState", summaryState.expectedLinkState() == null ? null : summaryState.expectedLinkState().name());
            runEntity.setResultSummary(objectMapper.writeValueAsString(payload));
            experimentRunRepository.save(runEntity);
        } catch (JsonProcessingException exception) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "EXPERIMENT_SUMMARY_SERIALIZATION_FAILED",
                    "实验运行摘要序列化失败",
                    Map.of("runId", runEntity.getRunId())
            );
        }
    }

    private LinkedHashMap<String, Object> readSummaryPayload(ExperimentRunEntity runEntity) {
        String summaryValue = runEntity.getResultSummary();
        if (summaryValue == null || summaryValue.isBlank() || !summaryValue.startsWith("{")) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(summaryValue.split("；", 2)[0], SUMMARY_TYPE);
        } catch (JsonProcessingException exception) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "EXPERIMENT_SUMMARY_DESERIALIZATION_FAILED",
                    "实验运行摘要反序列化失败",
                    Map.of("runId", runEntity.getRunId())
            );
        }
    }

    private List<String> readStringList(Object value) {
        if (value instanceof List<?> values) {
            return values.stream().filter(String.class::isInstance).map(String.class::cast).toList();
        }
        return List.of();
    }

    private Integer readInteger(Object value) {
        if (value instanceof Integer integerValue) {
            return integerValue;
        }
        if (value instanceof Number numberValue) {
            return numberValue.intValue();
        }
        return null;
    }

    private OffsetDateTime readOffsetDateTime(Object value) {
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime;
        }
        if (value instanceof String stringValue && !stringValue.isBlank()) {
            return OffsetDateTime.parse(stringValue);
        }
        return null;
    }

    private LinkState readLinkState(Object value) {
        if (value instanceof String stringValue && !stringValue.isBlank()) {
            return LinkState.valueOf(stringValue);
        }
        return null;
    }

    /**
     * 实验运行摘要结构。
     *
     * @param targetStationIds 目标场站
     * @param durationSeconds 持续时间
     * @param remark 备注
     * @param orchestrationStatus 闭环重调度状态
     * @param orchestrationTriggeredAt 闭环触发时间
     * @param orchestrationCompletedAt 闭环完成时间
     * @param dispatchContextSnapshotId 调度上下文快照标识
     * @param dispatchPlanId 调度计划标识
     * @param agentRunId 多智能体运行标识
     * @param planStatus 计划状态
     * @param executionBatchId 指令批次标识
     * @param safetyGatePassed 安全门是否通过
     * @param failureReason 失败原因
     * @param expectedLinkState 预期链路状态
     */
    private record ExperimentSummaryState(
            List<String> targetStationIds,
            Integer durationSeconds,
            String remark,
            String orchestrationStatus,
            OffsetDateTime orchestrationTriggeredAt,
            OffsetDateTime orchestrationCompletedAt,
            String dispatchContextSnapshotId,
            String dispatchPlanId,
            String agentRunId,
            String planStatus,
            String executionBatchId,
            Boolean safetyGatePassed,
            String failureReason,
            LinkState expectedLinkState
    ) {

        /**
         * 返回替换闭环状态后的摘要。
         *
         * @param nextStatus 新状态
         * @return 新摘要
         */
        private ExperimentSummaryState withOrchestrationStatus(String nextStatus) {
            return new ExperimentSummaryState(
                    targetStationIds,
                    durationSeconds,
                    remark,
                    nextStatus,
                    orchestrationTriggeredAt,
                    orchestrationCompletedAt,
                    dispatchContextSnapshotId,
                    dispatchPlanId,
                    agentRunId,
                    planStatus,
                    executionBatchId,
                    safetyGatePassed,
                    failureReason,
                    expectedLinkState
            );
        }

        /**
         * 返回替换闭环触发时间后的摘要。
         *
         * @param triggeredAt 闭环触发时间
         * @return 新摘要
         */
        private ExperimentSummaryState withTriggeredAt(OffsetDateTime triggeredAt) {
            return new ExperimentSummaryState(
                    targetStationIds,
                    durationSeconds,
                    remark,
                    orchestrationStatus,
                    triggeredAt,
                    orchestrationCompletedAt,
                    dispatchContextSnapshotId,
                    dispatchPlanId,
                    agentRunId,
                    planStatus,
                    executionBatchId,
                    safetyGatePassed,
                    failureReason,
                    expectedLinkState
            );
        }

        /**
         * 返回替换闭环完成时间后的摘要。
         *
         * @param completedAt 闭环完成时间
         * @return 新摘要
         */
        private ExperimentSummaryState withCompletedAt(OffsetDateTime completedAt) {
            return new ExperimentSummaryState(
                    targetStationIds,
                    durationSeconds,
                    remark,
                    orchestrationStatus,
                    orchestrationTriggeredAt,
                    completedAt,
                    dispatchContextSnapshotId,
                    dispatchPlanId,
                    agentRunId,
                    planStatus,
                    executionBatchId,
                    safetyGatePassed,
                    failureReason,
                    expectedLinkState
            );
        }

        /**
         * 返回替换调度上下文快照标识后的摘要。
         *
         * @param snapshotId 调度上下文快照标识
         * @return 新摘要
         */
        private ExperimentSummaryState withDispatchContextSnapshotId(String snapshotId) {
            return new ExperimentSummaryState(
                    targetStationIds,
                    durationSeconds,
                    remark,
                    orchestrationStatus,
                    orchestrationTriggeredAt,
                    orchestrationCompletedAt,
                    snapshotId,
                    dispatchPlanId,
                    agentRunId,
                    planStatus,
                    executionBatchId,
                    safetyGatePassed,
                    failureReason,
                    expectedLinkState
            );
        }

        /**
         * 返回替换调度计划标识后的摘要。
         *
         * @param planId 调度计划标识
         * @return 新摘要
         */
        private ExperimentSummaryState withDispatchPlanId(String planId) {
            return new ExperimentSummaryState(
                    targetStationIds,
                    durationSeconds,
                    remark,
                    orchestrationStatus,
                    orchestrationTriggeredAt,
                    orchestrationCompletedAt,
                    dispatchContextSnapshotId,
                    planId,
                    agentRunId,
                    planStatus,
                    executionBatchId,
                    safetyGatePassed,
                    failureReason,
                    expectedLinkState
            );
        }

        /**
         * 返回替换智能体运行标识后的摘要。
         *
         * @param runId 智能体运行标识
         * @return 新摘要
         */
        private ExperimentSummaryState withAgentRunId(String runId) {
            return new ExperimentSummaryState(
                    targetStationIds,
                    durationSeconds,
                    remark,
                    orchestrationStatus,
                    orchestrationTriggeredAt,
                    orchestrationCompletedAt,
                    dispatchContextSnapshotId,
                    dispatchPlanId,
                    runId,
                    planStatus,
                    executionBatchId,
                    safetyGatePassed,
                    failureReason,
                    expectedLinkState
            );
        }

        /**
         * 返回替换计划状态后的摘要。
         *
         * @param nextPlanStatus 计划状态
         * @return 新摘要
         */
        private ExperimentSummaryState withPlanStatus(String nextPlanStatus) {
            return new ExperimentSummaryState(
                    targetStationIds,
                    durationSeconds,
                    remark,
                    orchestrationStatus,
                    orchestrationTriggeredAt,
                    orchestrationCompletedAt,
                    dispatchContextSnapshotId,
                    dispatchPlanId,
                    agentRunId,
                    nextPlanStatus,
                    executionBatchId,
                    safetyGatePassed,
                    failureReason,
                    expectedLinkState
            );
        }

        /**
         * 返回替换执行批次标识后的摘要。
         *
         * @param batchId 执行批次标识
         * @return 新摘要
         */
        private ExperimentSummaryState withExecutionBatchId(String batchId) {
            return new ExperimentSummaryState(
                    targetStationIds,
                    durationSeconds,
                    remark,
                    orchestrationStatus,
                    orchestrationTriggeredAt,
                    orchestrationCompletedAt,
                    dispatchContextSnapshotId,
                    dispatchPlanId,
                    agentRunId,
                    planStatus,
                    batchId,
                    safetyGatePassed,
                    failureReason,
                    expectedLinkState
            );
        }

        /**
         * 返回替换安全门结果后的摘要。
         *
         * @param passed 安全门结果
         * @return 新摘要
         */
        private ExperimentSummaryState withSafetyGatePassed(Boolean passed) {
            return new ExperimentSummaryState(
                    targetStationIds,
                    durationSeconds,
                    remark,
                    orchestrationStatus,
                    orchestrationTriggeredAt,
                    orchestrationCompletedAt,
                    dispatchContextSnapshotId,
                    dispatchPlanId,
                    agentRunId,
                    planStatus,
                    executionBatchId,
                    passed,
                    failureReason,
                    expectedLinkState
            );
        }

        /**
         * 返回替换失败原因后的摘要。
         *
         * @param reason 失败原因
         * @return 新摘要
         */
        private ExperimentSummaryState withFailureReason(String reason) {
            return new ExperimentSummaryState(
                    targetStationIds,
                    durationSeconds,
                    remark,
                    orchestrationStatus,
                    orchestrationTriggeredAt,
                    orchestrationCompletedAt,
                    dispatchContextSnapshotId,
                    dispatchPlanId,
                    agentRunId,
                    planStatus,
                    executionBatchId,
                    safetyGatePassed,
                    reason,
                    expectedLinkState
            );
        }
    }

    /**
     * 闭环执行结果。
     *
     * @param dispatchContextSnapshotId 调度上下文快照标识
     * @param dispatchPlanId 调度计划标识
     * @param agentRunId 智能体运行标识
     * @param planStatus 计划状态
     * @param executionBatchId 执行批次标识
     * @param safetyGatePassed 安全门结果
     * @param triggeredAt 触发时间
     * @param completedAt 完成时间
     */
    private record ClosedLoopExecutionResult(
            String dispatchContextSnapshotId,
            String dispatchPlanId,
            String agentRunId,
            String planStatus,
            String executionBatchId,
            boolean safetyGatePassed,
            OffsetDateTime triggeredAt,
            OffsetDateTime completedAt
    ) {
    }

    /**
     * 调度计划过期异常中的原始证据。
     *
     * @param planId 过期计划标识
     * @param validUntil 计划有效截止时间
     * @param detectedAt 服务检测到过期的时间
     */
    private record ExpiredPlanEvidence(String planId, OffsetDateTime validUntil, OffsetDateTime detectedAt) {
    }
}

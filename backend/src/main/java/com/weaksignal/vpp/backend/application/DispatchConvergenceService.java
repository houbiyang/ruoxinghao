package com.weaksignal.vpp.backend.application;

import com.weaksignal.vpp.backend.api.ApiException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weaksignal.vpp.backend.api.dto.DispatchContextSnapshotResponse;
import com.weaksignal.vpp.backend.config.MpcConvergenceProperties;
import com.weaksignal.vpp.backend.api.dto.DispatchPlanCreateRequest;
import com.weaksignal.vpp.backend.api.dto.DispatchPlanExecutionResponse;
import com.weaksignal.vpp.backend.api.dto.DispatchPlanResponse;
import com.weaksignal.vpp.backend.api.dto.MarketAgentAnalyzeRequest;
import com.weaksignal.vpp.backend.api.dto.MarketAgentRunResponse;
import com.weaksignal.vpp.backend.domain.DispatchCommandStatus;
import com.weaksignal.vpp.backend.domain.DispatchConvergenceStatus;
import com.weaksignal.vpp.backend.domain.ResourceType;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.ControlCommandEntity;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.DispatchConvergenceRoundEntity;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.ExperimentRunEntity;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.TelemetrySnapshotEntity;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.ControlCommandRepository;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.DispatchConvergenceRoundRepository;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.ExperimentRunRepository;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.StationRepository;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.TelemetrySnapshotRepository;
import com.weaksignal.vpp.backend.marketdispatch.OptimizationResult;
import com.weaksignal.vpp.backend.marketdispatch.OptimizationStatus;
import com.weaksignal.vpp.backend.planning.DispatchContextSnapshotService;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 执行后遥测收敛判定及 MPC 滚动重调度服务。 */
@Service
public class DispatchConvergenceService {
    private static final Duration MAX_SNAPSHOT_SKEW = Duration.ofSeconds(30);
    private final DispatchConvergenceRoundRepository roundRepository;
    private final ExperimentRunRepository experimentRunRepository;
    private final ControlCommandRepository commandRepository;
    private final TelemetrySnapshotRepository telemetrySnapshotRepository;
    private final StationRepository stationRepository;
    private final DispatchContextSnapshotService snapshotService;
    private final MarketDispatchPlanningService planningService;
    private final MarketAgentOrchestrationService agentService;
    private final SolverObservabilityService solverObservabilityService;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final MpcConvergenceProperties properties;
    private DistributedExperimentControlService distributedExperimentControlService;

    /** 注入执行收敛依赖。 */
    public DispatchConvergenceService(DispatchConvergenceRoundRepository roundRepository, ExperimentRunRepository experimentRunRepository, ControlCommandRepository commandRepository,
            TelemetrySnapshotRepository telemetrySnapshotRepository, StationRepository stationRepository, DispatchContextSnapshotService snapshotService,
            MarketDispatchPlanningService planningService, MarketAgentOrchestrationService agentService,
            SolverObservabilityService solverObservabilityService, ObjectMapper objectMapper, Clock clock, MpcConvergenceProperties properties) {
        this.roundRepository = roundRepository; this.experimentRunRepository = experimentRunRepository; this.commandRepository = commandRepository;
        this.telemetrySnapshotRepository = telemetrySnapshotRepository; this.stationRepository = stationRepository; this.snapshotService = snapshotService;
        this.planningService = planningService; this.agentService = agentService; this.solverObservabilityService = solverObservabilityService; this.objectMapper = objectMapper; this.clock = clock; this.properties = properties;
    }

    /**
     * 注入可选的分布式实验控制器，避免收敛服务与控制器形成构造器循环依赖。
     *
     * @param distributedExperimentControlService 分布式实验控制器
     */
    @Autowired(required = false)
    public void setDistributedExperimentControlService(
            DistributedExperimentControlService distributedExperimentControlService
    ) {
        this.distributedExperimentControlService = distributedExperimentControlService;
    }

    /**
     * 从实验摘要恢复当前执行轮次，并检查新到达的遥测。该查询使服务重启后无需依赖 JVM 内存状态。
     *
     * @param experimentRunId 实验运行标识
     */
    @Transactional(noRollbackFor = ApiException.class)
    public void observeTelemetry(String experimentRunId) {
        ExperimentRunEntity run = experimentRunRepository.findByRunId(experimentRunId).orElse(null);
        if (run == null || run.getResultSummary() == null) return;
        try {
            Map<String, Object> summary = objectMapper.readValue(run.getResultSummary().split("；", 2)[0], new TypeReference<LinkedHashMap<String, Object>>() { });
            observeTelemetry(experimentRunId, string(summary, "dispatchPlanId"), string(summary, "dispatchContextSnapshotId"), string(summary, "agentRunId"), string(summary, "executionBatchId"));
        } catch (Exception ignored) {
            // 非结构化历史摘要不参与收敛判定。
        }
    }

    /**
     * 实验恢复后遥测不再携带实验标识时，按最近非终态轮次继续推进收敛。
     * 全系统供需判定需要等待所有启用场站更新，因此任一启用场站均可触发检查。
     *
     * @param stationId 当前遥测场站标识
     */
    @Transactional(noRollbackFor = ApiException.class)
    public void observeTelemetryForActiveRound(String stationId) {
        if (blank(stationId)) return;
        List<String> activeStatuses = List.of(
                DispatchConvergenceStatus.WAITING_ACK.name(),
                DispatchConvergenceStatus.WAITING_TELEMETRY.name(),
                DispatchConvergenceStatus.TRACKING.name()
        );
        DispatchConvergenceRoundEntity round = roundRepository.findFirstByStatusInOrderByStartedAtDesc(activeStatuses).orElse(null);
        if (round == null || blank(round.getReplacementPlanId()) || blank(round.getExecutionBatchId())) return;
        boolean enabledStation = stationRepository.findByStationId(stationId)
                .map(station -> station.isEnabled())
                .orElse(false);
        if (!enabledStation) return;
        observeTelemetry(round.getExperimentRunId(), round.getReplacementPlanId(), round.getSnapshotId(),
                round.getAgentRunId(), round.getExecutionBatchId());
    }

    /**
     * 以已经持久化的遥测触发收敛检查。行锁和数据库唯一约束共同保证服务重试、并发遥测不会生成重复轮次。
     *
     * @param experimentRunId 实验运行标识
     * @param sourcePlanId 当前执行计划标识
     * @param snapshotId 当前调度上下文快照标识
     * @param agentRunId 当前智能体运行标识
     * @param executionBatchId 当前执行批次标识
     */
    @Transactional(noRollbackFor = ApiException.class)
    public void observeTelemetry(String experimentRunId, String sourcePlanId, String snapshotId, String agentRunId, String executionBatchId) {
        if (blank(experimentRunId) || blank(sourcePlanId) || blank(executionBatchId)) return;
        DispatchConvergenceRoundEntity round = roundRepository.findFirstByExperimentRunIdOrderByRoundNumberDesc(experimentRunId)
                .orElseGet(() -> createInitialRound(experimentRunId, sourcePlanId, snapshotId, agentRunId, executionBatchId));
        if (terminal(round.getStatus())) return;
        OffsetDateTime now = OffsetDateTime.now(clock);
        if (round.getStartedAt().plus(properties.overallClosedLoopTimeout()).isBefore(now)) { finish(round, DispatchConvergenceStatus.CONVERGENCE_TIMEOUT, now, "TOTAL_TIMEOUT"); return; }
        List<ControlCommandEntity> commands = commandRepository.findByDispatchPlanIdOrderByCreatedAtAsc(round.getReplacementPlanId());
        if (commands.isEmpty()) return;
        if (commands.stream().anyMatch(command -> DispatchCommandStatus.FAILED.name().equals(command.getStatus()) || DispatchCommandStatus.REJECTED.name().equals(command.getStatus()) || DispatchCommandStatus.TIMEOUT.name().equals(command.getStatus()))) {
            finish(round, DispatchConvergenceStatus.FAILED, now, "COMMAND_FAILED"); return;
        }
        if (commands.stream().anyMatch(command -> !DispatchCommandStatus.EXECUTED.name().equals(command.getStatus()))) {
            transition(round, DispatchConvergenceStatus.WAITING_ACK, now, Map.of("acknowledgedCommandCount", commands.stream().filter(command -> DispatchCommandStatus.EXECUTED.name().equals(command.getStatus())).count())); return;
        }
        OffsetDateTime latestCommandTime = commands.stream()
                .map(ControlCommandEntity::getSendTime)
                .max(OffsetDateTime::compareTo)
                .orElse(now);
        OffsetDateTime stabilizationEndsAt = latestCommandTime.plus(properties.executionStabilizationWait());
        OffsetDateTime reoptimizationEligibleAt = stabilizationEndsAt.plus(properties.minimumReoptimizationInterval());
        if (now.isBefore(stabilizationEndsAt)) {
            transition(round, DispatchConvergenceStatus.WAITING_TELEMETRY, now, Map.of(
                    "stabilizing", true,
                    "latestCommandTime", latestCommandTime,
                    "stabilizationEndsAt", stabilizationEndsAt
            ));
            return;
        }
        Map<String, Double> targets = targets(commands);
        List<String> enabledStationIds = stationRepository.findAll().stream()
                .filter(station -> station.isEnabled())
                .map(station -> station.getStationId())
                .sorted()
                .toList();
        Map<String, TelemetrySnapshotEntity> snapshots = completeFreshSystemSnapshot(enabledStationIds, commands, now);
        if (snapshots == null) { transition(round, DispatchConvergenceStatus.WAITING_TELEMETRY, now, Map.of("snapshotComplete", false)); return; }
        Map<String, ResourceType> resourceTypes = resourceTypes(new java.util.LinkedHashSet<>(enabledStationIds));
        Map<String, Double> errors = new LinkedHashMap<>(); double totalActual = 0; double totalTarget = 0;
        for (Map.Entry<String, Double> target : targets.entrySet()) {
            double actual = snapshots.get(target.getKey()).getActivePower();
            ResourceType resourceType = resourceTypes.get(target.getKey());
            if (resourceType != ResourceType.GRID) {
                errors.put(target.getKey(), actual - target.getValue());
            }
            totalTarget += signedPower(resourceType, target.getValue());
        }
        for (Map.Entry<String, TelemetrySnapshotEntity> snapshot : snapshots.entrySet()) { totalActual += signedPower(resourceTypes.get(snapshot.getKey()), snapshot.getValue().getActivePower()); }
        double balanceError = totalActual;
        boolean succeeded = errors.values().stream().allMatch(error -> Math.abs(error) <= properties.defaultResourceTrackingToleranceKw()) && Math.abs(balanceError) <= properties.actualBalanceToleranceKw();
        round.setConsecutiveSuccessCount(succeeded ? round.getConsecutiveSuccessCount() + 1 : 0);
        Map<String, Object> evidence = new LinkedHashMap<>(); evidence.put("targetPowers", targets); evidence.put("actualPowers", actuals(snapshots)); evidence.put("resourceErrors", errors);
        evidence.put("actualNetPower", totalActual); evidence.put("plannedNetPower", totalTarget); evidence.put("supplyDemandError", balanceError); evidence.put("reoptimizationEligibleAt", reoptimizationEligibleAt); evidence.put("snapshotStart", snapshots.values().stream().map(TelemetrySnapshotEntity::getReceivedTimestamp).min(OffsetDateTime::compareTo).orElse(now)); evidence.put("snapshotEnd", snapshots.values().stream().map(TelemetrySnapshotEntity::getReceivedTimestamp).max(OffsetDateTime::compareTo).orElse(now));
        if (round.getConsecutiveSuccessCount() >= properties.requiredSatisfiedSamples()) { finish(round, DispatchConvergenceStatus.TARGET_REACHED, now, evidence); return; }
        transition(round, DispatchConvergenceStatus.TRACKING, now, evidence);
        if (!succeeded && !now.isBefore(reoptimizationEligibleAt)) reoptimize(round, now);
    }

    private DispatchConvergenceRoundEntity createInitialRound(String runId, String planId, String snapshotId, String agentRunId, String batchId) {
        DispatchConvergenceRoundEntity round = new DispatchConvergenceRoundEntity(); OffsetDateTime now = OffsetDateTime.now(clock);
        round.setRoundId(UUID.randomUUID().toString()); round.setExperimentRunId(runId); round.setRoundNumber(1); round.setSourcePlanId(planId); round.setSnapshotId(snapshotId); round.setAgentRunId(agentRunId); round.setReplacementPlanId(planId); round.setExecutionBatchId(batchId); round.setStatus(DispatchConvergenceStatus.WAITING_ACK.name()); round.setTriggerReason("INITIAL_EXECUTION"); round.setConsecutiveSuccessCount(0); round.setStartedAt(now); round.setAuditJson(write(Map.of("events", List.of(event(now, "INITIAL_EXECUTION", Map.of())))));
        try { return roundRepository.saveAndFlush(round); } catch (DataIntegrityViolationException exception) { return roundRepository.findFirstByExperimentRunIdOrderByRoundNumberDesc(runId).orElseThrow(() -> exception); }
    }

    private void reoptimize(DispatchConvergenceRoundEntity previous, OffsetDateTime now) {
        if (previous.getRoundNumber() >= properties.maximumRollingRounds()) { finish(previous, DispatchConvergenceStatus.CONVERGENCE_TIMEOUT, now, "MAX_ROUNDS_REACHED"); return; }
        finish(previous, DispatchConvergenceStatus.REOPTIMIZATION_REQUIRED, now, "TELEMETRY_NOT_CONVERGED");
        DispatchContextSnapshotResponse snapshot = snapshotService.createExperimentSnapshot();
        DispatchPlanResponse plan = createRollingPlan(previous, snapshot);
        solverObservabilityService.bindBusinessContext(snapshot.snapshotId(), plan.planId(), previous.getExperimentRunId(), previous.getRoundNumber() + 1);
        MarketAgentRunResponse agentRun = agentService.analyze(new MarketAgentAnalyzeRequest(snapshot.snapshotId(), plan.planId(), false));
        DispatchConvergenceRoundEntity next = new DispatchConvergenceRoundEntity(); next.setRoundId(UUID.randomUUID().toString()); next.setExperimentRunId(previous.getExperimentRunId()); next.setRoundNumber(previous.getRoundNumber() + 1); next.setSourcePlanId(previous.getReplacementPlanId()); next.setSnapshotId(snapshot.snapshotId()); next.setAgentRunId(agentRun.runId()); next.setReplacementPlanId(plan.planId()); next.setTriggerReason("REOPTIMIZATION_REQUIRED"); next.setConsecutiveSuccessCount(0);
        if (!agentRun.safetyDecision().passed()) { OffsetDateTime failedAt = OffsetDateTime.now(clock); next.setStartedAt(failedAt); next.setStatus(DispatchConvergenceStatus.FAILED.name()); next.setEndedAt(failedAt); next.setAuditJson(write(Map.of("events", List.of(event(failedAt, "SAFETY_GATE_REJECTED", Map.of("safetyGatePassed", false)))))); roundRepository.save(next); return; }
        planningService.validate(plan.planId()); planningService.approve(plan.planId()); planningService.activate(plan.planId()); DispatchPlanExecutionResponse execution = planningService.executeNext(plan.planId());
        OffsetDateTime executionStartedAt = OffsetDateTime.now(clock); next.setStartedAt(executionStartedAt); next.setExecutionBatchId(execution.activeBatchId()); next.setStatus(DispatchConvergenceStatus.WAITING_ACK.name()); next.setAuditJson(write(Map.of("events", List.of(event(executionStartedAt, "REOPTIMIZATION_EXECUTED", Map.of("safetyGatePassed", true, "executionBatchId", execution.activeBatchId()))))));
        roundRepository.save(next);
    }

    /**
     * 使用实验原始控制策略创建滚动计划，禁止分布式实验静默切换到集中式优化器。
     *
     * @param previous 上一收敛轮次
     * @param snapshot 最新调度上下文快照
     * @return 新一轮草稿计划
     */
    private DispatchPlanResponse createRollingPlan(
            DispatchConvergenceRoundEntity previous,
            DispatchContextSnapshotResponse snapshot
    ) {
        ExperimentRunEntity run = experimentRunRepository.findByRunId(previous.getExperimentRunId())
                .orElseThrow(() -> new IllegalStateException("滚动重调度缺少实验运行记录"));
        Map<String, Object> summary = readExperimentSummary(run.getResultSummary());
        String controlStrategy = summary.get("controlStrategy") instanceof String value && !value.isBlank()
                ? value : "CENTRALIZED";
        DispatchPlanCreateRequest request = new DispatchPlanCreateRequest(
                snapshot.snapshotId(), false, run.getScenarioCode());
        if ("CENTRALIZED".equals(controlStrategy)) {
            return planningService.create(request);
        }
        if (!"FIXED_WEIGHT".equals(controlStrategy) && !"ADAPTIVE".equals(controlStrategy)) {
            throw new IllegalStateException("滚动重调度控制策略不受支持: " + controlStrategy);
        }
        if (distributedExperimentControlService == null) {
            throw new IllegalStateException("滚动重调度缺少分布式控制器");
        }
        OptimizationResult optimizationResult = distributedExperimentControlService.optimize(run, snapshot);
        if (optimizationResult.status() != OptimizationStatus.FEASIBLE) {
            throw new ApiException(org.springframework.http.HttpStatus.CONFLICT, "CONVERGENCE_TIMEOUT",
                    "分布式滚动重调度未达到严格收敛条件", Map.of(
                    "controlStrategy", controlStrategy,
                    "optimizationRound", optimizationResult.optimizationRound(),
                    "solveTimeMs", optimizationResult.solveTimeMs()
            ));
        }
        return planningService.createFromOptimizationResult(request, optimizationResult);
    }

    /**
     * 读取实验运行的结构化摘要。
     *
     * @param resultSummary 实验结果摘要 JSON
     * @return 摘要字段映射
     */
    private Map<String, Object> readExperimentSummary(String resultSummary) {
        if (resultSummary == null || resultSummary.isBlank()) return new LinkedHashMap<>();
        try {
            return objectMapper.readValue(
                    resultSummary.split("；", 2)[0],
                    new TypeReference<LinkedHashMap<String, Object>>() { }
            );
        } catch (Exception exception) {
            throw new IllegalStateException("滚动重调度无法读取实验摘要", exception);
        }
    }

    private Map<String, TelemetrySnapshotEntity> completeFreshSystemSnapshot(
            List<String> stationIds,
            List<ControlCommandEntity> commands,
            OffsetDateTime now
    ) {
        if (stationIds.isEmpty()) return null;
        OffsetDateTime latestCommandTime = commands.stream()
                .map(ControlCommandEntity::getSendTime)
                .max(OffsetDateTime::compareTo)
                .orElse(now);
        Map<String, TelemetrySnapshotEntity> result = new LinkedHashMap<>();
        for (TelemetrySnapshotEntity snapshot : telemetrySnapshotRepository.findByStationIdIn(stationIds)) {
            if (stationIds.contains(snapshot.getStationId())
                    && snapshot.getReceivedTimestamp().isAfter(latestCommandTime)
                    && !snapshot.getReceivedTimestamp().isBefore(now.minus(properties.telemetryMaximumAge()))) {
                result.put(snapshot.getStationId(), snapshot);
            }
        }
        if (result.size() != stationIds.size()) return null;
        OffsetDateTime earliest = result.values().stream().map(TelemetrySnapshotEntity::getReceivedTimestamp)
                .min(OffsetDateTime::compareTo).orElse(now);
        OffsetDateTime latest = result.values().stream().map(TelemetrySnapshotEntity::getReceivedTimestamp)
                .max(OffsetDateTime::compareTo).orElse(now);
        return Duration.between(earliest, latest).compareTo(MAX_SNAPSHOT_SKEW) <= 0 ? result : null;
    }
    private Map<String, Double> targets(List<ControlCommandEntity> commands) { Map<String, Double> result = new LinkedHashMap<>(); for (ControlCommandEntity command : commands) { try { result.put(command.getStationId(), objectMapper.readTree(command.getCommandPayload()).path("targetPower").asDouble()); } catch (Exception exception) { throw new IllegalStateException("控制命令目标功率无法解析", exception); } } return result; }
    private Map<String, Double> actuals(Map<String, TelemetrySnapshotEntity> snapshots) { Map<String, Double> result = new LinkedHashMap<>(); snapshots.forEach((stationId, snapshot) -> result.put(stationId, snapshot.getActivePower())); return result; }
    private Map<String, ResourceType> resourceTypes(java.util.Set<String> stationIds) { Map<String, ResourceType> result = new LinkedHashMap<>(); for (String stationId : stationIds) { stationRepository.findByStationId(stationId).ifPresent(station -> result.put(stationId, station.getResourceType())); } if (result.size() != stationIds.size()) throw new IllegalStateException("收敛判定缺少场站资源类型"); return result; }
    private double signedPower(ResourceType resourceType, double power) { return switch (resourceType) { case PHOTOVOLTAIC, WIND, THERMAL, GRID, STORAGE -> power; case LOAD, CHARGING -> -Math.abs(power); }; }
    private void transition(DispatchConvergenceRoundEntity round, DispatchConvergenceStatus status, OffsetDateTime now, Map<String, Object> evidence) { round.setStatus(status.name()); append(round, now, status.name(), evidence); roundRepository.save(round); }
    private void finish(DispatchConvergenceRoundEntity round, DispatchConvergenceStatus status, OffsetDateTime now, Object evidence) {
        round.setStatus(status.name());
        round.setEndedAt(now);
        Map<String, Object> finalEvidence = evidence instanceof Map<?, ?> values
                ? cast(values)
                : Map.of("reason", evidence);
        append(round, now, status.name(), finalEvidence);
        roundRepository.save(round);
        persistExperimentConvergenceStatus(round, status, now, finalEvidence);
    }

    /**
     * 将收敛终态同步到实验摘要，作为恢复完成判定的真实控制证据。
     *
     * @param round 收敛轮次
     * @param status 收敛终态
     * @param occurredAt 状态发生时间
     * @param evidence 收敛终态证据
     */
    private void persistExperimentConvergenceStatus(
            DispatchConvergenceRoundEntity round,
            DispatchConvergenceStatus status,
            OffsetDateTime occurredAt,
            Map<String, Object> evidence
    ) {
        ExperimentRunEntity run = experimentRunRepository.findByRunId(round.getExperimentRunId()).orElse(null);
        if (run == null || run.getResultSummary() == null) {
            return;
        }
        try {
            String[] summaryParts = run.getResultSummary().split("；", 2);
            Map<String, Object> summary = objectMapper.readValue(
                    summaryParts[0],
                    new TypeReference<LinkedHashMap<String, Object>>() { }
            );
            summary.put("convergenceStatus", status.name());
            summary.put("convergenceRoundNumber", round.getRoundNumber());
            summary.put("convergenceUpdatedAt", occurredAt);
            if (evidence.get("supplyDemandError") instanceof Number balanceError) {
                Map<String, Object> telemetryValidation = objectMap(summary.get("postDispatchTelemetryValidation"));
                telemetryValidation.put("actualBalanceErrorKw", balanceError.doubleValue());
                telemetryValidation.put("measuredAt", occurredAt);
                summary.put("postDispatchTelemetryValidation", telemetryValidation);
            }
            String structuredSummary = objectMapper.writeValueAsString(summary);
            run.setResultSummary(summaryParts.length == 1 ? structuredSummary : structuredSummary + "；" + summaryParts[1]);
            experimentRunRepository.save(run);
        } catch (Exception exception) {
            throw new IllegalStateException("实验收敛状态写入失败", exception);
        }
    }
    @SuppressWarnings("unchecked") private Map<String, Object> cast(Map<?, ?> values) { return (Map<String, Object>) values; }
    @SuppressWarnings("unchecked") private Map<String, Object> objectMap(Object value) { return value instanceof Map<?, ?> map ? new LinkedHashMap<>((Map<String, Object>) map) : new LinkedHashMap<>(); }
    private void append(DispatchConvergenceRoundEntity round, OffsetDateTime now, String type, Map<String, Object> evidence) { Map<String, Object> audit = read(round.getAuditJson()); List<Map<String, Object>> events = new ArrayList<>((List<Map<String, Object>>) audit.getOrDefault("events", List.of())); events.add(event(now, type, evidence)); audit.put("events", events); audit.put("finalStatus", round.getStatus()); audit.put("consecutiveSuccessCount", round.getConsecutiveSuccessCount()); audit.put("sourcePlanId", round.getSourcePlanId()); audit.put("snapshotId", round.getSnapshotId()); audit.put("agentRunId", round.getAgentRunId()); audit.put("replacementPlanId", round.getReplacementPlanId()); audit.put("executionBatchId", round.getExecutionBatchId()); round.setAuditJson(write(audit)); }
    private Map<String, Object> event(OffsetDateTime now, String type, Map<String, Object> evidence) { Map<String, Object> result = new LinkedHashMap<>(); result.put("at", now); result.put("type", type); result.put("evidence", evidence); return result; }
    private Map<String, Object> read(String json) { try { return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() { }); } catch (Exception exception) { return new LinkedHashMap<>(); } }
    private String write(Object value) { try { return objectMapper.writeValueAsString(value); } catch (Exception exception) { throw new IllegalStateException("收敛审计序列化失败", exception); } }
    private boolean terminal(String status) { return DispatchConvergenceStatus.TARGET_REACHED.name().equals(status) || DispatchConvergenceStatus.CONVERGENCE_TIMEOUT.name().equals(status) || DispatchConvergenceStatus.FAILED.name().equals(status) || DispatchConvergenceStatus.REOPTIMIZATION_REQUIRED.name().equals(status); }
    private boolean blank(String value) { return value == null || value.isBlank(); }
    private String string(Map<String, Object> values, String key) { return values.get(key) instanceof String value ? value : null; }
}

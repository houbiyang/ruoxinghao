package com.weaksignal.vpp.backend.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weaksignal.vpp.backend.api.dto.DispatchContextSnapshotResponse;
import com.weaksignal.vpp.backend.api.dto.ForecastPointResponse;
import com.weaksignal.vpp.backend.api.dto.ResourceCapabilityResponse;
import com.weaksignal.vpp.backend.control.distributed.AdaptiveConsensusPolicy;
import com.weaksignal.vpp.backend.control.distributed.CommunicationGraph;
import com.weaksignal.vpp.backend.control.distributed.ConsensusPolicy;
import com.weaksignal.vpp.backend.control.distributed.ConvergenceEvaluator;
import com.weaksignal.vpp.backend.control.distributed.DistributedAgentState;
import com.weaksignal.vpp.backend.control.distributed.DistributedDispatchAgent;
import com.weaksignal.vpp.backend.control.distributed.DistributedDispatchCoordinator;
import com.weaksignal.vpp.backend.control.distributed.DistributedDispatchProperties;
import com.weaksignal.vpp.backend.control.distributed.DistributedDispatchSafetyValidator;
import com.weaksignal.vpp.backend.control.distributed.DistributedMarketBalanceProjector;
import com.weaksignal.vpp.backend.control.distributed.FixedWeightConsensusPolicy;
import com.weaksignal.vpp.backend.control.distributed.IncrementalCostModel;
import com.weaksignal.vpp.backend.domain.LinkState;
import com.weaksignal.vpp.backend.domain.ResourceType;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.ExperimentRunEntity;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.StationEntity;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.TelemetrySnapshotEntity;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.ExperimentRunRepository;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.StationRepository;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.TelemetrySnapshotRepository;
import com.weaksignal.vpp.backend.marketdispatch.DispatchPlanItem;
import com.weaksignal.vpp.backend.marketdispatch.OptimizationResult;
import com.weaksignal.vpp.backend.marketdispatch.OptimizationStatus;
import com.weaksignal.vpp.backend.marketdispatch.PowerDirection;
import com.weaksignal.vpp.backend.marketdispatch.ResourceDispatchTarget;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 将分布式协同控制器接入故障实验的真实遥测和 MQTT 指令链路。
 */
@Service
public class DistributedExperimentControlService {

    private static final TypeReference<LinkedHashMap<String, Object>> SUMMARY_TYPE = new TypeReference<>() { };
    private static final double ITERATION_HOURS = 1.0 / 3600.0;
    private static final Duration MINIMUM_CONTROL_INTERVAL = Duration.ofSeconds(1);
    private static final Duration MAXIMUM_SNAPSHOT_SKEW = Duration.ofMillis(250);
    private static final Duration MAXIMUM_CONVERGENCE_DURATION = Duration.ofMinutes(2);
    private static final int MAXIMUM_CONVERGENCE_ITERATIONS = 80;

    private final ExperimentRunRepository experimentRunRepository;
    private final TelemetrySnapshotRepository telemetrySnapshotRepository;
    private final StationRepository stationRepository;
    private final DistributedDispatchProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final DistributedDispatchSafetyValidator safetyValidator = new DistributedDispatchSafetyValidator();
    private final DistributedMarketBalanceProjector marketBalanceProjector = new DistributedMarketBalanceProjector();
    private final Map<String, ControlSession> sessionsByRunId = new LinkedHashMap<>();

    /**
     * 注入分布式实验控制依赖。
     *
     * @param experimentRunRepository 实验运行仓储
     * @param telemetrySnapshotRepository 遥测快照仓储
     * @param stationRepository 场站仓储
     * @param properties 分布式协同配置
     * @param objectMapper JSON 组件
     * @param clock 系统时钟
     */
    public DistributedExperimentControlService(
            ExperimentRunRepository experimentRunRepository,
            TelemetrySnapshotRepository telemetrySnapshotRepository,
            StationRepository stationRepository,
            DistributedDispatchProperties properties,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.experimentRunRepository = experimentRunRepository;
        this.telemetrySnapshotRepository = telemetrySnapshotRepository;
        this.stationRepository = stationRepository;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * 判断链路状态是否应由分布式控制处理。
     *
     * @param linkState 当前链路状态
     * @return 是否属于分布式控制范围
     */
    public boolean supports(LinkState linkState) {
        return linkState == LinkState.S0 || linkState == LinkState.S1 || linkState == LinkState.S2
                || linkState == LinkState.S3 || linkState == LinkState.S4;
    }

    /**
     * 基于真实遥测快照执行固定权重或自适应分布式优化。
     *
     * <p>本方法只产生不可变优化结果，不直接下发控制命令。达到严格收敛条件后，调用方必须将
     * 结果持久化为计划，并继续经过多智能体安全门和统一执行链。</p>
     *
     * @param runEntity 实验运行
     * @param contextSnapshot 调度上下文快照
     * @return 可持久化的分布式优化结果；未收敛时返回不可执行状态
     */
    @Transactional
    public synchronized OptimizationResult optimize(
            ExperimentRunEntity runEntity,
            DispatchContextSnapshotResponse contextSnapshot
    ) {
        if (runEntity == null || contextSnapshot == null || contextSnapshot.snapshotId() == null) {
            throw new IllegalArgumentException("分布式优化输入不能为空");
        }
        List<TelemetrySnapshotEntity> snapshots = telemetrySnapshotRepository.findAll();
        Map<String, StationEntity> stationsById = loadStationsById();
        ControlSession session = createSession(
                runEntity,
                snapshots,
                stationsById,
                contextSnapshot.resourceCapabilities()
        );
        double photovoltaicPower = powerOf(snapshots, stationsById, ResourceType.PHOTOVOLTAIC);
        double windPower = powerOf(snapshots, stationsById, ResourceType.WIND);
        double loadPower = loadPowerOf(snapshots, stationsById);
        long latencyMillis = Math.round(snapshots.stream().mapToInt(TelemetrySnapshotEntity::getLatency).average().orElse(0.0));
        double packetLossRate = snapshots.stream().mapToDouble(TelemetrySnapshotEntity::getPacketLossRate).average().orElse(0.0);
        long startedAtNanos = System.nanoTime();
        List<Map<String, Object>> iterationHistory = new ArrayList<>();
        DistributedDispatchCoordinator.DispatchIterationResult latestIteration = null;
        DistributedMarketBalanceProjector.BalanceProjection latestProjection = null;
        List<SafeControlTarget> latestSafeTargets = List.of();
        int executableTargetConsecutiveIterations = 0;

        for (int iteration = 1; iteration <= MAXIMUM_CONVERGENCE_ITERATIONS; iteration++) {
            if (Duration.ofNanos(System.nanoTime() - startedAtNanos).compareTo(MAXIMUM_CONVERGENCE_DURATION) >= 0) {
                break;
            }
            latestIteration = session.coordinator().iterate(
                    runEntity.getRunId(),
                    Map.of(),
                    ITERATION_HOURS,
                    OffsetDateTime.now(clock),
                    latencyMillis,
                    packetLossRate,
                    photovoltaicPower,
                    windPower,
                    loadPower,
                    session.random()
            );
            latestSafeTargets = validateTargets(latestIteration, snapshots, stationsById, contextSnapshot);
            latestProjection = projectMarketBalance(
                    latestSafeTargets, snapshots, stationsById, photovoltaicPower + windPower, loadPower
            );
            boolean executableTargetSatisfied = latestProjection.feasible()
                    && Math.abs(latestProjection.plannedBalanceErrorKw()) < properties.balanceToleranceKw()
                    && latestIteration.convergenceMetrics().consensusError() < properties.consensusTolerance();
            executableTargetConsecutiveIterations = executableTargetSatisfied
                    ? executableTargetConsecutiveIterations + 1 : 0;
            iterationHistory.add(iterationEvidence(
                    iteration,
                    latestIteration,
                    latestProjection,
                    executableTargetConsecutiveIterations
            ));
            if (executableTargetConsecutiveIterations >= properties.requiredConsecutiveIterations()) {
                OptimizationResult result = buildOptimizationResult(
                        runEntity,
                        contextSnapshot,
                        latestIteration,
                        latestSafeTargets,
                        latestProjection,
                        iteration,
                        elapsedMillis(startedAtNanos)
                );
                persistOptimizationAudit(runEntity, result, iterationHistory, latestIteration, latestProjection);
                return result;
            }
        }

        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("controllerType", controllerType(runEntity));
        evidence.put("controlStrategy", controlStrategy(runEntity));
        evidence.put("randomSeed", randomSeed(runEntity));
        evidence.put("maximumIterations", MAXIMUM_CONVERGENCE_ITERATIONS);
        evidence.put("maximumDurationSeconds", MAXIMUM_CONVERGENCE_DURATION.toSeconds());
        evidence.put("iterationHistory", iterationHistory);
        if (latestIteration != null) {
            evidence.put("balanceErrorKw", latestIteration.convergenceMetrics().balanceErrorKw());
            evidence.put("consensusError", latestIteration.convergenceMetrics().consensusError());
        }
        if (latestProjection != null) {
            evidence.put("projectedBalanceErrorKw", latestProjection.plannedBalanceErrorKw());
        }
        OptimizationResult timeoutResult = new OptimizationResult(
                List.of(),
                controllerType(runEntity),
                properties.configurationVersion(),
                OptimizationStatus.TIME_LIMIT_WITHOUT_SOLUTION,
                null,
                elapsedMillis(startedAtNanos),
                null,
                iterationHistory.size(),
                contextSnapshot.snapshotId(),
                latestProjection == null ? null : latestProjection.plannedBalanceErrorKw(),
                evidence,
                "CONVERGENCE_TIMEOUT",
                Map.of(),
                Map.of()
        );
        persistOptimizationAudit(runEntity, timeoutResult, iterationHistory, latestIteration, latestProjection);
        return timeoutResult;
    }

    /**
     * 为已观测故障创建控制会话并执行首轮真实控制。
     *
     * @param runEntity 实验运行
     * @return 首轮执行结果
     */
    @Transactional
    public synchronized DistributedControlExecution start(ExperimentRunEntity runEntity) {
        ControlSession session = createSession(runEntity);
        sessionsByRunId.put(runEntity.getRunId(), session);
        return executeIteration(runEntity, session);
    }

    /**
     * 基于当前真实遥测执行确定性集中式实验基线。
     *
     * <p>该路径仅用于三策略对照中的集中式组：不构造或运行一致性消息，仍通过与分布式组相同的
     * MQTT 指令服务执行已投影的目标功率。</p>
     *
     * @param runEntity 实验运行
     * @return 集中式执行结果
     */
    @Transactional
    public synchronized DistributedControlExecution startCentralized(ExperimentRunEntity runEntity) {
        List<TelemetrySnapshotEntity> snapshots = telemetrySnapshotRepository.findAll();
        Map<String, StationEntity> stationsById = new LinkedHashMap<>();
        stationRepository.findAll().forEach(station -> stationsById.put(station.getStationId(), station));
        List<TelemetrySnapshotEntity> controllable = snapshots.stream()
                .filter(snapshot -> isControllable(stationsById.get(snapshot.getStationId())))
                .filter(TelemetrySnapshotEntity::isOnline)
                .filter(snapshot -> snapshot.getLinkState() != LinkState.S3)
                .toList();
        List<DistributedMarketBalanceProjector.ResourceTarget> marketTargets = new ArrayList<>();
        Map<String, Double> rampRates = new LinkedHashMap<>();
        for (TelemetrySnapshotEntity snapshot : controllable) {
            StationEntity station = stationsById.get(snapshot.getStationId());
            double rampRate = maximumRampRate(snapshot.getStationId(), snapshots);
            rampRates.put(snapshot.getStationId(), rampRate);
            DistributedDispatchAgent.DistributedDispatchDecision candidate = new DistributedDispatchAgent.DistributedDispatchDecision(
                    snapshot.getStationId(), 1, snapshot.getLinkState(), "CENTRALIZED_CANDIDATE", 0.0, 0.0,
                    snapshot.getActivePower(), snapshot.getActivePower(), false, true
            );
            DistributedDispatchSafetyValidator.TargetValidation validation = safetyValidator.validateTarget(
                    candidate, snapshot, station.getResourceType(), rampRate
            );
            marketTargets.add(new DistributedMarketBalanceProjector.ResourceTarget(
                    snapshot.getStationId(), station.getResourceType(), validation.executable(),
                    validation.projectedPowerKw(), validation.rampLowerBoundKw(), validation.rampUpperBoundKw()
            ));
        }
        DistributedMarketBalanceProjector.BalanceProjection marketProjection = marketBalanceProjector.project(
                powerOf(snapshots, stationsById, ResourceType.PHOTOVOLTAIC)
                        + powerOf(snapshots, stationsById, ResourceType.WIND),
                loadPowerOf(snapshots, stationsById),
                marketTargets,
                properties.balanceToleranceKw()
        );
        return new DistributedControlExecution("centralized-" + runEntity.getRunId(), null,
                "CENTRAL_OPTIMIZATION", marketProjection.plannedBalanceErrorKw(), marketProjection.feasible(),
                1, controllable.size());
    }

    /**
     * 在真实遥测更新后推进相关实验的一轮协同控制。
     *
     * @param stationId 已更新遥测的场站标识
     */
    @Transactional
    public synchronized void observeTelemetry(String stationId) {
        List<String> staleRunIds = new ArrayList<>();
        for (Map.Entry<String, ControlSession> entry : sessionsByRunId.entrySet()) {
            ExperimentRunEntity runEntity = experimentRunRepository.findByRunId(entry.getKey()).orElse(null);
            if (runEntity == null || !("FAULT_OBSERVED".equals(runEntity.getStatus()) || "ORCHESTRATING".equals(runEntity.getStatus()))) {
                staleRunIds.add(entry.getKey());
                continue;
            }
            if (!entry.getValue().nodeIds().contains(stationId)) {
                continue;
            }
            OffsetDateTime now = OffsetDateTime.now(clock);
            if (!entry.getValue().isReady(now)) {
                continue;
            }
            if (!hasCoherentSnapshotSet()) {
                continue;
            }
            DistributedDispatchSafetyValidator.TelemetryValidation telemetryValidation = validateCurrentTelemetry();
            persistTelemetryValidation(runEntity, telemetryValidation, calculateActualMarketBalance());
            if (!telemetryValidation.valid()) {
                staleRunIds.add(entry.getKey());
                continue;
            }
            refreshLinkStates(entry.getValue());
            executeIteration(runEntity, entry.getValue());
        }
        staleRunIds.forEach(sessionsByRunId::remove);
    }

    private Map<String, StationEntity> loadStationsById() {
        Map<String, StationEntity> stationsById = new LinkedHashMap<>();
        stationRepository.findAll().forEach(station -> stationsById.put(station.getStationId(), station));
        return stationsById;
    }

    private List<SafeControlTarget> validateTargets(
            DistributedDispatchCoordinator.DispatchIterationResult iterationResult,
            List<TelemetrySnapshotEntity> snapshots,
            Map<String, StationEntity> stationsById,
            DispatchContextSnapshotResponse contextSnapshot
    ) {
        Map<String, TelemetrySnapshotEntity> snapshotsByStationId = snapshots.stream()
                .collect(java.util.stream.Collectors.toMap(TelemetrySnapshotEntity::getStationId, snapshot -> snapshot));
        Map<String, ResourceCapabilityResponse> capabilitiesByStationId = contextSnapshot.resourceCapabilities().stream()
                .collect(java.util.stream.Collectors.toMap(ResourceCapabilityResponse::stationId, capability -> capability));
        List<SafeControlTarget> safeTargets = new ArrayList<>();
        for (DistributedDispatchAgent.DistributedDispatchDecision decision : iterationResult.decisions().values()) {
            TelemetrySnapshotEntity snapshot = snapshotsByStationId.get(decision.nodeId());
            StationEntity station = stationsById.get(decision.nodeId());
            double rampRate = maximumRampRate(decision.nodeId(), snapshots);
            DistributedDispatchSafetyValidator.TargetValidation validation = safetyValidator.validateTarget(
                    decision,
                    snapshot,
                    station == null ? null : station.getResourceType(),
                    rampRate
            );
            validation = applyCapabilityBoundary(
                    decision,
                    validation,
                    capabilitiesByStationId.get(decision.nodeId()),
                    contextSnapshot
            );
            safeTargets.add(new SafeControlTarget(decision.nodeId(), decision.iteration(), rampRate, validation));
        }
        return List.copyOf(safeTargets);
    }

    /**
     * 将实时遥测投影结果与调度上下文中的能力、SOC 和市场限额取交集。
     *
     * @param decision 分布式候选决策
     * @param telemetryValidation 遥测容量与爬坡校验结果
     * @param capability 调度上下文能力快照
     * @param contextSnapshot 调度上下文快照
     * @return 使用统一能力边界后的校验结果
     */
    private DistributedDispatchSafetyValidator.TargetValidation applyCapabilityBoundary(
            DistributedDispatchAgent.DistributedDispatchDecision decision,
            DistributedDispatchSafetyValidator.TargetValidation telemetryValidation,
            ResourceCapabilityResponse capability,
            DispatchContextSnapshotResponse contextSnapshot
    ) {
        if (!telemetryValidation.executable() || capability == null || !capability.dataTrusted()
                || capability.linkState() == LinkState.S3) {
            return new DistributedDispatchSafetyValidator.TargetValidation(
                    false,
                    "CAPABILITY_UNAVAILABLE",
                    decision.targetPowerKw(),
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    capability == null ? 0.0 : capability.availableCapacity(),
                    telemetryValidation.maximumRampRateKw()
            );
        }
        double capabilityLowerBound = capability.resourceType() == ResourceType.GRID
                ? -contextSnapshot.marketSignal().gridExportLimit() : capability.minimumPower();
        double capabilityUpperBound = capability.resourceType() == ResourceType.GRID
                ? Math.min(capability.maximumPower(), contextSnapshot.marketSignal().gridImportLimit())
                : capability.maximumPower();
        double lowerBound = Math.max(telemetryValidation.lowerBoundKw(), capabilityLowerBound);
        double upperBound = Math.min(telemetryValidation.upperBoundKw(), capabilityUpperBound);
        double rampLowerBound = Math.max(
                telemetryValidation.rampLowerBoundKw(),
                capability.currentPower() - capability.maximumRampDown()
        );
        double rampUpperBound = Math.min(
                telemetryValidation.rampUpperBoundKw(),
                capability.currentPower() + capability.maximumRampUp()
        );
        rampLowerBound = Math.max(rampLowerBound, lowerBound);
        rampUpperBound = Math.min(rampUpperBound, upperBound);
        if (lowerBound > upperBound || rampLowerBound > rampUpperBound) {
            return new DistributedDispatchSafetyValidator.TargetValidation(
                    false,
                    "CAPABILITY_BOUNDARY_EMPTY",
                    decision.targetPowerKw(),
                    0.0,
                    lowerBound,
                    upperBound,
                    rampLowerBound,
                    rampUpperBound,
                    capability.availableCapacity(),
                    telemetryValidation.maximumRampRateKw()
            );
        }
        double projectedPower = Math.max(rampLowerBound, Math.min(rampUpperBound, decision.targetPowerKw()));
        String reasonCode = projectedPower == decision.targetPowerKw()
                ? "WITHIN_CAPABILITY_LIMITS" : "PROJECTED_TO_CAPABILITY_LIMITS";
        return new DistributedDispatchSafetyValidator.TargetValidation(
                true,
                reasonCode,
                decision.targetPowerKw(),
                projectedPower,
                lowerBound,
                upperBound,
                rampLowerBound,
                rampUpperBound,
                capability.availableCapacity(),
                telemetryValidation.maximumRampRateKw()
        );
    }

    private DistributedMarketBalanceProjector.BalanceProjection projectMarketBalance(
            List<SafeControlTarget> safeTargets,
            List<TelemetrySnapshotEntity> snapshots,
            Map<String, StationEntity> stationsById,
            double fixedGenerationPower,
            double loadPower
    ) {
        Map<String, TelemetrySnapshotEntity> snapshotsByStationId = snapshots.stream()
                .collect(java.util.stream.Collectors.toMap(TelemetrySnapshotEntity::getStationId, snapshot -> snapshot));
        List<DistributedMarketBalanceProjector.ResourceTarget> marketTargets = safeTargets.stream().map(target -> {
            TelemetrySnapshotEntity snapshot = snapshotsByStationId.get(target.stationId());
            StationEntity station = stationsById.get(target.stationId());
            DistributedDispatchSafetyValidator.TargetValidation validation = target.validation();
            double fixedPower = snapshot == null ? 0.0 : snapshot.getActivePower();
            return new DistributedMarketBalanceProjector.ResourceTarget(
                    target.stationId(),
                    station.getResourceType(),
                    validation.executable(),
                    validation.executable() ? validation.projectedPowerKw() : fixedPower,
                    validation.executable() ? validation.rampLowerBoundKw() : fixedPower,
                    validation.executable() ? validation.rampUpperBoundKw() : fixedPower
            );
        }).toList();
        return marketBalanceProjector.project(
                fixedGenerationPower,
                loadPower,
                marketTargets,
                properties.balanceToleranceKw()
        );
    }

    private Map<String, Object> iterationEvidence(
            int iteration,
            DistributedDispatchCoordinator.DispatchIterationResult iterationResult,
            DistributedMarketBalanceProjector.BalanceProjection marketProjection,
            int executableTargetConsecutiveIterations
    ) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("iteration", iteration);
        evidence.put("balanceErrorKw", iterationResult.convergenceMetrics().balanceErrorKw());
        evidence.put("consensusError", iterationResult.convergenceMetrics().consensusError());
        evidence.put("consecutiveSatisfiedIterations",
                iterationResult.convergenceMetrics().consecutiveSatisfiedIterations());
        evidence.put("strictlyConverged", iterationResult.convergenceMetrics().converged());
        evidence.put("projectedBalanceErrorKw", marketProjection.plannedBalanceErrorKw());
        evidence.put("marketProjectionFeasible", marketProjection.feasible());
        evidence.put("executableTargetConsecutiveIterations", executableTargetConsecutiveIterations);
        evidence.put("executableTargetConverged",
                executableTargetConsecutiveIterations >= properties.requiredConsecutiveIterations());
        evidence.put("messageSentCount", iterationResult.states().values().stream()
                .mapToLong(DistributedAgentState::messageSentCount).sum());
        evidence.put("messageLostCount", iterationResult.states().values().stream()
                .mapToLong(DistributedAgentState::messageLostCount).sum());
        return Map.copyOf(evidence);
    }

    private OptimizationResult buildOptimizationResult(
            ExperimentRunEntity runEntity,
            DispatchContextSnapshotResponse contextSnapshot,
            DistributedDispatchCoordinator.DispatchIterationResult iterationResult,
            List<SafeControlTarget> safeTargets,
            DistributedMarketBalanceProjector.BalanceProjection marketProjection,
            int iteration,
            long solveTimeMs
    ) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        ForecastPointResponse activePoint = contextSnapshot.forecastSnapshot().points().stream()
                .filter(point -> !now.isBefore(point.intervalStart()) && now.isBefore(point.intervalEnd()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("调度上下文没有当前可执行预测时间片"));
        Map<String, StationEntity> stationsById = loadStationsById();
        List<ResourceDispatchTarget> targets = safeTargets.stream()
                .filter(target -> target.validation().executable())
                .map(target -> {
                    StationEntity station = stationsById.get(target.stationId());
                    double targetPower = marketProjection.targetPowersKw().get(target.stationId());
                    Map<String, Object> targetEvidence = new LinkedHashMap<>();
                    targetEvidence.put("controllerType", controllerType(runEntity));
                    targetEvidence.put("controlStrategy", controlStrategy(runEntity));
                    targetEvidence.put("reasonCode", target.validation().reasonCode());
                    targetEvidence.put("requestedPowerKw", target.validation().requestedPowerKw());
                    targetEvidence.put("rampLowerBoundKw", target.validation().rampLowerBoundKw());
                    targetEvidence.put("rampUpperBoundKw", target.validation().rampUpperBoundKw());
                    return new ResourceDispatchTarget(
                            target.stationId(),
                            station.getResourceType(),
                            targetPower,
                            directionOf(targetPower),
                            OptimizationStatus.FEASIBLE,
                            iteration,
                            contextSnapshot.snapshotId(),
                            targetEvidence,
                            now
                    );
                })
                .sorted(java.util.Comparator.comparing(ResourceDispatchTarget::stationId))
                .toList();
        if (targets.isEmpty()) {
            throw new IllegalStateException("分布式优化没有可执行的远程控制目标");
        }
        double netPowerTarget = targets.stream().mapToDouble(target -> signedPower(target.resourceType(), target.targetPower())).sum();
        double gridPower = targetPowerOf(targets, ResourceType.GRID);
        double storagePower = targetPowerOf(targets, ResourceType.STORAGE);
        String itemId = "dispatch-plan-item-" + UUID.randomUUID();
        DispatchPlanItem item = new DispatchPlanItem(
                itemId,
                contextSnapshot.snapshotId(),
                activePoint.intervalStart(),
                activePoint.intervalEnd(),
                netPowerTarget,
                Math.max(0.0, gridPower),
                Math.max(0.0, -gridPower),
                Math.max(0.0, -storagePower),
                Math.max(0.0, storagePower),
                0.0,
                0.0,
                0.0,
                0.0,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                controlStrategy(runEntity) + "_CONSENSUS",
                0
        );
        String targetHash = ResourceDispatchTarget.summaryHash(targets);
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("controllerType", controllerType(runEntity));
        evidence.put("controlStrategy", controlStrategy(runEntity));
        evidence.put("randomSeed", randomSeed(runEntity));
        evidence.put("iteration", iteration);
        evidence.put("balanceErrorKw", iterationResult.convergenceMetrics().balanceErrorKw());
        evidence.put("consensusError", iterationResult.convergenceMetrics().consensusError());
        evidence.put("consecutiveSatisfiedIterations",
                iterationResult.convergenceMetrics().consecutiveSatisfiedIterations());
        evidence.put("executableTargetConsecutiveIterations", properties.requiredConsecutiveIterations());
        evidence.put("projectedBalanceErrorKw", marketProjection.plannedBalanceErrorKw());
        evidence.put("messageSentCount", iterationResult.states().values().stream()
                .mapToLong(DistributedAgentState::messageSentCount).sum());
        evidence.put("messageLostCount", iterationResult.states().values().stream()
                .mapToLong(DistributedAgentState::messageLostCount).sum());
        evidence.put("constraintViolationCount", 0);
        evidence.put("targetHash", targetHash);
        return new OptimizationResult(
                List.of(item),
                controllerType(runEntity),
                properties.configurationVersion(),
                OptimizationStatus.FEASIBLE,
                null,
                solveTimeMs,
                null,
                iteration,
                contextSnapshot.snapshotId(),
                marketProjection.plannedBalanceErrorKw(),
                Map.copyOf(evidence),
                null,
                Map.of(itemId, targets),
                Map.of(itemId, targetHash)
        );
    }

    private void persistOptimizationAudit(
            ExperimentRunEntity runEntity,
            OptimizationResult result,
            List<Map<String, Object>> iterationHistory,
            DistributedDispatchCoordinator.DispatchIterationResult latestIteration,
            DistributedMarketBalanceProjector.BalanceProjection latestProjection
    ) {
        try {
            LinkedHashMap<String, Object> summary = readSummary(runEntity);
            Map<String, Object> distributedControl = new LinkedHashMap<>();
            distributedControl.put("controllerType", controllerType(runEntity));
            distributedControl.put("controlStrategy", controlStrategy(runEntity));
            distributedControl.put("randomSeed", randomSeed(runEntity));
            distributedControl.put("convergenceStatus", result.status() == OptimizationStatus.FEASIBLE
                    ? "CONVERGED" : "CONVERGENCE_TIMEOUT");
            distributedControl.put("iteration", result.optimizationRound());
            distributedControl.put("solveTimeMs", result.solveTimeMs());
            distributedControl.put("plannedBalanceErrorKw", result.plannedBalanceError());
            distributedControl.put("targetSummaryHashesByItemId", result.targetSummaryHashesByItemId());
            distributedControl.put("constraintEvidence", result.constraintEvidence());
            distributedControl.put("iterationHistory", iterationHistory);
            if (latestIteration != null) {
                distributedControl.put("consensusError", latestIteration.convergenceMetrics().consensusError());
                distributedControl.put("messageSentCount", latestIteration.states().values().stream()
                        .mapToLong(DistributedAgentState::messageSentCount).sum());
                distributedControl.put("messageLostCount", latestIteration.states().values().stream()
                        .mapToLong(DistributedAgentState::messageLostCount).sum());
            }
            if (latestProjection != null) {
                distributedControl.put("marketBalanceProjection", latestProjection);
            }
            summary.put("distributedControl", distributedControl);
            summary.put("controllerType", controllerType(runEntity));
            runEntity.setResultSummary(objectMapper.writeValueAsString(summary));
            experimentRunRepository.save(runEntity);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("分布式优化审计序列化失败", exception);
        }
    }

    private String controlStrategy(ExperimentRunEntity runEntity) {
        try {
            Object value = readSummary(runEntity).get("controlStrategy");
            return value instanceof String strategy && !strategy.isBlank() ? strategy : "ADAPTIVE";
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("实验策略摘要反序列化失败", exception);
        }
    }

    private long randomSeed(ExperimentRunEntity runEntity) {
        try {
            Object value = readSummary(runEntity).get("randomSeed");
            return value instanceof Number number ? number.longValue() : runEntity.getRunId().hashCode();
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("实验随机种子摘要反序列化失败", exception);
        }
    }

    private String controllerType(ExperimentRunEntity runEntity) {
        return "FIXED_WEIGHT".equals(controlStrategy(runEntity))
                ? "FIXED_WEIGHT_CONSENSUS" : "ADAPTIVE_CONSENSUS";
    }

    private PowerDirection directionOf(double power) {
        return power > 0.0 ? PowerDirection.POSITIVE : power < 0.0 ? PowerDirection.NEGATIVE : PowerDirection.NEUTRAL;
    }

    private double targetPowerOf(List<ResourceDispatchTarget> targets, ResourceType resourceType) {
        return targets.stream()
                .filter(target -> target.resourceType() == resourceType)
                .mapToDouble(ResourceDispatchTarget::targetPower)
                .sum();
    }

    private double signedPower(ResourceType resourceType, double power) {
        return resourceType == ResourceType.LOAD || resourceType == ResourceType.CHARGING ? -Math.abs(power) : power;
    }

    private long elapsedMillis(long startedAtNanos) {
        return (System.nanoTime() - startedAtNanos) / 1_000_000L;
    }

    private ControlSession createSession(ExperimentRunEntity runEntity) {
        return createSession(runEntity, telemetrySnapshotRepository.findAll(), loadStationsById(), List.of());
    }

    private ControlSession createSession(
            ExperimentRunEntity runEntity,
            List<TelemetrySnapshotEntity> snapshots,
            Map<String, StationEntity> stationsById,
            List<ResourceCapabilityResponse> resourceCapabilities
    ) {
        List<TelemetrySnapshotEntity> controllableSnapshots = snapshots.stream()
                .filter(snapshot -> isControllable(stationsById.get(snapshot.getStationId())))
                .toList();
        if (controllableSnapshots.isEmpty()) {
            throw new IllegalStateException("没有可用于分布式协同的在线可调资源");
        }
        CommunicationGraph graph = createCompleteGraph(controllableSnapshots);
        ConsensusPolicy policy = createConsensusPolicy(runEntity);
        List<DistributedDispatchAgent> agents = new ArrayList<>();
        List<TelemetrySnapshotEntity> collaborativeSnapshots = controllableSnapshots.stream()
                .filter(snapshot -> snapshot.getLinkState() != LinkState.S3)
                .toList();
        double autonomousPower = controllableSnapshots.stream()
                .filter(snapshot -> snapshot.getLinkState() == LinkState.S3)
                .mapToDouble(TelemetrySnapshotEntity::getActivePower)
                .sum();
        double collaborativeDemand = collaborativeSnapshots.isEmpty() ? 0.0
                : (calculateResidualDemand(snapshots, stationsById) - autonomousPower) / collaborativeSnapshots.size();
        Map<String, ResourceCapabilityResponse> capabilitiesByStationId = resourceCapabilities.stream()
                .collect(java.util.stream.Collectors.toMap(ResourceCapabilityResponse::stationId, capability -> capability));
        for (TelemetrySnapshotEntity snapshot : controllableSnapshots) {
            StationEntity station = stationsById.get(snapshot.getStationId());
            ResourceCapabilityResponse capability = capabilitiesByStationId.get(snapshot.getStationId());
            double localDemand = snapshot.getLinkState() == LinkState.S3
                    ? snapshot.getActivePower() : collaborativeDemand;
            agents.add(new DistributedDispatchAgent(
                    createCostModel(snapshot, station, capability),
                    graph,
                    policy,
                    snapshot.getActivePower(),
                    localDemand,
                    initialSoc(snapshot, station, capability),
                    snapshot.getLinkState()
            ));
        }
        return new ControlSession(
                new DistributedDispatchCoordinator(agents, new ConvergenceEvaluator(properties)),
                controllableSnapshots.stream().map(TelemetrySnapshotEntity::getStationId).toList(),
                UUID.randomUUID().toString(),
                new Random(randomSeed(runEntity)),
                0,
                null
        );
    }

    private DistributedControlExecution executeIteration(ExperimentRunEntity runEntity, ControlSession session) {
        List<TelemetrySnapshotEntity> snapshots = telemetrySnapshotRepository.findAll();
        Map<String, StationEntity> stationsById = new LinkedHashMap<>();
        stationRepository.findAll().forEach(station -> stationsById.put(station.getStationId(), station));
        refreshLinkStates(session);
        session.coordinator().synchronizeActualPower(snapshots.stream()
                .filter(snapshot -> session.nodeIds().contains(snapshot.getStationId()))
                .collect(java.util.stream.Collectors.toMap(TelemetrySnapshotEntity::getStationId, TelemetrySnapshotEntity::getActivePower)));
        double photovoltaicPower = powerOf(snapshots, stationsById, ResourceType.PHOTOVOLTAIC);
        double windPower = powerOf(snapshots, stationsById, ResourceType.WIND);
        double loadPower = loadPowerOf(snapshots, stationsById);
        long latencyMillis = Math.round(snapshots.stream().mapToInt(TelemetrySnapshotEntity::getLatency).average().orElse(0.0));
        double packetLossRate = snapshots.stream().mapToDouble(TelemetrySnapshotEntity::getPacketLossRate).average().orElse(0.0);
        DistributedDispatchCoordinator.DispatchIterationResult iterationResult = session.coordinator().iterate(
                runEntity.getRunId(),
                Map.of(),
                ITERATION_HOURS,
                OffsetDateTime.now(clock),
                latencyMillis,
                packetLossRate,
                photovoltaicPower,
                windPower,
                loadPower,
                session.random()
        );
        Map<String, TelemetrySnapshotEntity> snapshotsByStationId = snapshots.stream()
                .collect(java.util.stream.Collectors.toMap(TelemetrySnapshotEntity::getStationId, snapshot -> snapshot));
        List<SafeControlTarget> safeTargets = new ArrayList<>();
        for (DistributedDispatchAgent.DistributedDispatchDecision decision : iterationResult.decisions().values()) {
            TelemetrySnapshotEntity snapshot = snapshotsByStationId.get(decision.nodeId());
            StationEntity station = stationsById.get(decision.nodeId());
            double rampRate = maximumRampRate(decision.nodeId(), snapshots);
            DistributedDispatchSafetyValidator.TargetValidation validation = safetyValidator.validateTarget(
                    decision,
                    snapshot,
                    station == null ? null : station.getResourceType(),
                    rampRate
            );
            safeTargets.add(new SafeControlTarget(decision.nodeId(), decision.iteration(), rampRate, validation));
        }
        List<DistributedMarketBalanceProjector.ResourceTarget> marketTargets = safeTargets.stream().map(target -> {
            TelemetrySnapshotEntity snapshot = snapshotsByStationId.get(target.stationId());
            StationEntity station = stationsById.get(target.stationId());
            DistributedDispatchSafetyValidator.TargetValidation validation = target.validation();
            double fixedPower = snapshot == null ? 0.0 : snapshot.getActivePower();
            return new DistributedMarketBalanceProjector.ResourceTarget(
                    target.stationId(),
                    station.getResourceType(),
                    validation.executable(),
                    validation.executable() ? validation.projectedPowerKw() : fixedPower,
                    validation.executable() ? validation.rampLowerBoundKw() : fixedPower,
                    validation.executable() ? validation.rampUpperBoundKw() : fixedPower
            );
        }).toList();
        DistributedMarketBalanceProjector.BalanceProjection marketProjection = marketBalanceProjector.project(
                photovoltaicPower + windPower,
                loadPower,
                marketTargets,
                properties.balanceToleranceKw()
        );
        int executableTargetCount = (int) safeTargets.stream().filter(target -> target.validation().executable()).count();
        String batchId = null;
        int nextIteration = session.iteration() + 1;
        ControlSession advancedSession = session.withIteration(nextIteration, OffsetDateTime.now(clock));
        sessionsByRunId.put(runEntity.getRunId(), advancedSession);
        persistAudit(runEntity, iterationResult, safeTargets, marketProjection, batchId, nextIteration);
        String controlMode = executableTargetCount == 0 ? "LOCAL_AUTONOMY" : "EDGE_COORDINATION";
        return new DistributedControlExecution(
                session.sessionId(),
                batchId,
                controlMode,
                marketProjection.plannedBalanceErrorKw(),
                marketProjection.feasible(),
                nextIteration,
                executableTargetCount
        );
    }

    private void refreshLinkStates(ControlSession session) {
        Map<String, TelemetrySnapshotEntity> snapshots = telemetrySnapshotRepository.findAll().stream()
                .collect(java.util.stream.Collectors.toMap(TelemetrySnapshotEntity::getStationId, snapshot -> snapshot));
        for (String nodeId : session.nodeIds()) {
            TelemetrySnapshotEntity snapshot = snapshots.get(nodeId);
            if (snapshot != null) {
                session.coordinator().changeLinkState(nodeId, snapshot.getLinkState());
            }
        }
    }

    private void persistAudit(
            ExperimentRunEntity runEntity,
            DistributedDispatchCoordinator.DispatchIterationResult iterationResult,
            List<SafeControlTarget> safeTargets,
            DistributedMarketBalanceProjector.BalanceProjection marketProjection,
            String batchId,
            int iteration
    ) {
        try {
            LinkedHashMap<String, Object> summary = readSummary(runEntity);
            Map<String, Object> distributedControl = new LinkedHashMap<>();
            distributedControl.put("controller", "DISTRIBUTED_DISPATCH_COORDINATOR");
            distributedControl.put("iteration", iteration);
            distributedControl.put("balanceErrorKw", marketProjection.plannedBalanceErrorKw());
            distributedControl.put("consensusCandidateBalanceErrorKw", iterationResult.convergenceMetrics().balanceErrorKw());
            distributedControl.put("consensusError", iterationResult.convergenceMetrics().consensusError());
            distributedControl.put("converged", marketProjection.feasible());
            distributedControl.put("marketDemandSatisfied", marketProjection.feasible());
            distributedControl.put("marketBalanceProjection", marketProjection);
            distributedControl.put("messageSentCount", iterationResult.states().values().stream().mapToLong(state -> state.messageSentCount()).sum());
            distributedControl.put("messageLostCount", iterationResult.states().values().stream().mapToLong(state -> state.messageLostCount()).sum());
            distributedControl.put("executionBatchId", batchId);
            distributedControl.put("decisions", iterationResult.decisions().values());
            distributedControl.put("targetSafetyValidations", safeTargets.stream().map(target -> Map.of(
                    "stationId", target.stationId(),
                    "iteration", target.iteration(),
                    "validation", target.validation()
            )).toList());
            summary.put("distributedControl", distributedControl);
            List<Object> iterationHistory = summary.get("distributedControlIterations") instanceof List<?> source
                    ? new ArrayList<>(source) : new ArrayList<>();
            iterationHistory.add(distributedControl);
            summary.put("distributedControlIterations", iterationHistory);
            runEntity.setResultSummary(objectMapper.writeValueAsString(summary));
            experimentRunRepository.save(runEntity);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("分布式控制审计序列化失败", exception);
        }
    }

    private DistributedDispatchSafetyValidator.TelemetryValidation validateCurrentTelemetry() {
        List<TelemetrySnapshotEntity> snapshots = telemetrySnapshotRepository.findAll();
        Map<String, ResourceType> resourceTypes = new LinkedHashMap<>();
        stationRepository.findAll().forEach(station -> resourceTypes.put(station.getStationId(), station.getResourceType()));
        return safetyValidator.validateTelemetry(snapshots, resourceTypes::get);
    }

    private boolean hasCoherentSnapshotSet() {
        List<TelemetrySnapshotEntity> snapshots = telemetrySnapshotRepository.findAll();
        if (snapshots.size() < stationRepository.count()) {
            return false;
        }
        OffsetDateTime earliestTimestamp = snapshots.stream()
                .map(TelemetrySnapshotEntity::getDeviceTimestamp)
                .filter(java.util.Objects::nonNull)
                .min(OffsetDateTime::compareTo)
                .orElse(null);
        OffsetDateTime latestTimestamp = snapshots.stream()
                .map(TelemetrySnapshotEntity::getDeviceTimestamp)
                .filter(java.util.Objects::nonNull)
                .max(OffsetDateTime::compareTo)
                .orElse(null);
        return earliestTimestamp != null && latestTimestamp != null
                && Duration.between(earliestTimestamp, latestTimestamp).abs().compareTo(MAXIMUM_SNAPSHOT_SKEW) <= 0;
    }

    private void persistTelemetryValidation(
            ExperimentRunEntity runEntity,
            DistributedDispatchSafetyValidator.TelemetryValidation telemetryValidation,
            ActualMarketBalance actualMarketBalance
    ) {
        try {
            LinkedHashMap<String, Object> summary = readSummary(runEntity);
            Map<String, Object> validation = new LinkedHashMap<>();
            validation.put("valid", telemetryValidation.valid());
            validation.put("checkedAt", OffsetDateTime.now(clock));
            validation.put("violations", telemetryValidation.violations());
            validation.put("actualSupplyKw", actualMarketBalance.supplyKw());
            validation.put("actualDemandKw", actualMarketBalance.demandKw());
            validation.put("actualBalanceErrorKw", actualMarketBalance.balanceErrorKw());
            validation.put("balanceToleranceKw", properties.balanceToleranceKw());
            validation.put("marketDemandSatisfied", actualMarketBalance.marketDemandSatisfied());
            summary.put("postDispatchTelemetryValidation", validation);
            runEntity.setResultSummary(objectMapper.writeValueAsString(summary));
            experimentRunRepository.save(runEntity);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("调度后遥测约束审计序列化失败", exception);
        }
    }

    private ActualMarketBalance calculateActualMarketBalance() {
        Map<String, ResourceType> resourceTypes = new LinkedHashMap<>();
        stationRepository.findAll().forEach(station -> resourceTypes.put(station.getStationId(), station.getResourceType()));
        double supplyPower = 0.0;
        double demandPower = 0.0;
        for (TelemetrySnapshotEntity snapshot : telemetrySnapshotRepository.findAll()) {
            ResourceType resourceType = resourceTypes.get(snapshot.getStationId());
            if (resourceType == ResourceType.LOAD || resourceType == ResourceType.CHARGING) {
                demandPower += Math.abs(snapshot.getActivePower());
            } else if (resourceType != null) {
                supplyPower += snapshot.getActivePower();
            }
        }
        double balanceError = Math.abs(supplyPower - demandPower);
        return new ActualMarketBalance(supplyPower, demandPower, balanceError,
                balanceError <= properties.balanceToleranceKw());
    }

    private LinkedHashMap<String, Object> readSummary(ExperimentRunEntity runEntity) throws JsonProcessingException {
        if (runEntity.getResultSummary() == null || runEntity.getResultSummary().isBlank() || !runEntity.getResultSummary().startsWith("{")) {
            return new LinkedHashMap<>();
        }
        return objectMapper.readValue(runEntity.getResultSummary().split("；", 2)[0], SUMMARY_TYPE);
    }

    private boolean isControllable(StationEntity station) {
        return station != null && station.isEnabled() && switch (station.getResourceType()) {
            case THERMAL, GRID, STORAGE -> true;
            default -> false;
        };
    }

    private CommunicationGraph createCompleteGraph(List<TelemetrySnapshotEntity> snapshots) {
        Map<String, Map<String, Double>> weights = new LinkedHashMap<>();
        for (TelemetrySnapshotEntity snapshot : snapshots) {
            Map<String, Double> neighbors = new LinkedHashMap<>();
            for (TelemetrySnapshotEntity candidate : snapshots) {
                if (!snapshot.getStationId().equals(candidate.getStationId())) {
                    neighbors.put(candidate.getStationId(), 1.0);
                }
            }
            weights.put(snapshot.getStationId(), neighbors);
        }
        return new CommunicationGraph(weights);
    }

    private IncrementalCostModel createCostModel(
            TelemetrySnapshotEntity snapshot,
            StationEntity station,
            ResourceCapabilityResponse capability
    ) {
        double capacity = Math.max(1.0, snapshot.getAvailableCapacity());
        double minimumPower = capability == null
                ? station.getResourceType() == ResourceType.STORAGE ? -capacity : 0.0
                : capability.minimumPower();
        double maximumPower = capability == null ? capacity : capability.maximumPower();
        double maximumRampUp = capability == null ? capacity * 0.2 : capability.maximumRampUp();
        double maximumRampDown = capability == null ? capacity * 0.2 : capability.maximumRampDown();
        double minimumSoc = capability != null && capability.storageConstraint() != null
                ? capability.storageConstraint().minimumSoc() : 10.0;
        double maximumSoc = capability != null && capability.storageConstraint() != null
                ? capability.storageConstraint().maximumSoc() : 90.0;
        double energyCapacity = station.getResourceType() == ResourceType.STORAGE
                ? capability != null && capability.storageConstraint() != null
                ? capability.storageConstraint().energyCapacityKWh() : capacity * 2.0
                : 0.0;
        double chargeEfficiency = capability != null && capability.storageConstraint() != null
                ? capability.storageConstraint().chargeEfficiency() : 0.95;
        double dischargeEfficiency = capability != null && capability.storageConstraint() != null
                ? capability.storageConstraint().dischargeEfficiency() : 0.95;
        return new IncrementalCostModel(
                snapshot.getStationId(), station.getResourceType(), 0.01, 0.0, 0.0,
                minimumPower, maximumPower, maximumRampUp, maximumRampDown,
                minimumSoc, maximumSoc, energyCapacity, chargeEfficiency, dischargeEfficiency
        );
    }

    private ConsensusPolicy createConsensusPolicy(ExperimentRunEntity runEntity) {
        try {
            String strategy = readSummary(runEntity).get("controlStrategy") instanceof String value ? value : "ADAPTIVE";
            return "FIXED_WEIGHT".equals(strategy) ? new FixedWeightConsensusPolicy(properties) : new AdaptiveConsensusPolicy(properties);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("实验策略摘要反序列化失败", exception);
        }
    }

    private double initialSoc(
            TelemetrySnapshotEntity snapshot,
            StationEntity station,
            ResourceCapabilityResponse capability
    ) {
        if (station.getResourceType() != ResourceType.STORAGE) {
            return 0.0;
        }
        return capability != null && capability.storageConstraint() != null
                ? capability.storageConstraint().soc() : 50.0;
    }

    private double calculateResidualDemand(List<TelemetrySnapshotEntity> snapshots, Map<String, StationEntity> stationsById) {
        return loadPowerOf(snapshots, stationsById)
                - powerOf(snapshots, stationsById, ResourceType.PHOTOVOLTAIC)
                - powerOf(snapshots, stationsById, ResourceType.WIND);
    }

    private double powerOf(List<TelemetrySnapshotEntity> snapshots, Map<String, StationEntity> stationsById, ResourceType resourceType) {
        return snapshots.stream().filter(snapshot -> {
            StationEntity station = stationsById.get(snapshot.getStationId());
            return station != null && station.getResourceType() == resourceType;
        }).mapToDouble(TelemetrySnapshotEntity::getActivePower).sum();
    }

    private double loadPowerOf(List<TelemetrySnapshotEntity> snapshots, Map<String, StationEntity> stationsById) {
        return snapshots.stream().filter(snapshot -> {
            StationEntity station = stationsById.get(snapshot.getStationId());
            return station != null && (station.getResourceType() == ResourceType.LOAD || station.getResourceType() == ResourceType.CHARGING);
        }).mapToDouble(snapshot -> Math.abs(snapshot.getActivePower())).sum();
    }

    private double maximumRampRate(String stationId, List<TelemetrySnapshotEntity> snapshots) {
        return snapshots.stream().filter(snapshot -> stationId.equals(snapshot.getStationId())).findFirst()
                .map(snapshot -> Math.max(1.0, snapshot.getAvailableCapacity() * 0.2)).orElse(1.0);
    }

    /**
     * 分布式控制首轮或增量轮次的执行结果。
     *
     * @param sessionId 分布式会话标识
     * @param executionBatchId MQTT 控制批次标识
     * @param controlMode 生效控制模式
     * @param balanceErrorKw 当前功率平衡误差
     * @param converged 是否已收敛
     * @param iteration 已执行轮次
     * @param commandCount 已下发命令数量
     */
    public record DistributedControlExecution(
            String sessionId,
            String executionBatchId,
            String controlMode,
            double balanceErrorKw,
            boolean converged,
            int iteration,
            int commandCount
    ) {
    }

    private record ControlSession(
            DistributedDispatchCoordinator coordinator,
            List<String> nodeIds,
            String sessionId,
            Random random,
            int iteration,
            OffsetDateTime lastExecutedAt
    ) {
        private ControlSession withIteration(int nextIteration, OffsetDateTime executedAt) {
            return new ControlSession(coordinator, nodeIds, sessionId, random, nextIteration, executedAt);
        }

        private boolean isReady(OffsetDateTime now) {
            return lastExecutedAt == null || !now.isBefore(lastExecutedAt.plus(MINIMUM_CONTROL_INTERVAL));
        }
    }

    private record SafeControlTarget(
            String stationId,
            long iteration,
            double maximumRampRate,
            DistributedDispatchSafetyValidator.TargetValidation validation
    ) {
    }

    /**
     * 调度后最新遥测对应的实际市场供需平衡结果。
     *
     * @param supplyKw 实际总供给功率，单位 kW
     * @param demandKw 实际总需求功率，单位 kW
     * @param balanceErrorKw 实际供需平衡误差，单位 kW
     * @param marketDemandSatisfied 是否处于配置容差内
     */
    private record ActualMarketBalance(
            double supplyKw,
            double demandKw,
            double balanceErrorKw,
            boolean marketDemandSatisfied
    ) {
    }
}

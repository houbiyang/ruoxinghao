package com.weaksignal.vpp.simulator;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * 仿真引擎。
 */
public class SimulationEngine {

    private static final String THERMAL_STATION_ID = "thermal-plant-06";
    private static final String GRID_STATION_ID = "grid-link-00";
    private static final String EDGE_COORDINATION_MODE = "edge-coordination";
    private static final double SUPPLY_RESERVE_KW = 5.0;
    private static final double NORMAL_POWER_WAVE_AMPLITUDE_KW = 4.0;
    private static final double NORMAL_POWER_NOISE_AMPLITUDE_KW = 0.5;
    private static final double PHOTOVOLTAIC_BASELINE_RATIO = 0.30;
    private static final double WIND_BASELINE_RATIO = 0.28;
    private static final double THERMAL_BASELINE_RATIO = 0.06;
    private static final double THERMAL_BASELINE_MAXIMUM_RATIO = 0.095;
    private static final double GRID_BASELINE_RATIO = 0.03;
    private static final double STORAGE_BASELINE_RATIO = 0.03;
    private static final double LOAD_BASELINE_RATIO = 0.52;
    private static final double CHARGING_BASELINE_RATIO = 0.34;
    private static final double WIND_CUT_IN_SPEED = 3.0;
    private static final double WIND_RATED_SPEED = 12.0;
    private static final double WIND_CUT_OUT_SPEED = 25.0;
    private static final double PHOTOVOLTAIC_REFERENCE_IRRADIANCE = 1_000.0;
    private static final double PHOTOVOLTAIC_REFERENCE_TEMPERATURE = 25.0;
    private static final double PHOTOVOLTAIC_TEMPERATURE_COEFFICIENT = 0.004;
    private static final double STORAGE_STATE_OF_HEALTH = 0.96;
    private static final double LOCAL_AUTONOMY_RAMP_LIMIT_KW = 5.0;
    private static final double RECOVERY_RAMP_LIMIT_KW = 2.5;
    private static final int REQUIRED_RECOVERY_STABLE_SAMPLES = 3;
    private final SimulationConfig config;
    private final Map<String, StationRuntime> runtimeMap;
    private final Map<String, Random> randomMap;
    private final Map<String, FaultActionResult> processedFaultActions;
    private final Map<String, DispatchControlResult> processedDispatchCommands;
    private final Map<String, String> latestPlanVersionByStation;
    private DemoScenario activeScenario;
    private ExperimentCommand activeExperimentCommand;
    private long experimentStartTick;
    private long experimentEndTick;
    private long tickIndex;
    private ScenarioReportPayload pendingRecoveryReport;
    private ExperimentCommand recoveringExperimentCommand;
    private long recoveryCompletionTick;

    /**
     * 创建仿真引擎。
     *
     * @param config 仿真配置
     */
    public SimulationEngine(SimulationConfig config) {
        this.config = config;
        this.activeScenario = config.initialScenario();
        this.runtimeMap = new HashMap<>();
        this.randomMap = new HashMap<>();
        this.processedFaultActions = new HashMap<>();
        this.processedDispatchCommands = new HashMap<>();
        this.latestPlanVersionByStation = new HashMap<>();
        for (StationDefinition stationDefinition : StationDefinition.defaults()) {
            if (!config.stationIds().contains(stationDefinition.stationId())) {
                continue;
            }
            runtimeMap.put(stationDefinition.stationId(), new StationRuntime(stationDefinition));
            randomMap.put(stationDefinition.stationId(), new Random(config.randomSeed() + stationDefinition.stationId().hashCode()));
        }
    }

    /**
     * 查询当前场景。
     *
     * @return 当前场景
     */
    public DemoScenario getActiveScenario() {
        return activeScenario;
    }

    /**
     * 切换场景。
     *
     * @param scenario 目标场景
     */
    public void applyScenario(DemoScenario scenario) {
        this.activeScenario = scenario;
        this.activeExperimentCommand = null;
        if (scenario == DemoScenario.STABLE) {
            clearDispatchTargets();
        }
    }

    /**
     * 应用实验命令。
     *
     * @param command 实验命令
     */
    public void applyExperiment(ExperimentCommand command) {
        this.activeScenario = command.scenario();
        this.activeExperimentCommand = command;
        this.experimentStartTick = tickIndex;
        long durationTicks = Math.max(1, command.durationSeconds() * 1000L / config.simulationIntervalMillis());
        this.experimentEndTick = tickIndex + durationTicks;
        if (command.randomSeed() != null) {
            resetRandomSources(command.randomSeed());
        }
        for (StationRuntime runtime : runtimeMap.values()) {
            runtime.setFrozenPower(null);
            if (isTargetStation(runtime)
                    && ("COM_OFFLINE".equals(command.experimentScenarioCode())
                    || "T5_FULL_LOSS_AUTONOMY".equals(command.experimentScenarioCode()))) {
                double referencePower = runtime.getLastExecutedSafeTargetPower() == null
                        ? runtime.getActivePower() : runtime.getLastExecutedSafeTargetPower();
                runtime.startLocalAutonomy(referencePower, tickIndex);
                runtime.setDispatchTargetPower(null);
                runtime.setDispatchControlMode(null);
            } else {
                runtime.clearLocalAutonomyState();
            }
        }
    }

    private void resetRandomSources(long seed) {
        randomMap.clear();
        runtimeMap.keySet().forEach(stationId -> randomMap.put(stationId, new Random(seed + stationId.hashCode())));
    }

    /**
     * 停止实验并恢复稳定场景。
     */
    public ScenarioReportPayload stopExperiment() {
        return stopExperiment(activeExperimentCommand == null ? null : activeExperimentCommand.experimentRunId());
    }

    /**
     * 停止指定实验并恢复目标场站的稳定运行状态。
     *
     * @param experimentRunId 待停止实验标识；与当前实验不一致时不执行停止
     */
    public ScenarioReportPayload stopExperiment(String experimentRunId) {
        if (activeExperimentCommand != null
                && experimentRunId != null
                && !experimentRunId.equals(activeExperimentCommand.experimentRunId())) {
            return null;
        }
        if (activeExperimentCommand == null) {
            return null;
        }
        ExperimentCommand completedCommand = activeExperimentCommand;
        this.activeScenario = DemoScenario.RECOVERING;
        this.activeExperimentCommand = null;
        this.recoveringExperimentCommand = completedCommand;
        this.recoveryCompletionTick = tickIndex + Math.max(2, 8_000L / config.simulationIntervalMillis());
        for (StationRuntime runtime : runtimeMap.values()) {
            runtime.setFrozenPower(null);
            runtime.setOnline(true);
            runtime.setTelemetryRateDivisor(1);
        }
        OffsetDateTime reportedAt = OffsetDateTime.now(ZoneOffset.ofHours(8));
        return new ScenarioReportPayload(
                completedCommand.experimentRunId(), completedCommand.experimentScenarioCode(), "RECOVERY_STARTED",
                completedCommand.targetStationIds(), reportedAt, null
        );
    }

    /**
     * 停止指定实验；若模拟器已无活动实验，则幂等确认稳定恢复并清除残留控制目标。
     *
     * @param experimentRunId 待恢复实验标识
     * @param experimentScenarioCode 实验场景编码
     * @param targetStationIds 目标场站标识
     * @return 恢复开始或恢复完成报告；存在其他活动实验时返回 {@code null}
     */
    public ScenarioReportPayload stopOrConfirmStable(
            String experimentRunId,
            String experimentScenarioCode,
            List<String> targetStationIds
    ) {
        ScenarioReportPayload recoveryStarted = stopExperiment(experimentRunId);
        if (recoveryStarted != null) {
            return recoveryStarted;
        }
        if (activeExperimentCommand != null) {
            return null;
        }
        if (recoveringExperimentCommand != null) {
            if (!recoveringExperimentCommand.experimentRunId().equals(experimentRunId)) {
                return null;
            }
            return new ScenarioReportPayload(
                    experimentRunId,
                    recoveringExperimentCommand.experimentScenarioCode(),
                    "RECOVERY_STARTED",
                    recoveringExperimentCommand.targetStationIds(),
                    OffsetDateTime.now(ZoneOffset.ofHours(8)),
                    null
            );
        }
        this.activeScenario = DemoScenario.STABLE;
        this.recoveringExperimentCommand = null;
        clearDispatchTargets();
        return new ScenarioReportPayload(
                experimentRunId,
                experimentScenarioCode,
                "RECOVERED",
                targetStationIds,
                OffsetDateTime.now(ZoneOffset.ofHours(8)),
                null
        );
    }

    /**
     * 校验并应用有功功率调度指令。
     *
     * @param command 调度指令
     * @param now 当前时间
     * @return 指令处理结果
     */
    public synchronized DispatchControlResult applyDispatchControl(
            DispatchControlCommand command,
            OffsetDateTime now
    ) {
        DispatchControlResult previousResult = processedDispatchCommands.get(command.commandId());
        if (previousResult != null) {
            return previousResult;
        }
        StationRuntime runtime = runtimeMap.get(command.stationId());
        if (runtime == null) {
            return rememberDispatch(command.commandId(), new DispatchControlResult(false, "目标场站不存在"));
        }
        if (command.expireAt().isBefore(now)) {
            return rememberDispatch(command.commandId(), new DispatchControlResult(false, "控制指令已过期"));
        }
        if (command.targetPower() < 0
                && runtime.getStationDefinition().resourceType() != ResourceType.STORAGE
                && runtime.getStationDefinition().resourceType() != ResourceType.GRID) {
            return rememberDispatch(command.commandId(), new DispatchControlResult(false, "仅储能和电网联络线允许负向功率"));
        }
        if (Math.abs(command.targetPower()) > runtime.getAvailableCapacity()) {
            return rememberDispatch(command.commandId(), new DispatchControlResult(false, "目标功率超过场站可用容量"));
        }
        if (!runtime.isOnline() || runtime.isLocalAutonomy() || activeScenario == DemoScenario.LOST) {
            return rememberDispatch(command.commandId(), new DispatchControlResult(false, "场站离线或处于 S3 本地自治状态"));
        }
        if (command.planVersion() != null && !command.planVersion().isBlank()) {
            String latestVersion = latestPlanVersionByStation.get(command.stationId());
            if (latestVersion != null && comparePlanVersion(command.planVersion(), latestVersion) < 0) {
                return rememberDispatch(command.commandId(), new DispatchControlResult(false, "计划版本已过期，场站已接受更新版本"));
            }
            latestPlanVersionByStation.put(command.stationId(), command.planVersion());
        }
        runtime.setDispatchTargetPower(command.targetPower());
        runtime.setDispatchPlanId(command.dispatchPlanId());
        runtime.setDispatchRampRate(Math.max(0.1, command.maximumRampRate()));
        runtime.setDispatchControlMode(command.controlMode());
        runtime.setLastExecutedSafeTargetPower(command.targetPower());
        return rememberDispatch(command.commandId(), new DispatchControlResult(true, "控制指令已接受，场站按爬坡约束执行"));
    }

    /**
     * 释放指定计划对场站的功率控制，使场站重新跟随模拟器自然基线。
     *
     * @param stationId 场站标识
     * @param dispatchPlanId 调度计划标识
     * @return 是否成功释放控制
     */
    public synchronized boolean releaseDispatchControl(String stationId, String dispatchPlanId) {
        StationRuntime runtime = runtimeMap.get(stationId);
        if (runtime == null) {
            return false;
        }
        if (runtime.getDispatchPlanId() != null
                && dispatchPlanId != null
                && !runtime.getDispatchPlanId().equals(dispatchPlanId)) {
            return false;
        }
        runtime.setDispatchTargetPower(null);
        runtime.setDispatchPlanId(null);
        runtime.setDispatchControlMode(null);
        runtime.setDispatchRampRate(0.0);
        return true;
    }

    /**
     * 校验并幂等执行故障处置动作。
     *
     * @param command 故障动作
     * @param now 当前时间
     * @return 执行结果
     */
    public synchronized FaultActionResult applyFaultAction(FaultActionCommand command, OffsetDateTime now) {
        FaultActionResult previousResult = processedFaultActions.get(command.actionId());
        if (previousResult != null) {
            return previousResult;
        }
        StationRuntime runtime = runtimeMap.get(command.stationId());
        if (runtime == null) {
            return remember(command.actionId(), new FaultActionResult(false, "目标场站不存在"));
        }
        if (command.expireAt().isBefore(now)) {
            return remember(command.actionId(), new FaultActionResult(false, "故障处置动作已过期"));
        }
        FaultActionResult result = switch (command.actionType()) {
            case MARK_DATA_UNTRUSTED -> {
                runtime.setDataTrusted(false);
                yield new FaultActionResult(true, "场站遥测已标记为不可信");
            }
            case FREEZE_EXPIRED_COMMANDS -> {
                runtime.setDispatchTargetPower(null);
                yield new FaultActionResult(true, "已冻结过期远程指令");
            }
            case REDUCE_TELEMETRY_RATE -> {
                runtime.setTelemetryRateDivisor(3);
                yield new FaultActionResult(true, "非关键遥测已降频");
            }
            case LIMIT_CONTROL_RAMP -> {
                runtime.setDispatchRampRate(5);
                yield new FaultActionResult(true, "控制爬坡限制已收紧");
            }
            case EXCLUDE_OFFLINE_CAPACITY -> new FaultActionResult(true, "聚合侧容量剔除标记已确认");
            case ENTER_LOCAL_AUTONOMY -> {
                double referencePower = runtime.getLastExecutedSafeTargetPower() == null
                        ? runtime.getActivePower() : runtime.getLastExecutedSafeTargetPower();
                runtime.startLocalAutonomy(referencePower, tickIndex);
                runtime.setDispatchTargetPower(null);
                yield new FaultActionResult(true, "场站已进入本地自治");
            }
            case ENABLE_CONSERVATIVE_ESTIMATE -> new FaultActionResult(true, "场站已启用保守状态估计");
            case START_RATE_LIMITED_RECOVERY -> {
                runtime.setDataTrusted(true);
                runtime.setTelemetryRateDivisor(2);
                yield new FaultActionResult(true, "已按快照、去重和斜坡顺序启动恢复");
            }
        };
        return remember(command.actionId(), result);
    }

    /**
     * 执行一次仿真。
     *
     * @return 仿真输出
     */
    public SimulationTick nextTick() {
        tickIndex += 1;
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.ofHours(8));
        if (activeExperimentCommand != null && activeExperimentCommand.automaticRecovery() && tickIndex >= experimentEndTick) {
            ScenarioReportPayload recoveryStarted = stopExperiment(activeExperimentCommand.experimentRunId());
            if (recoveryStarted != null) {
                pendingRecoveryReport = recoveryStarted;
            }
        }
        if (recoveringExperimentCommand != null && tickIndex >= recoveryCompletionTick) {
            ExperimentCommand completedCommand = recoveringExperimentCommand;
            activeScenario = DemoScenario.STABLE;
            recoveringExperimentCommand = null;
            clearDispatchTargets();
            pendingRecoveryReport = new ScenarioReportPayload(
                    completedCommand.experimentRunId(), completedCommand.experimentScenarioCode(), "RECOVERED",
                    completedCommand.targetStationIds(), now, null
            );
        }
        List<TelemetryPayload> telemetryPayloads = new ArrayList<>();
        List<HeartbeatPayload> heartbeatPayloads = new ArrayList<>();

        for (StationRuntime runtime : runtimeMap.values()) {
            updateRuntime(runtime);
        }
        balanceThermalSupply();
        balanceEdgeCoordinatedGrid();
        for (StationRuntime runtime : runtimeMap.values()) {
            if (shouldPublishHeartbeat(runtime)) {
                heartbeatPayloads.add(new HeartbeatPayload(runtime.getStationDefinition().stationId(), runtime.isOnline(), now, buildClientId(runtime)));
            }
            if (runtime.isOnline() && tickIndex % runtime.getTelemetryRateDivisor() == 0) {
                runtime.nextSequenceNumber();
                if (shouldPublishTelemetry(runtime)) {
                    telemetryPayloads.add(toTelemetryPayload(runtime, now));
                }
            }
        }

        List<ScenarioReportPayload> scenarioReports = pendingRecoveryReport == null ? List.of() : List.of(pendingRecoveryReport);
        pendingRecoveryReport = null;
        return new SimulationTick(telemetryPayloads, heartbeatPayloads, scenarioReports);
    }

    private void updateRuntime(StationRuntime runtime) {
        StationDefinition definition = runtime.getStationDefinition();
        Random random = randomMap.get(definition.stationId());
        double phase = tickIndex / 6.0 + Math.abs(definition.stationId().hashCode() % 7);
        double drift = (random.nextDouble() * 2.0 - 1.0) * NORMAL_POWER_NOISE_AMPLITUDE_KW;
        double targetPower = switch (definition.resourceType()) {
            case PHOTOVOLTAIC -> clamp(definition.capacity() * PHOTOVOLTAIC_BASELINE_RATIO
                            + Math.sin(phase) * NORMAL_POWER_WAVE_AMPLITUDE_KW + drift,
                    definition.capacity() * 0.15, definition.capacity() * 0.96);
            case WIND -> clamp(definition.capacity() * WIND_BASELINE_RATIO
                            + Math.cos(phase * 0.8) * NORMAL_POWER_WAVE_AMPLITUDE_KW + drift,
                    definition.capacity() * 0.12, definition.capacity() * 0.88);
            case THERMAL -> clamp(definition.capacity() * THERMAL_BASELINE_RATIO
                            + Math.sin(phase * 0.45) * NORMAL_POWER_WAVE_AMPLITUDE_KW + drift,
                    definition.capacity() * 0.08, definition.capacity() * 0.24);
            case GRID -> clamp(definition.capacity() * GRID_BASELINE_RATIO
                            + Math.sin(phase * 0.4) * NORMAL_POWER_WAVE_AMPLITUDE_KW + drift,
                    definition.capacity() * -0.25, definition.capacity() * 0.45);
            case STORAGE -> clampStoragePower(runtime, definition.capacity(),
                    definition.capacity() * STORAGE_BASELINE_RATIO
                            + Math.sin(phase * 0.7) * NORMAL_POWER_WAVE_AMPLITUDE_KW + drift);
            case LOAD -> clamp(definition.capacity() * LOAD_BASELINE_RATIO
                            + Math.sin(phase * 0.6) * NORMAL_POWER_WAVE_AMPLITUDE_KW + drift,
                    definition.capacity() * 0.24, definition.capacity() * 0.9);
            case CHARGING -> clamp(definition.capacity() * CHARGING_BASELINE_RATIO
                            + Math.cos(phase * 0.5) * NORMAL_POWER_WAVE_AMPLITUDE_KW + drift,
                    definition.capacity() * 0.1, definition.capacity() * 0.82);
        };

        if (activeExperimentCommand == null && activeScenario == DemoScenario.DEGRADED) {
            targetPower *= definition.resourceType() == ResourceType.WIND || definition.resourceType() == ResourceType.PHOTOVOLTAIC ? 0.9 : 0.95;
        } else if (activeExperimentCommand == null && activeScenario == DemoScenario.LOST) {
            boolean offlineGroup = definition.stationId().equals("pv-gobi-01") || definition.stationId().equals("wind-ridge-02");
            runtime.setOnline(!offlineGroup);
            if (offlineGroup) {
                return;
            }
            targetPower *= 0.72;
        } else if (activeExperimentCommand == null && activeScenario == DemoScenario.RECOVERING) {
            runtime.setOnline(true);
            if (recoveringExperimentCommand == null || isTargetStation(runtime)) {
                targetPower = runtime.getActivePower() + (targetPower - runtime.getActivePower()) * 0.35;
            }
        } else {
            runtime.setOnline(true);
        }

        applyExperimentRuntime(runtime);
        double availableCapacity = applyExperimentPhysicalFault(runtime, definition.capacity());
        targetPower = applyExperimentPhysicalPower(runtime, targetPower, availableCapacity);

        if (runtime.isLocalAutonomy()) {
            targetPower = localAutonomyTarget(runtime, availableCapacity);
        } else if (runtime.getDispatchTargetPower() != null) {
            targetPower = Math.copySign(
                    Math.min(Math.abs(runtime.getDispatchTargetPower()), availableCapacity),
                    runtime.getDispatchTargetPower()
            );
        } else if (activeExperimentCommand == null && recoveringExperimentCommand == null
                && activeScenario == DemoScenario.STABLE) {
            targetPower = ensureNormalNonZeroPower(definition, targetPower, phase);
        }

        runtime.setAvailableCapacity(availableCapacity);
        double rampLimit = runtime.isLocalAutonomy()
                ? (recoveringExperimentCommand == null ? LOCAL_AUTONOMY_RAMP_LIMIT_KW : RECOVERY_RAMP_LIMIT_KW)
                : runtime.getDispatchTargetPower() == null
                ? (definition.resourceType() == ResourceType.STORAGE ? 18 : 24)
                : runtime.getDispatchRampRate();
        double rampedPower = applyRamp(runtime.getActivePower(), targetPower, rampLimit);
        if (activeExperimentCommand != null && isTargetStation(runtime)
                && isPhysicalFault(activeExperimentCommand.experimentScenarioCode())) {
            rampedPower = Math.copySign(Math.min(Math.abs(rampedPower), availableCapacity), rampedPower);
        }
        runtime.setActivePower(rampedPower);
        if (runtime.isLocalAutonomy()) {
            boolean constraintSatisfied = Math.abs(rampedPower) <= availableCapacity + 0.000001
                    && storageSocAllowsPower(runtime, rampedPower);
            runtime.recordLocalAutonomyAction(rampedPower, constraintSatisfied);
            if (recoveringExperimentCommand != null && isTargetStation(runtime) && runtime.isOnline()
                    && runtime.recordRecoveryStableSample() >= REQUIRED_RECOVERY_STABLE_SAMPLES) {
                runtime.markRejoinedCommunicationGraph();
            }
        }
        runtime.setSoc(clamp(runtime.getSoc() + (definition.resourceType() == ResourceType.STORAGE ? -runtime.getActivePower() / 200.0 : 0), 10, 90));
        runtime.setTemperature(clamp(runtime.getTemperature() + (random.nextDouble() - 0.5) * 0.8, 18, 42));
        runtime.setVoltage(clamp(380 + (random.nextDouble() - 0.5) * 6, 360, 400));
        runtime.setFrequency(clamp(50 + (random.nextDouble() - 0.5) * 0.05, 49.8, 50.2));
        runtime.setVehicleCount((int) Math.round(clamp(runtime.getVehicleCount() + (random.nextDouble() - 0.5) * 2, 1, 18)));
    }

    /**
     * 使用火电机组补齐实时供需缺口，并保留安全供电裕度。
     *
     * <p>火电仅在没有显式调度目标时补偿供需缺口；已有调度目标时必须保持命令值，确保执行回执与遥测一致。</p>
     */
    private void balanceThermalSupply() {
        StationRuntime thermalRuntime = runtimeMap.get(THERMAL_STATION_ID);
        if (thermalRuntime == null || !thermalRuntime.isOnline() || thermalRuntime.isLocalAutonomy()) {
            return;
        }
        double demandPower = runtimeMap.values().stream()
                .filter(runtime -> runtime.getStationDefinition().resourceType() == ResourceType.LOAD
                        || runtime.getStationDefinition().resourceType() == ResourceType.CHARGING)
                .mapToDouble(StationRuntime::getActivePower)
                .map(power -> Math.max(0.0, power))
                .sum();
        double nonThermalSupplyPower = runtimeMap.values().stream()
                .filter(runtime -> runtime.getStationDefinition().resourceType() != ResourceType.LOAD
                        && runtime.getStationDefinition().resourceType() != ResourceType.CHARGING
                        && runtime.getStationDefinition().resourceType() != ResourceType.THERMAL)
                .mapToDouble(StationRuntime::getActivePower)
                .map(power -> Math.max(0.0, power))
                .sum();
        double requiredThermalPower = Math.max(0.0, demandPower + SUPPLY_RESERVE_KW - nonThermalSupplyPower);
        double normalMinimumPower = clamp(
                thermalRuntime.getStationDefinition().capacity() * THERMAL_BASELINE_RATIO
                        + Math.sin(tickIndex / 18.0) * NORMAL_POWER_WAVE_AMPLITUDE_KW,
                thermalRuntime.getStationDefinition().capacity() * 0.05,
                thermalRuntime.getStationDefinition().capacity() * 0.075
        );
        double targetPower = thermalRuntime.getDispatchTargetPower() == null
                ? Math.max(requiredThermalPower, normalMinimumPower)
                : thermalRuntime.getDispatchTargetPower();
        if (thermalRuntime.getDispatchTargetPower() == null
                && activeExperimentCommand == null
                && recoveringExperimentCommand == null
                && activeScenario == DemoScenario.STABLE
                && runtimeMap.size() == StationDefinition.defaults().size()) {
            targetPower = Math.min(
                    targetPower,
                    thermalRuntime.getStationDefinition().capacity() * THERMAL_BASELINE_MAXIMUM_RATIO
            );
        }
        thermalRuntime.setActivePower(Math.min(targetPower, thermalRuntime.getAvailableCapacity()));
    }

    /**
     * 保证稳定场景下各类资源保持非零运行功率，同时保留连续波动。
     *
     * @param definition 场站定义
     * @param targetPower 当前目标功率
     * @param phase 当前仿真相位
     * @return 避开零点后的目标功率
     */
    private double ensureNormalNonZeroPower(StationDefinition definition, double targetPower, double phase) {
        double minimumMagnitude = switch (definition.resourceType()) {
            case PHOTOVOLTAIC, WIND, THERMAL -> definition.capacity() * 0.05;
            case GRID, STORAGE -> definition.capacity() * 0.03;
            case LOAD, CHARGING -> definition.capacity() * 0.08;
        };
        if (Math.abs(targetPower) >= minimumMagnitude) {
            return targetPower;
        }
        double direction = targetPower == 0.0 ? (Math.sin(phase) >= 0 ? 1.0 : -1.0) : Math.signum(targetPower);
        double fluctuation = 1.0 + 0.18 * Math.sin(phase * 0.73);
        return direction * minimumMagnitude * fluctuation;
    }

    /**
     * 按实时容量、储能 SOC 和资源功率方向生成站端自治目标。
     *
     * @param runtime 场站运行时
     * @param availableCapacity 当前可用容量
     * @return 通过本地硬约束投影后的自治目标功率
     */
    private double localAutonomyTarget(StationRuntime runtime, double availableCapacity) {
        ResourceType resourceType = runtime.getStationDefinition().resourceType();
        double lowerBound = resourceType == ResourceType.STORAGE || resourceType == ResourceType.GRID
                ? -availableCapacity : 0.0;
        double targetPower = clamp(runtime.getLocalAutonomyReferencePower(), lowerBound, availableCapacity);
        if (resourceType == ResourceType.STORAGE) {
            if (runtime.getSoc() <= 10.0 && targetPower > 0.0) {
                return 0.0;
            }
            if (runtime.getSoc() >= 90.0 && targetPower < 0.0) {
                return 0.0;
            }
        }
        return targetPower;
    }

    /**
     * 判断储能当前 SOC 是否允许给定功率方向。
     *
     * @param runtime 场站运行时
     * @param power 当前功率
     * @return 非储能或储能方向满足 SOC 边界时返回 {@code true}
     */
    private boolean storageSocAllowsPower(StationRuntime runtime, double power) {
        if (runtime.getStationDefinition().resourceType() != ResourceType.STORAGE) {
            return true;
        }
        return !((runtime.getSoc() <= 10.0 && power > 0.0)
                || (runtime.getSoc() >= 90.0 && power < 0.0));
    }

    /**
     * 清除已结束实验遗留的调度目标，使稳定场景重新使用自然功率曲线。
     */
    private void clearDispatchTargets() {
        for (StationRuntime runtime : runtimeMap.values()) {
            runtime.setDispatchTargetPower(null);
            runtime.setDispatchPlanId(null);
            runtime.setDispatchControlMode(null);
            runtime.setDispatchRampRate(0.0);
            runtime.clearLocalAutonomyState();
        }
        latestPlanVersionByStation.clear();
    }

    /**
     * 使用电网联络线消除边缘协同计划执行后一周期内产生的实时功率偏差。
     *
     * <p>调度目标是日前或滚动计划基准，联络线在边缘协同模式下承担实时双向补差。
     * 补差结果不会越过当前联络线可用容量，正值表示购电，负值表示上网。</p>
     */
    private void balanceEdgeCoordinatedGrid() {
        StationRuntime gridRuntime = runtimeMap.get(GRID_STATION_ID);
        if (gridRuntime == null || !gridRuntime.isOnline() || gridRuntime.isLocalAutonomy()
                || !EDGE_COORDINATION_MODE.equals(gridRuntime.getDispatchControlMode())) {
            return;
        }
        double demandPower = runtimeMap.values().stream()
                .filter(runtime -> runtime.getStationDefinition().resourceType() == ResourceType.LOAD
                        || runtime.getStationDefinition().resourceType() == ResourceType.CHARGING)
                .mapToDouble(runtime -> round(runtime.getActivePower()))
                .map(Math::abs)
                .sum();
        double supplyWithoutGrid = runtimeMap.values().stream()
                .filter(runtime -> runtime.getStationDefinition().resourceType() != ResourceType.LOAD
                        && runtime.getStationDefinition().resourceType() != ResourceType.CHARGING
                        && runtime.getStationDefinition().resourceType() != ResourceType.GRID)
                .mapToDouble(runtime -> round(runtime.getActivePower()))
                .sum();
        double requiredGridPower = round(demandPower - supplyWithoutGrid);
        double capacity = Math.max(0.0, gridRuntime.getAvailableCapacity());
        gridRuntime.setActivePower(clamp(requiredGridPower, -capacity, capacity));
    }

    private TelemetryPayload toTelemetryPayload(StationRuntime runtime, OffsetDateTime now) {
        if (isActiveExperiment("T7_OUT_OF_ORDER_DUPLICATE") && isTargetStation(runtime) && tickIndex % 5 == 0) {
            runtime.rollbackSequenceNumber();
        }

        int latency = switch (activeScenario) {
            case STABLE -> 20 + (int) (Math.abs(Math.sin(tickIndex)) * 35);
            case DEGRADED -> 90 + (int) (Math.abs(Math.cos(tickIndex)) * 160);
            case LOST -> 180 + (int) (Math.abs(Math.sin(tickIndex * 0.7)) * 220);
            case RECOVERING -> 30 + (int) (Math.abs(Math.sin(tickIndex * 0.5)) * 20);
        };
        latency = applyExperimentLatency(runtime, latency);
        double packetLossRate = switch (activeScenario) {
            case STABLE -> 0.2;
            case DEGRADED -> 2.6;
            case LOST -> 8.5;
            case RECOVERING -> 1.2;
        };
        packetLossRate = applyExperimentPacketLoss(runtime, packetLossRate);
        String linkState = switch (activeScenario) {
            case STABLE -> "S0";
            case DEGRADED -> runtime.getStationDefinition().resourceType() == ResourceType.WIND ? "S2" : "S1";
            case LOST -> "S3";
            case RECOVERING -> "S4";
        };
        linkState = applyExperimentLinkState(runtime, linkState);
        OffsetDateTime deviceTimestamp = now.minusNanos(Math.max(10, latency - 12) * 1_000_000L);
        if (isActiveExperiment("T8_STALE_TELEMETRY") && isTargetStation(runtime)) {
            deviceTimestamp = now.minusSeconds(18);
        }
        double activePower = runtime.getFrozenPower() == null ? runtime.getActivePower() : runtime.getFrozenPower();

        Map<String, Double> metrics = new HashMap<>();
        if (runtime.getStationDefinition().resourceType() == ResourceType.STORAGE) {
            metrics.put("soc", round(runtime.getSoc()));
        }
        metrics.put("temperature", round(runtime.getTemperature()));
        metrics.put("voltage", round(runtime.getVoltage()));
        metrics.put("frequency", round(runtime.getFrequency()));
        addEnvironmentMetrics(runtime, metrics);
        if (runtime.getStationDefinition().resourceType() == ResourceType.CHARGING) {
            metrics.put("vehicleCount", (double) runtime.getVehicleCount());
        }
        if (activeExperimentCommand != null && isTargetStation(runtime)) {
            metrics.putAll(activeExperimentCommand.faultParameters());
            String scenarioCode = activeExperimentCommand.experimentScenarioCode();
            if ("ENV_WIND_CALM".equals(scenarioCode)) {
                metrics.put("windSpeed", experimentParameter("windSpeed", 0.0));
                metrics.put("turbulence", experimentParameter("turbulence", 0.0));
            }
            if ("ENV_OVERCAST".equals(scenarioCode)) {
                metrics.put("irradiance", experimentParameter("irradiance", 0.0));
                metrics.put("cloudCover", experimentParameter("cloudCover", 0.0));
            }
            if ("ENV_HEAT".equals(scenarioCode)) {
                metrics.put("ambientTemperature", experimentParameter("ambientTemperature", 25.0));
                metrics.put("moduleTemperature", experimentParameter("moduleTemperature", runtime.getTemperature()));
            }
            if ("ENV_DUST".equals(scenarioCode)) {
                metrics.put("dustDensity", experimentParameter("dustDensity", 0.0));
                metrics.put("visibility", experimentParameter("visibility", 10.0));
                metrics.put("conversionEfficiency", experimentParameter("conversionEfficiency", 1.0));
            }
            if ("DEV_INVERTER".equals(scenarioCode)) {
                metrics.put("deviceHealthStatus", 0.0);
                metrics.put("deratingRate", experimentParameter("deratingRate", experimentParameter("derating", 0.0)));
            }
            if ("DEV_STORAGE".equals(scenarioCode)) {
                metrics.put("onlineClusterCount", experimentParameter("onlineClusterCount", 6.0));
                metrics.put("remainingSoc", experimentParameter("remainingSoc", runtime.getSoc()));
            }
        }
        if (runtime.isLocalAutonomyEvidenceAvailable() && isTargetStation(runtime)) {
            metrics.put("localAutonomyActive", runtime.isLocalAutonomy() ? 1.0 : 0.0);
            metrics.put("localAutonomyStartTick", (double) runtime.getLocalAutonomyStartTick());
            metrics.put("localAutonomyReferencePower", round(runtime.getLocalAutonomyReferencePower()));
            metrics.put("localAutonomyActualPower", round(runtime.getLocalAutonomyActualPower()));
            metrics.put("localAutonomyActionCount", (double) runtime.getLocalAutonomyActionCount());
            metrics.put("localAutonomyConstraintViolationCount",
                    (double) runtime.getLocalAutonomyConstraintViolationCount());
            metrics.put("recoveryStableSampleCount", (double) runtime.getRecoveryStableSampleCount());
            metrics.put("rejoinedCommunicationGraph", runtime.isRejoinedCommunicationGraph() ? 1.0 : 0.0);
        }

        ExperimentCommand telemetryExperiment = activeExperimentCommand == null
                ? recoveringExperimentCommand : activeExperimentCommand;

        return new TelemetryPayload(
                UUID.randomUUID().toString(),
                runtime.getStationDefinition().stationId(),
                runtime.getStationDefinition().stationName(),
                runtime.getStationDefinition().resourceType().getValue(),
                runtime.getSequenceNumber(),
                deviceTimestamp,
                now,
                round(activePower),
                round(runtime.getAvailableCapacity()),
                latency,
                packetLossRate,
                runtime.isOnline(),
                !runtime.isDataTrusted() || activeScenario == DemoScenario.LOST ? "DEGRADED" : "GOOD",
                linkState,
                telemetryExperiment == null ? null : telemetryExperiment.experimentRunId(),
                activeExperimentCommand == null || !isTargetStation(runtime) ? null : activeExperimentCommand.experimentScenarioCode(),
                metrics
        );
    }

    /**
     * 写入模拟器实际采集的环境测量值。
     *
     * @param runtime 场站运行状态
     * @param metrics 待写入的遥测指标
     */
    private void addEnvironmentMetrics(StationRuntime runtime, Map<String, Double> metrics) {
        ResourceType resourceType = runtime.getStationDefinition().resourceType();
        if (resourceType == ResourceType.WIND) {
            metrics.put("windSpeed", round(8.0 + Math.sin(tickIndex / 8.0) * 1.5));
            metrics.put("turbulence", 0.08);
            return;
        }
        if (resourceType == ResourceType.PHOTOVOLTAIC) {
            metrics.put("irradiance", round(780.0 + Math.sin(tickIndex / 7.0) * 80.0));
            metrics.put("cloudCover", 0.15);
            metrics.put("ambientTemperature", round(runtime.getTemperature() - 5.0));
            metrics.put("moduleTemperature", round(runtime.getTemperature()));
            metrics.put("dustDensity", 0.5);
            metrics.put("visibility", 15.0);
            metrics.put("conversionEfficiency", 0.96);
        }
    }

    private void applyExperimentRuntime(StationRuntime runtime) {
        if (activeExperimentCommand == null || !isTargetStation(runtime)) {
            return;
        }
        String scenarioCode = activeExperimentCommand.experimentScenarioCode();
        if ("T4_INTERMITTENT_DISCONNECT".equals(scenarioCode)) {
            runtime.setOnline(tickIndex % 8 >= 2);
        } else if ("T5_FULL_LOSS_AUTONOMY".equals(scenarioCode) || "COM_OFFLINE".equals(scenarioCode)) {
            runtime.setOnline(false);
        } else if ("T8_STALE_TELEMETRY".equals(scenarioCode) && runtime.getFrozenPower() == null) {
            runtime.setFrozenPower(runtime.getActivePower());
        } else if ("T9_RECOVERY_SYNC".equals(scenarioCode)) {
            runtime.setOnline(true);
        } else if ("DEV_STORAGE".equals(scenarioCode)) {
            runtime.setSoc(clamp(experimentParameter("remainingSoc", runtime.getSoc()), 10.0, 90.0));
        }
    }

    private boolean shouldPublishHeartbeat(StationRuntime runtime) {
        return !((isActiveExperiment("T5_FULL_LOSS_AUTONOMY") || isActiveExperiment("COM_OFFLINE")) && isTargetStation(runtime));
    }

    private boolean shouldPublishTelemetry(StationRuntime runtime) {
        if (tickIndex % runtime.getTelemetryRateDivisor() != 0) {
            return false;
        }
        if (activeExperimentCommand == null || !isTargetStation(runtime)) {
            return true;
        }
        return switch (activeExperimentCommand.experimentScenarioCode()) {
            case "T2_RANDOM_PACKET_LOSS" -> tickIndex % 4 != 0;
            case "T3_BURST_PACKET_LOSS" -> tickIndex % 10 >= 3;
            case "T5_FULL_LOSS_AUTONOMY" -> false;
            case "T6_BANDWIDTH_LIMIT" -> tickIndex % 3 == 0;
            case "COM_PACKET_LOSS", "COM_COMBINED" -> shouldDropPacketLossTelemetry();
            case "COM_OFFLINE" -> false;
            default -> true;
        };
    }

    /**
     * 判断当前时间片是否应上报丢包实验的目标遥测。
     *
     * @return 未落入目标丢包或突发窗口时返回 {@code true}
     */
    private boolean shouldDropPacketLossTelemetry() {
        double packetLossRate = experimentParameter("packetLossRate", experimentParameter("packetLoss", 0.0));
        int burstLength = Math.max(1, (int) Math.round(experimentParameter("burstLength", 1.0)));
        int period = Math.max(burstLength, 20);
        int slot = (int) ((tickIndex - experimentStartTick) % period);
        int rateDropCount = (int) Math.round(period * packetLossRate / 100.0);
        int dropCount = Math.min(period, Math.max(burstLength, rateDropCount));
        return slot >= dropCount;
    }

    private FaultActionResult remember(String actionId, FaultActionResult result) {
        processedFaultActions.put(actionId, result);
        return result;
    }

    private DispatchControlResult rememberDispatch(String commandId, DispatchControlResult result) {
        processedDispatchCommands.put(commandId, result);
        return result;
    }

    private int comparePlanVersion(String leftVersion, String rightVersion) {
        int leftNumber = extractPlanVersionNumber(leftVersion);
        int rightNumber = extractPlanVersionNumber(rightVersion);
        return Integer.compare(leftNumber, rightNumber);
    }

    private int extractPlanVersionNumber(String version) {
        int separatorIndex = version.lastIndexOf("-v");
        if (separatorIndex < 0 || separatorIndex + 2 >= version.length()) {
            return 0;
        }
        try {
            return Integer.parseInt(version.substring(separatorIndex + 2));
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private int applyExperimentLatency(StationRuntime runtime, int latency) {
        if (!isTargetStation(runtime)) {
            return latency;
        }
        if (isActiveExperiment("T1_LATENCY_JITTER")) {
            return 120 + (int) (Math.abs(Math.sin(tickIndex * 1.7)) * 160);
        }
        if (isActiveExperiment("COM_LATENCY") || isActiveExperiment("COM_COMBINED")) {
            return (int) Math.round(experimentParameter("latency", 200.0)
                    + Math.abs(Math.sin(tickIndex * 1.7)) * experimentParameter("jitter", 0.0));
        }
        if (isActiveExperiment("T6_BANDWIDTH_LIMIT")) {
            return Math.max(latency, 150);
        }
        return latency;
    }

    private double applyExperimentPacketLoss(StationRuntime runtime, double packetLossRate) {
        if (!isTargetStation(runtime)) {
            return packetLossRate;
        }
        if (isActiveExperiment("T2_RANDOM_PACKET_LOSS")) {
            return 6.0;
        }
        if (isActiveExperiment("COM_PACKET_LOSS") || isActiveExperiment("COM_COMBINED")) {
            return experimentParameter("packetLossRate", experimentParameter("packetLoss", 0.0));
        }
        if (isActiveExperiment("T3_BURST_PACKET_LOSS")) {
            return 18.0;
        }
        if (isActiveExperiment("T10_CONTROL_ACK_LOSS")) {
            return 4.0;
        }
        return packetLossRate;
    }

    private String applyExperimentLinkState(StationRuntime runtime, String linkState) {
        if (activeExperimentCommand == null && recoveringExperimentCommand != null && !isTargetStation(runtime)) {
            return "S0";
        }
        if (!isTargetStation(runtime)) {
            return linkState;
        }
        if (isActiveExperiment("T3_BURST_PACKET_LOSS")
                || isActiveExperiment("T4_INTERMITTENT_DISCONNECT")
                || isActiveExperiment("T8_STALE_TELEMETRY")) {
            return "S2";
        }
        if (isActiveExperiment("T5_FULL_LOSS_AUTONOMY")) {
            return "S3";
        }
        if (isActiveExperiment("COM_OFFLINE")) {
            return "S3";
        }
        if (isActiveExperiment("COM_PACKET_LOSS") || isActiveExperiment("COM_COMBINED")) {
            return "S2";
        }
        if (isActiveExperiment("COM_LATENCY") || isActiveExperiment("COM_COMBINED")) {
            return "S1";
        }
        if (isActiveExperiment("T9_RECOVERY_SYNC")) {
            long elapsedTicks = tickIndex - experimentStartTick;
            return elapsedTicks < 3 ? "S3" : "S4";
        }
        if (isActiveExperiment("T0_STABLE_BASELINE")) {
            return "S0";
        }
        if (activeExperimentCommand != null) {
            return activeExperimentCommand.experimentScenarioCode().startsWith("COM_") ? "S1" : linkState;
        }
        return linkState;
    }

    private boolean isTargetStation(StationRuntime runtime) {
        ExperimentCommand command = activeExperimentCommand == null ? recoveringExperimentCommand : activeExperimentCommand;
        if (command == null || command.targetStationIds().isEmpty()) {
            return false;
        }
        return command.targetStationIds().contains(runtime.getStationDefinition().stationId());
    }

    /**
     * 获取指定场站的控制确认真实延迟。
     *
     * @param stationId 场站标识
     * @return 应延迟的毫秒数
     */
    public long getControlAcknowledgementDelayMillis(String stationId) {
        if (activeExperimentCommand == null || !activeExperimentCommand.targetStationIds().contains(stationId)) {
            return 0;
        }
        if (isActiveExperiment("COM_LATENCY") || isActiveExperiment("COM_COMBINED")) {
            return getCommunicationDelayMillis(stationId);
        }
        if (isActiveExperiment("T10_CONTROL_ACK_LOSS")) {
            return Math.max(config.simulationIntervalMillis(), 1_000L);
        }
        return 0;
    }

    /**
     * 获取指定场站当前通信故障对应的真实出站延迟。
     *
     * @param stationId 场站标识
     * @return 应延迟的毫秒数；非目标场站或无通信时延实验时返回零
     */
    public long getCommunicationDelayMillis(String stationId) {
        if (activeExperimentCommand == null || !activeExperimentCommand.targetStationIds().contains(stationId)
                || (!isActiveExperiment("COM_LATENCY") && !isActiveExperiment("COM_COMBINED"))) {
            return 0L;
        }
        double delayMillis = experimentParameter("latency", 0.0)
                + Math.abs(Math.sin(tickIndex * 1.7)) * experimentParameter("jitter", 0.0);
        return Math.max(0L, Math.round(delayMillis));
    }

    /**
     * 判断指定场站是否仍处于需延迟控制确认的通信时延实验中。
     *
     * @param stationId 场站标识
     * @return 延迟确认仍有效时返回 {@code true}
     */
    public boolean isControlAcknowledgementDelayActive(String stationId) {
        return activeExperimentCommand != null
                && activeExperimentCommand.targetStationIds().contains(stationId)
                && (isActiveExperiment("COM_LATENCY") || isActiveExperiment("COM_COMBINED"));
    }

    private boolean isActiveExperiment(String scenarioCode) {
        return activeExperimentCommand != null && scenarioCode.equals(activeExperimentCommand.experimentScenarioCode());
    }

    /**
     * 读取当前实验数值参数。
     *
     * @param parameterName 参数名称
     * @param defaultValue 默认值
     * @return 有限参数值或默认值
     */
    private double experimentParameter(String parameterName, double defaultValue) {
        if (activeExperimentCommand == null) {
            return defaultValue;
        }
        Double value = activeExperimentCommand.faultParameters().get(parameterName);
        return value != null && Double.isFinite(value) ? value : defaultValue;
    }

    private double applyExperimentPhysicalFault(StationRuntime runtime, double nominalCapacity) {
        if (activeExperimentCommand == null || !isTargetStation(runtime)) {
            return nominalCapacity;
        }
        String scenarioCode = activeExperimentCommand.experimentScenarioCode();
        return switch (scenarioCode) {
            case "ENV_WIND_CALM" -> nominalCapacity * windPowerFactor(experimentParameter("windSpeed", 0.0));
            case "ENV_OVERCAST" -> nominalCapacity * photovoltaicEnvironmentFactor(runtime);
            case "ENV_HEAT" -> nominalCapacity * clamp(1.0 - Math.max(0.0,
                    experimentParameter("moduleTemperature", experimentParameter("ambientTemperature", 25.0)) - 25.0) * 0.006, 0.3, 1.0);
            case "ENV_DUST" -> nominalCapacity * clamp(experimentParameter("conversionEfficiency", 0.7), 0.0, 1.0);
            case "DEV_INVERTER" -> nominalCapacity * clamp(1.0 - experimentParameter("deratingRate", experimentParameter("derating", 0.0)), 0.0, 1.0);
            case "DEV_STORAGE" -> nominalCapacity
                    * clamp(experimentParameter("onlineClusterCount", 6.0) / 8.0, 0.0, 1.0)
                    * STORAGE_STATE_OF_HEALTH;
            default -> nominalCapacity;
        };
    }

    private double applyExperimentPhysicalPower(StationRuntime runtime, double targetPower, double availableCapacity) {
        if (activeExperimentCommand == null || !isTargetStation(runtime)) {
            return targetPower;
        }
        String scenarioCode = activeExperimentCommand.experimentScenarioCode();
        if ("ENV_WIND_CALM".equals(scenarioCode) && runtime.getStationDefinition().resourceType() == ResourceType.WIND) {
            double windSpeed = experimentParameter("windSpeed", 0.0);
            return runtime.getStationDefinition().capacity() * windPowerFactor(windSpeed);
        }
        return Math.copySign(Math.min(Math.abs(targetPower), availableCapacity), targetPower);
    }

    /**
     * 判断实验场景是否会改变目标设备的物理能力。
     *
     * @param scenarioCode 实验场景编码
     * @return 会改变物理能力时返回 {@code true}
     */
    private boolean isPhysicalFault(String scenarioCode) {
        return switch (scenarioCode) {
            case "ENV_WIND_CALM", "ENV_OVERCAST", "ENV_HEAT", "ENV_DUST", "DEV_INVERTER", "DEV_STORAGE" -> true;
            default -> false;
        };
    }

    /**
     * 计算分段风机功率曲线系数。
     *
     * @param windSpeed 实时风速，单位 m/s
     * @return 额定容量对应的功率系数
     */
    private double windPowerFactor(double windSpeed) {
        if (windSpeed < WIND_CUT_IN_SPEED || windSpeed > WIND_CUT_OUT_SPEED) {
            return 0.0;
        }
        if (windSpeed >= WIND_RATED_SPEED) {
            return 1.0;
        }
        double numerator = Math.pow(windSpeed, 3) - Math.pow(WIND_CUT_IN_SPEED, 3);
        double denominator = Math.pow(WIND_RATED_SPEED, 3) - Math.pow(WIND_CUT_IN_SPEED, 3);
        return clamp(numerator / denominator, 0.0, 1.0);
    }

    /**
     * 计算已排除云量重复降额的光伏环境系数。
     *
     * @param runtime 光伏场站运行状态
     * @return 光伏物理可用容量系数
     */
    private double photovoltaicEnvironmentFactor(StationRuntime runtime) {
        double irradianceFactor = clamp(
                experimentParameter("irradiance", PHOTOVOLTAIC_REFERENCE_IRRADIANCE)
                        / PHOTOVOLTAIC_REFERENCE_IRRADIANCE,
                0.0,
                1.1
        );
        double moduleTemperature = experimentParameter("moduleTemperature", runtime.getTemperature());
        double temperatureFactor = clamp(
                1.0 - PHOTOVOLTAIC_TEMPERATURE_COEFFICIENT
                        * Math.max(moduleTemperature - PHOTOVOLTAIC_REFERENCE_TEMPERATURE, 0.0),
                0.7,
                1.0
        );
        double conversionEfficiency = experimentParameter("conversionEfficiency", 0.96);
        double soilingLossRate = experimentParameter("soilingLossRate", 0.0);
        double shadingLossRate = experimentParameter("shadingLossRate", 0.0);
        double snowLossRate = experimentParameter("snowCover", 0.0);
        double lossFactor = clamp(
                conversionEfficiency * (1.0 - soilingLossRate - shadingLossRate - 0.7 * snowLossRate),
                0.15,
                1.0
        );
        return irradianceFactor * temperatureFactor * lossFactor;
    }

    private String buildClientId(StationRuntime runtime) {
        return "sim-" + runtime.getStationDefinition().stationId();
    }

    private double clampStoragePower(StationRuntime runtime, double capacity, double rawTarget) {
        double limited = clamp(rawTarget, -capacity * 0.45, capacity * 0.45);
        return applyRamp(runtime.getActivePower(), limited, 16);
    }

    private double applyRamp(double current, double target, double rampLimit) {
        double delta = target - current;
        if (Math.abs(delta) <= rampLimit) {
            return target;
        }
        return current + Math.copySign(rampLimit, delta);
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}

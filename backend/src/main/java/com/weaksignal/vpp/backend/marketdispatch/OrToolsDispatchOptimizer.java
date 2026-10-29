package com.weaksignal.vpp.backend.marketdispatch;

import com.google.ortools.linearsolver.MPConstraint;
import com.google.ortools.linearsolver.MPObjective;
import com.google.ortools.linearsolver.MPSolver;
import com.google.ortools.linearsolver.MPVariable;
import com.weaksignal.vpp.backend.capability.ResourceCapabilitySnapshot;
import com.weaksignal.vpp.backend.capability.StorageConstraint;
import com.weaksignal.vpp.backend.config.OptimizationProperties;
import com.weaksignal.vpp.backend.application.SolverObservabilityService;
import com.weaksignal.vpp.backend.domain.LinkState;
import com.weaksignal.vpp.backend.domain.ResourceType;
import com.weaksignal.vpp.backend.forecast.ForecastPoint;
import com.weaksignal.vpp.backend.planning.DispatchContextSnapshot;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * 跨预测时间片的 OR-Tools SCIP 滚动 MILP 优化器。
 */
@Component
public class OrToolsDispatchOptimizer implements DispatchOptimizer {
    private static final double EPSILON = 0.001;
    private static final String WIND_CALM_SCENARIO = "ENV_WIND_CALM";
    private static final double THERMAL_SUPPORT_RATIO = 0.15;
    private static final double STORAGE_SUPPORT_RATIO = 0.10;
    private static final double GRID_SUPPORT_RATIO = 0.10;
    private static final double MINIMUM_OPERATING_RATIO = 0.05;
    private static final double CURRENT_POWER_RETENTION_RATIO = 0.25;
    private static final double SUPPORT_INCREASE_RATIO = 1.15;
    private final OrToolsRuntime runtime;
    private final OptimizationProperties properties;
    private final SolverObservabilityService observabilityService;

    /**
     * 注入 OR-Tools 运行时与优化配置。
     *
     * @param runtime OR-Tools 运行时
     * @param properties 优化配置
     */
    public OrToolsDispatchOptimizer(OrToolsRuntime runtime, OptimizationProperties properties, SolverObservabilityService observabilityService) {
        this.runtime = runtime;
        this.properties = properties;
        this.observabilityService = observabilityService;
    }

    /**
     * 对整个预测窗口建立一次连续时间 MILP，并返回每个时间片的不可变资源目标。
     *
     * @param context 优化上下文
     * @return 优化结果
     */
    @Override
    public OptimizationResult optimize(OptimizationContext context) {
        DispatchContextSnapshot snapshot = context.dispatchContextSnapshot();
        List<ForecastPoint> points = snapshot.forecastSnapshot().points();
        OrToolsAvailability availability = runtime.availability();
        if (!availability.solverAvailable()) {
            return audited(failure(context, OptimizationStatus.NATIVE_LOAD_FAILED, availability.failureReason(), Map.of()), snapshot, points);
        }
        long startedAt = System.nanoTime();
        try {
            if (points.isEmpty()) {
                return audited(failure(context, OptimizationStatus.INFEASIBLE, "FORECAST_POINTS_EMPTY", Map.of("category", "FORECAST")), snapshot, points);
            }
            Model model = createModel(context, points);
            MPSolver.ResultStatus solverStatus = model.solver().solve();
            if (solverStatus != MPSolver.ResultStatus.OPTIMAL && solverStatus != MPSolver.ResultStatus.FEASIBLE) {
                OptimizationStatus status = solverStatus == MPSolver.ResultStatus.NOT_SOLVED
                        ? OptimizationStatus.TIME_LIMIT_WITHOUT_SOLUTION : OptimizationStatus.INFEASIBLE;
                return audited(failure(context, status, solverStatus.name(), diagnose(snapshot, points)), snapshot, points);
            }
            OptimizationResult result = toResult(snapshot, points, model, solverStatus, startedAt);
            return audited(result, snapshot, points);
        } catch (RuntimeException exception) {
            return audited(failure(context, OptimizationStatus.SOLVER_FAILED, exception.getClass().getSimpleName(), Map.of("message", exception.getMessage())), snapshot, points);
        }
    }

    private OptimizationResult audited(OptimizationResult result, DispatchContextSnapshot snapshot, List<ForecastPoint> points) {
        observabilityService.append(result, snapshot.resourceCapabilitySnapshots().size(), points.size(),
                15 * points.size(), 5 * points.size(), 27 * points.size(), false);
        return result;
    }

    private Model createModel(OptimizationContext context, List<ForecastPoint> points) {
        DispatchContextSnapshot snapshot = context.dispatchContextSnapshot();
        MPSolver solver = MPSolver.createSolver("SCIP");
        if (solver == null) {
            throw new IllegalStateException("SCIP_MIP_SOLVER_UNAVAILABLE");
        }
        solver.setTimeLimit(properties.solverTimeLimitSeconds() * 1000L);
        solver.setSolverSpecificParametersAsString("limits/gap = " + properties.relativeMipGap());
        ResourceCapabilitySnapshot storage = capability(snapshot, ResourceType.STORAGE);
        ResourceCapabilitySnapshot grid = capability(snapshot, ResourceType.GRID);
        ResourceCapabilitySnapshot thermal = capability(snapshot, ResourceType.THERMAL);
        ResourceCapabilitySnapshot photovoltaic = capability(snapshot, ResourceType.PHOTOVOLTAIC);
        ResourceCapabilitySnapshot wind = capability(snapshot, ResourceType.WIND);
        ResourceCapabilitySnapshot load = capability(snapshot, ResourceType.LOAD);
        ResourceCapabilitySnapshot charging = capability(snapshot, ResourceType.CHARGING);
        int count = points.size();
        Variables variables = new Variables(count);
        double hours = snapshot.intervalMinutes() / 60.0;
        StorageConstraint storageConstraint = requireStorageConstraint(storage);
        boolean windFaultCoordination = WIND_CALM_SCENARIO.equals(context.experimentScenarioCode());
        double thermalSupportFloor = windFaultCoordination ? thermalSupportFloor(thermal) : 0;
        double storageSupportFloor = windFaultCoordination ? storageSupportFloor(storage, storageConstraint) : 0;
        double gridSupportFloor = windFaultCoordination
                ? gridSupportFloor(grid, snapshot.marketSignalSnapshot().gridImportLimit())
                : 0;
        boolean initialThermalOn = controllable(thermal)
                && (thermal.currentPower() > EPSILON || thermalSupportFloor > EPSILON);
        int minimumUpPeriods = periods(properties.thermalMinimumUpMinutes(), snapshot.intervalMinutes());
        int minimumDownPeriods = periods(properties.thermalMinimumDownMinutes(), snapshot.intervalMinutes());
        MPObjective objective = solver.objective();
        for (int index = 0; index < count; index++) {
            ForecastPoint point = points.get(index);
            variables.photovoltaic[index] = boundedVariable(solver, photovoltaic,
                    positiveOperatingFloor(photovoltaic, point.photovoltaicP50()), point.photovoltaicP50(), "pv_" + index);
            variables.wind[index] = boundedVariable(solver, wind,
                    positiveOperatingFloor(wind, point.windP50()), point.windP50(), "wind_" + index);
            double thermalControlUpperBound = controllable(thermal) ? Math.max(0, thermal.maximumPower()) : 0;
            double currentThermalFloor = Math.max(positiveOperatingFloor(thermal, thermalControlUpperBound),
                    index == 0 ? thermalSupportFloor : 0);
            variables.thermal[index] = solver.makeNumVar(currentThermalFloor, thermalControlUpperBound, "thermal_" + index);
            variables.thermalOn[index] = solver.makeIntVar(0, controllable(thermal) ? 1 : 0, "thermal_on_" + index);
            variables.startup[index] = solver.makeIntVar(0, controllable(thermal) ? 1 : 0, "thermal_start_" + index);
            variables.shutdown[index] = solver.makeIntVar(0, controllable(thermal) ? 1 : 0, "thermal_stop_" + index);
            double currentGridFloor = Math.max(positiveOperatingFloor(grid, snapshot.marketSignalSnapshot().gridImportLimit()),
                    index == 0 ? gridSupportFloor : 0);
            variables.gridImport[index] = boundedVariable(solver, grid, currentGridFloor,
                    snapshot.marketSignalSnapshot().gridImportLimit(), "grid_import_" + index);
            variables.gridExport[index] = boundedVariable(solver, grid, 0, snapshot.marketSignalSnapshot().gridExportLimit(), "grid_export_" + index);
            double maximumChargePower = controllable(storage) ? storageConstraint.maximumChargePowerKW() : 0;
            double maximumDischargePower = controllable(storage) ? storageConstraint.maximumDischargePowerKW() : 0;
            boolean storageCanDischarge = maximumDischargePower > EPSILON;
            double chargeFloor = storageCanDischarge ? 0 : storageOperatingFloor(storage.currentPower(), maximumChargePower);
            variables.charge[index] = solver.makeNumVar(chargeFloor, maximumChargePower, "charge_" + index);
            double currentStorageFloor = storageCanDischarge
                    ? Math.max(storageOperatingFloor(storage.currentPower(), maximumDischargePower), index == 0 ? storageSupportFloor : 0)
                    : 0;
            variables.discharge[index] = solver.makeNumVar(currentStorageFloor, maximumDischargePower, "discharge_" + index);
            variables.chargingMode[index] = solver.makeIntVar(0, 1, "charging_mode_" + index);
            variables.dischargingMode[index] = solver.makeIntVar(0, 1, "discharging_mode_" + index);
            double loadUpperBound = Math.max(load.maximumPower(), load.currentPower());
            double chargingUpperBound = Math.max(charging.maximumPower(), charging.currentPower());
            variables.loadPower[index] = boundedVariable(solver, load, positiveOperatingFloor(load, loadUpperBound), loadUpperBound, "load_power_" + index);
            variables.chargingPower[index] = boundedVariable(solver, charging, positiveOperatingFloor(charging, chargingUpperBound), chargingUpperBound, "charging_power_" + index);
            variables.flexibleReduction[index] = solver.makeNumVar(
                    0,
                    Math.max(0, load.availableCapacity() * 0.35 + charging.availableCapacity() * 0.25),
                    "flex_" + index
            );
            variables.curtailment[index] = solver.makeNumVar(0, Math.max(0, point.photovoltaicP50() + point.windP50()), "curtailment_" + index);
            variables.soc[index] = solver.makeNumVar(storageConstraint.minimumSoc(), storageConstraint.maximumSoc(), "soc_" + index);
            addLoadComposition(solver, point, variables, index);
            addRenewableAccounting(solver, point, variables, index);
            addBalance(solver, point, variables, index);
            addStorageConstraints(solver, variables, storageConstraint, hours, index);
            addThermalConstraints(solver, variables, thermal, initialThermalOn, index);
            addObjectiveTerms(objective, variables, index);
            addLoadTrackingCosts(solver, objective, variables, load, charging, index);
            if (index == 0) {
                addInitialRampConstraints(solver, variables.thermal[index], thermal);
            } else {
                addRampConstraints(solver, variables.thermal[index - 1], variables.thermal[index], thermal, index);
                addTargetChangeCosts(solver, objective, variables, index);
            }
        }
        addMinimumCommitmentConstraints(solver, variables, minimumUpPeriods, minimumDownPeriods);
        objective.minimization();
        return new Model(solver, variables, storage, grid, thermal, photovoltaic, wind, load, charging);
    }

    /**
     * 计算无风故障首个控制周期的火电支撑下限。
     *
     * @param thermal 火电资源能力
     * @return 火电支撑功率下限
     */
    private double thermalSupportFloor(ResourceCapabilitySnapshot thermal) {
        if (!controllable(thermal) || thermal.maximumPower() <= EPSILON) {
            return 0;
        }
        double increasedCurrentPower = Math.max(0, thermal.currentPower()) * SUPPORT_INCREASE_RATIO;
        double minimumSupportPower = thermal.maximumPower() * THERMAL_SUPPORT_RATIO;
        return Math.min(thermal.maximumPower(), Math.max(increasedCurrentPower, minimumSupportPower));
    }

    /**
     * 计算无风故障首个控制周期的储能放电支撑下限。
     *
     * @param storage 储能资源能力
     * @param storageConstraint 储能约束
     * @return 储能放电支撑功率下限
     */
    private double storageSupportFloor(
            ResourceCapabilitySnapshot storage,
            StorageConstraint storageConstraint
    ) {
        if (!controllable(storage) || storageConstraint.maximumDischargePowerKW() <= EPSILON) {
            return 0;
        }
        double increasedCurrentPower = Math.max(0, storage.currentPower()) * SUPPORT_INCREASE_RATIO;
        double minimumSupportPower = storageConstraint.maximumDischargePowerKW() * STORAGE_SUPPORT_RATIO;
        return Math.min(storageConstraint.maximumDischargePowerKW(), Math.max(increasedCurrentPower, minimumSupportPower));
    }

    /**
     * 计算无风故障首个控制周期的市电支撑下限。
     *
     * @param grid 市电资源能力
     * @param gridImportLimit 市场允许的最大购电功率
     * @return 市电支撑功率下限
     */
    private double gridSupportFloor(ResourceCapabilitySnapshot grid, double gridImportLimit) {
        if (!controllable(grid) || grid.maximumPower() <= EPSILON || gridImportLimit <= EPSILON) {
            return 0;
        }
        double maximumImportPower = Math.min(grid.maximumPower(), gridImportLimit);
        double increasedCurrentPower = Math.max(0, grid.currentPower()) * SUPPORT_INCREASE_RATIO;
        double minimumSupportPower = maximumImportPower * GRID_SUPPORT_RATIO;
        return Math.min(maximumImportPower, Math.max(increasedCurrentPower, minimumSupportPower));
    }

    /**
     * 计算健康资源的非零运行下限，避免调度将具备能力的设备直接置零。
     *
     * @param capability 资源能力
     * @param requestedUpper 当前变量允许的上限
     * @return 非零运行下限；资源不可控或无可用能力时返回零
     */
    private double positiveOperatingFloor(ResourceCapabilitySnapshot capability, double requestedUpper) {
        if (!controllable(capability) || requestedUpper <= EPSILON) {
            return 0;
        }
        double capabilityUpper = capability.resourceType() == ResourceType.LOAD
                || capability.resourceType() == ResourceType.CHARGING
                ? Math.max(capability.maximumPower(), capability.currentPower())
                : capability.maximumPower();
        double effectiveUpper = Math.max(0, Math.min(requestedUpper, capabilityUpper));
        if (effectiveUpper <= EPSILON) {
            return 0;
        }
        double retainedCurrentPower = Math.max(0, capability.currentPower()) * CURRENT_POWER_RETENTION_RATIO;
        double minimumOperatingPower = effectiveUpper * MINIMUM_OPERATING_RATIO;
        return Math.min(effectiveUpper, Math.max(0.1, Math.max(retainedCurrentPower, minimumOperatingPower)));
    }

    /**
     * 计算储能充电或放电方向的非零运行下限。
     *
     * @param currentPower 当前储能功率
     * @param directionUpper 当前方向最大允许功率
     * @return 当前方向运行下限；该方向不可用时返回零
     */
    private double storageOperatingFloor(double currentPower, double directionUpper) {
        if (directionUpper <= EPSILON) {
            return 0;
        }
        double retainedCurrentPower = Math.abs(currentPower) * CURRENT_POWER_RETENTION_RATIO;
        return Math.min(directionUpper,
                Math.max(0.1, Math.max(retainedCurrentPower, directionUpper * MINIMUM_OPERATING_RATIO)));
    }

    private void addLoadComposition(MPSolver solver, ForecastPoint point, Variables variables, int index) {
        MPConstraint loadComposition = solver.makeConstraint(point.loadP50(), point.loadP50(), "load_composition_" + index);
        loadComposition.setCoefficient(variables.loadPower[index], 1);
        loadComposition.setCoefficient(variables.chargingPower[index], 1);
        loadComposition.setCoefficient(variables.flexibleReduction[index], 1);
    }

    private void addBalance(MPSolver solver, ForecastPoint point, Variables variables, int index) {
        MPConstraint balance = solver.makeConstraint(0, 0, "balance_" + index);
        balance.setCoefficient(variables.photovoltaic[index], 1); balance.setCoefficient(variables.wind[index], 1);
        balance.setCoefficient(variables.thermal[index], 1); balance.setCoefficient(variables.discharge[index], 1);
        balance.setCoefficient(variables.gridImport[index], 1); balance.setCoefficient(variables.charge[index], -1);
        balance.setCoefficient(variables.gridExport[index], -1);
        balance.setCoefficient(variables.loadPower[index], -1); balance.setCoefficient(variables.chargingPower[index], -1);
    }

    /**
     * 约束新能源预测功率由实际利用功率与弃电功率组成，避免在功率平衡中重复扣减弃电量。
     *
     * @param solver 求解器
     * @param point 当前预测点
     * @param variables 优化变量
     * @param index 时段索引
     */
    private void addRenewableAccounting(MPSolver solver, ForecastPoint point, Variables variables, int index) {
        double forecastRenewablePower = Math.max(0.0, point.photovoltaicP50()) + Math.max(0.0, point.windP50());
        MPConstraint accounting = solver.makeConstraint(
                forecastRenewablePower,
                forecastRenewablePower,
                "renewable_accounting_" + index
        );
        accounting.setCoefficient(variables.photovoltaic[index], 1);
        accounting.setCoefficient(variables.wind[index], 1);
        accounting.setCoefficient(variables.curtailment[index], 1);
    }

    private void addStorageConstraints(MPSolver solver, Variables variables, StorageConstraint constraint, double hours, int index) {
        double infinity = MPSolver.infinity();
        MPConstraint chargeMutex = solver.makeConstraint(-infinity, 0, "charge_mutex_" + index);
        chargeMutex.setCoefficient(variables.charge[index], 1); chargeMutex.setCoefficient(variables.chargingMode[index], -constraint.maximumChargePowerKW());
        MPConstraint dischargeMutex = solver.makeConstraint(-infinity, 0, "discharge_mutex_" + index);
        dischargeMutex.setCoefficient(variables.discharge[index], 1); dischargeMutex.setCoefficient(variables.dischargingMode[index], -constraint.maximumDischargePowerKW());
        MPConstraint exclusive = solver.makeConstraint(-infinity, 1, "storage_exclusive_" + index);
        exclusive.setCoefficient(variables.chargingMode[index], 1); exclusive.setCoefficient(variables.dischargingMode[index], 1);
        double previousSoc = index == 0 ? constraint.soc() : 0;
        MPConstraint socTransition = solver.makeConstraint(previousSoc, previousSoc, "soc_transition_" + index);
        socTransition.setCoefficient(variables.soc[index], 1);
        if (index > 0) socTransition.setCoefficient(variables.soc[index - 1], -1);
        socTransition.setCoefficient(variables.charge[index], -hours * constraint.chargeEfficiency() * 100 / constraint.energyCapacityKWh());
        socTransition.setCoefficient(variables.discharge[index], hours * 100 / constraint.dischargeEfficiency() / constraint.energyCapacityKWh());
    }

    private void addThermalConstraints(MPSolver solver, Variables variables, ResourceCapabilitySnapshot thermal, boolean initialOn, int index) {
        double infinity = MPSolver.infinity();
        MPConstraint upper = solver.makeConstraint(-infinity, 0, "thermal_upper_" + index);
        upper.setCoefficient(variables.thermal[index], 1); upper.setCoefficient(variables.thermalOn[index], -thermal.maximumPower());
        MPConstraint lower = solver.makeConstraint(0, infinity, "thermal_lower_" + index);
        lower.setCoefficient(variables.thermal[index], 1); lower.setCoefficient(variables.thermalOn[index], -Math.max(0, thermal.minimumPower()));
        MPConstraint state = solver.makeConstraint(index == 0 ? (initialOn ? 1 : 0) : 0, index == 0 ? (initialOn ? 1 : 0) : 0, "thermal_state_" + index);
        state.setCoefficient(variables.thermalOn[index], 1);
        if (index > 0) state.setCoefficient(variables.thermalOn[index - 1], -1);
        state.setCoefficient(variables.startup[index], -1); state.setCoefficient(variables.shutdown[index], 1);
    }

    private void addRampConstraints(MPSolver solver, MPVariable previous, MPVariable current, ResourceCapabilitySnapshot thermal, int index) {
        MPConstraint upward = solver.makeConstraint(-MPSolver.infinity(), thermal.maximumRampUp(), "ramp_up_" + index);
        upward.setCoefficient(current, 1); upward.setCoefficient(previous, -1);
        MPConstraint downward = solver.makeConstraint(-MPSolver.infinity(), thermal.maximumRampDown(), "ramp_down_" + index);
        downward.setCoefficient(previous, 1); downward.setCoefficient(current, -1);
    }

    private void addInitialRampConstraints(MPSolver solver, MPVariable current, ResourceCapabilitySnapshot thermal) {
        MPConstraint upward = solver.makeConstraint(-MPSolver.infinity(), thermal.maximumRampUp(), "initial_ramp_up");
        upward.setCoefficient(current, 1);
        MPConstraint downward = solver.makeConstraint(-MPSolver.infinity(), thermal.maximumRampDown(), "initial_ramp_down");
        downward.setCoefficient(current, -1);
        upward.setBounds(-MPSolver.infinity(), thermal.currentPower() + thermal.maximumRampUp());
        downward.setBounds(-MPSolver.infinity(), thermal.maximumRampDown() - thermal.currentPower());
    }

    private void addMinimumCommitmentConstraints(MPSolver solver, Variables variables, int minimumUpPeriods, int minimumDownPeriods) {
        for (int index = 0; index < variables.thermalOn.length; index++) {
            MPConstraint minimumUp = solver.makeConstraint(-MPSolver.infinity(), 0, "minimum_up_" + index);
            for (int previous = Math.max(0, index - minimumUpPeriods + 1); previous <= index; previous++) minimumUp.setCoefficient(variables.startup[previous], 1);
            minimumUp.setCoefficient(variables.thermalOn[index], -1);
            MPConstraint minimumDown = solver.makeConstraint(-MPSolver.infinity(), 1, "minimum_down_" + index);
            for (int previous = Math.max(0, index - minimumDownPeriods + 1); previous <= index; previous++) minimumDown.setCoefficient(variables.shutdown[previous], 1);
            minimumDown.setCoefficient(variables.thermalOn[index], 1);
        }
    }

    private void addObjectiveTerms(MPObjective objective, Variables variables, int index) {
        objective.setCoefficient(variables.gridImport[index], properties.gridImportCostWeight());
        objective.setCoefficient(variables.thermal[index], properties.thermalOperationCostWeight());
        objective.setCoefficient(variables.startup[index], properties.thermalStartupCostWeight());
        objective.setCoefficient(variables.charge[index], properties.storageCycleCostWeight()); objective.setCoefficient(variables.discharge[index], properties.storageCycleCostWeight());
        objective.setCoefficient(variables.curtailment[index], properties.curtailmentCostWeight()); objective.setCoefficient(variables.flexibleReduction[index], properties.flexibleLoadCostWeight());
    }

    private void addLoadTrackingCosts(
            MPSolver solver,
            MPObjective objective,
            Variables variables,
            ResourceCapabilitySnapshot load,
            ResourceCapabilitySnapshot charging,
            int index
    ) {
        addReferenceDeviation(solver, objective, variables.loadPower[index], load.currentPower(), "load_tracking_" + index);
        addReferenceDeviation(solver, objective, variables.chargingPower[index], charging.currentPower(), "charging_tracking_" + index);
    }

    private void addReferenceDeviation(
            MPSolver solver,
            MPObjective objective,
            MPVariable variable,
            double reference,
            String name
    ) {
        MPVariable deviation = solver.makeNumVar(0, MPSolver.infinity(), name);
        MPConstraint positive = solver.makeConstraint(-MPSolver.infinity(), reference, name + "_positive");
        positive.setCoefficient(variable, 1);
        positive.setCoefficient(deviation, -1);
        MPConstraint negative = solver.makeConstraint(-MPSolver.infinity(), -reference, name + "_negative");
        negative.setCoefficient(variable, -1);
        negative.setCoefficient(deviation, -1);
        objective.setCoefficient(deviation, properties.trackingErrorCostWeight());
    }

    private void addTargetChangeCosts(MPSolver solver, MPObjective objective, Variables variables, int index) {
        addAbsoluteChange(solver, objective, variables.thermal[index - 1], variables.thermal[index], "thermal_delta_" + index);
        addAbsoluteChange(solver, objective, variables.photovoltaic[index - 1], variables.photovoltaic[index], "pv_delta_" + index);
        addAbsoluteChange(solver, objective, variables.wind[index - 1], variables.wind[index], "wind_delta_" + index);
        addAbsoluteChange(solver, objective, variables.gridImport[index - 1], variables.gridImport[index], "grid_import_delta_" + index);
        addAbsoluteChange(solver, objective, variables.gridExport[index - 1], variables.gridExport[index], "grid_export_delta_" + index);
        addAbsoluteChange(solver, objective, variables.charge[index - 1], variables.charge[index], "charge_delta_" + index);
        addAbsoluteChange(solver, objective, variables.discharge[index - 1], variables.discharge[index], "discharge_delta_" + index);
        addAbsoluteChange(solver, objective, variables.flexibleReduction[index - 1], variables.flexibleReduction[index], "flex_delta_" + index);
    }

    private void addAbsoluteChange(MPSolver solver, MPObjective objective, MPVariable previous, MPVariable current, String name) {
        MPVariable delta = solver.makeNumVar(0, MPSolver.infinity(), name);
        MPConstraint positive = solver.makeConstraint(-MPSolver.infinity(), 0, name + "_positive"); positive.setCoefficient(current, 1); positive.setCoefficient(previous, -1); positive.setCoefficient(delta, -1);
        MPConstraint negative = solver.makeConstraint(-MPSolver.infinity(), 0, name + "_negative"); negative.setCoefficient(previous, 1); negative.setCoefficient(current, -1); negative.setCoefficient(delta, -1);
        objective.setCoefficient(delta, properties.targetChangeCostWeight());
    }

    private OptimizationResult toResult(DispatchContextSnapshot snapshot, List<ForecastPoint> points, Model model, MPSolver.ResultStatus solverStatus, long startedAt) {
        List<DispatchPlanItem> items = new ArrayList<>();
        Map<String, List<ResourceDispatchTarget>> targetsByItemId = new LinkedHashMap<>();
        Map<String, String> hashes = new LinkedHashMap<>();
        OptimizationStatus status = solverStatus == MPSolver.ResultStatus.OPTIMAL
                ? OptimizationStatus.OPTIMAL : OptimizationStatus.TIME_LIMIT_WITH_FEASIBLE_SOLUTION;
        for (int index = 0; index < points.size(); index++) {
            ForecastPoint point = points.get(index);
            double netPowerTarget = rounded(
                    model.variables.photovoltaic[index].solutionValue()
                            + model.variables.wind[index].solutionValue()
                            + model.variables.thermal[index].solutionValue()
                            + model.variables.discharge[index].solutionValue()
                            - model.variables.charge[index].solutionValue()
                            + model.variables.gridImport[index].solutionValue()
                            - model.variables.gridExport[index].solutionValue()
                            - model.variables.loadPower[index].solutionValue()
                            - model.variables.chargingPower[index].solutionValue()
            );
            DispatchPlanItem item = new DispatchPlanItem("dispatch-plan-item-" + UUID.randomUUID(), snapshot.snapshotId(), point.intervalStart(), point.intervalEnd(), netPowerTarget,
                    rounded(model.variables.gridImport[index].solutionValue()), rounded(model.variables.gridExport[index].solutionValue()), rounded(model.variables.charge[index].solutionValue()), rounded(model.variables.discharge[index].solutionValue()), rounded(model.variables.flexibleReduction[index].solutionValue()), 0, 0, rounded(model.variables.curtailment[index].solutionValue()), BigDecimal.valueOf(model.solver.objective().value()).setScale(6, RoundingMode.HALF_UP), BigDecimal.ZERO, BigDecimal.ZERO, "OR_TOOLS_MPC", index);
            List<ResourceDispatchTarget> targets = targets(snapshot, item, model, index, status);
            items.add(item); targetsByItemId.put(item.itemId(), targets); hashes.put(item.itemId(), ResourceDispatchTarget.summaryHash(targets));
        }
        return new OptimizationResult(items, "OR_TOOLS_SCIP", runtime.availability().solverVersion(), status, model.solver.objective().value(), elapsedMillis(startedAt), optimalityGap(model.solver), 0, snapshot.snapshotId(), 0.0, Map.of("timePeriods", points.size(), "hardBalance", true, "variables", 20 * points.size(), "constraints", "SOC/commitment/ramp/smoothing/load-composition"), null, targetsByItemId, hashes);
    }

    private List<ResourceDispatchTarget> targets(DispatchContextSnapshot snapshot, DispatchPlanItem item, Model model, int index, OptimizationStatus status) {
        List<ResourceDispatchTarget> targets = new ArrayList<>();
        addTarget(targets, model.photovoltaic, model.variables.photovoltaic[index].solutionValue(), snapshot, item, status); addTarget(targets, model.wind, model.variables.wind[index].solutionValue(), snapshot, item, status);
        addTarget(targets, model.thermal, model.variables.thermal[index].solutionValue(), snapshot, item, status); addTarget(targets, model.storage, model.variables.discharge[index].solutionValue() - model.variables.charge[index].solutionValue(), snapshot, item, status);
        addTarget(targets, model.grid, model.variables.gridImport[index].solutionValue() - model.variables.gridExport[index].solutionValue(), snapshot, item, status);
        addTarget(targets, model.load, model.variables.loadPower[index].solutionValue(), snapshot, item, status);
        addTarget(targets, model.charging, model.variables.chargingPower[index].solutionValue(), snapshot, item, status);
        return targets.stream().sorted(java.util.Comparator.comparing(ResourceDispatchTarget::stationId)).toList();
    }

    private Map<String, Object> diagnose(DispatchContextSnapshot snapshot, List<ForecastPoint> points) {
        if (!properties.diagnosticRelaxationEnabled()) return Map.of("diagnosisEnabled", false);
        MPSolver diagnosticSolver = MPSolver.createSolver("GLOP");
        if (diagnosticSolver == null) return Map.of("diagnosisEnabled", true, "status", "DIAGNOSTIC_SOLVER_UNAVAILABLE", "executable", false);
        ResourceCapabilitySnapshot photovoltaic = capability(snapshot, ResourceType.PHOTOVOLTAIC);
        ResourceCapabilitySnapshot wind = capability(snapshot, ResourceType.WIND);
        ResourceCapabilitySnapshot thermal = capability(snapshot, ResourceType.THERMAL);
        ResourceCapabilitySnapshot storage = capability(snapshot, ResourceType.STORAGE);
        ResourceCapabilitySnapshot grid = capability(snapshot, ResourceType.GRID);
        ResourceCapabilitySnapshot load = capability(snapshot, ResourceType.LOAD);
        ResourceCapabilitySnapshot charging = capability(snapshot, ResourceType.CHARGING);
        StorageConstraint storageConstraint = requireStorageConstraint(storage);
        List<MPVariable> shortages = new ArrayList<>();
        List<MPVariable> excesses = new ArrayList<>();
        MPObjective objective = diagnosticSolver.objective();
        for (int index = 0; index < points.size(); index++) {
            ForecastPoint point = points.get(index);
            MPVariable photovoltaicPower = diagnosticVariable(diagnosticSolver, photovoltaic, point.photovoltaicP50(), "diagnostic_pv_" + index);
            MPVariable windPower = diagnosticVariable(diagnosticSolver, wind, point.windP50(), "diagnostic_wind_" + index);
            MPVariable thermalPower = diagnosticVariable(diagnosticSolver, thermal, thermal.maximumPower(), "diagnostic_thermal_" + index);
            MPVariable gridImport = diagnosticSolver.makeNumVar(0, controllable(grid) ? Math.min(grid.maximumPower(), snapshot.marketSignalSnapshot().gridImportLimit()) : 0, "diagnostic_grid_import_" + index);
            MPVariable gridExport = diagnosticSolver.makeNumVar(0, controllable(grid) ? snapshot.marketSignalSnapshot().gridExportLimit() : 0, "diagnostic_grid_export_" + index);
            MPVariable chargePower = diagnosticSolver.makeNumVar(0, controllable(storage) ? storageConstraint.maximumChargePowerKW() : 0, "diagnostic_charge_" + index);
            MPVariable dischargePower = diagnosticSolver.makeNumVar(0, controllable(storage) ? storageConstraint.maximumDischargePowerKW() : 0, "diagnostic_discharge_" + index);
            MPVariable loadPower = diagnosticVariable(diagnosticSolver, load, load.maximumPower(), "diagnostic_load_" + index);
            MPVariable chargingPower = diagnosticVariable(diagnosticSolver, charging, charging.maximumPower(), "diagnostic_charging_" + index);
            MPVariable flexibleReduction = diagnosticSolver.makeNumVar(0, Math.max(0, load.availableCapacity() * 0.35 + charging.availableCapacity() * 0.25), "diagnostic_flex_" + index);
            MPVariable shortage = diagnosticSolver.makeNumVar(0, MPSolver.infinity(), "diagnostic_shortage_" + index);
            MPVariable excess = diagnosticSolver.makeNumVar(0, MPSolver.infinity(), "diagnostic_excess_" + index);
            shortages.add(shortage); excesses.add(excess);
            MPConstraint loadComposition = diagnosticSolver.makeConstraint(point.loadP50(), point.loadP50(), "diagnostic_load_composition_" + index);
            loadComposition.setCoefficient(loadPower, 1); loadComposition.setCoefficient(chargingPower, 1); loadComposition.setCoefficient(flexibleReduction, 1);
            MPConstraint balance = diagnosticSolver.makeConstraint(0, 0, "diagnostic_balance_" + index);
            balance.setCoefficient(photovoltaicPower, 1); balance.setCoefficient(windPower, 1); balance.setCoefficient(thermalPower, 1);
            balance.setCoefficient(dischargePower, 1); balance.setCoefficient(gridImport, 1); balance.setCoefficient(shortage, 1);
            balance.setCoefficient(chargePower, -1); balance.setCoefficient(gridExport, -1); balance.setCoefficient(loadPower, -1);
            balance.setCoefficient(chargingPower, -1); balance.setCoefficient(excess, -1);
            objective.setCoefficient(shortage, 1_000_000); objective.setCoefficient(excess, 1_000_000); objective.setCoefficient(flexibleReduction, 1_000);
        }
        objective.minimization();
        MPSolver.ResultStatus diagnosticStatus = diagnosticSolver.solve();
        List<Map<String, Object>> periodEvidence = new ArrayList<>();
        double largestShortage = 0;
        if (diagnosticStatus == MPSolver.ResultStatus.OPTIMAL || diagnosticStatus == MPSolver.ResultStatus.FEASIBLE) {
            for (int index = 0; index < points.size(); index++) {
                double shortage = rounded(shortages.get(index).solutionValue());
                double excess = rounded(excesses.get(index).solutionValue());
                largestShortage = Math.max(largestShortage, shortage);
                periodEvidence.add(Map.of("intervalStart", points.get(index).intervalStart(), "shortageKw", shortage, "excessKw", excess));
            }
        }
        return Map.of("diagnosisEnabled", true, "status", diagnosticStatus.name(), "category", "RELAXED_BALANCE",
                "minimumShortageKw", rounded(largestShortage), "periods", periodEvidence, "executable", false);
    }

    private MPVariable diagnosticVariable(MPSolver solver, ResourceCapabilitySnapshot capability, double requestedMaximum, String name) {
        if (!controllable(capability)) return solver.makeNumVar(0, 0, name);
        return solver.makeNumVar(Math.max(0, capability.minimumPower()), Math.max(0, Math.min(requestedMaximum, capability.maximumPower())), name);
    }

    private ResourceCapabilitySnapshot capability(DispatchContextSnapshot snapshot, ResourceType type) { return snapshot.resourceCapabilitySnapshots().stream().filter(value -> value.resourceType() == type).findFirst().orElseThrow(() -> new IllegalArgumentException("RESOURCE_CAPABILITY_MISSING:" + type)); }
    private StorageConstraint requireStorageConstraint(ResourceCapabilitySnapshot storage) { if (storage.storageConstraint() == null) throw new IllegalArgumentException("STORAGE_CONSTRAINT_MISSING"); return storage.storageConstraint(); }
    private MPVariable boundedVariable(MPSolver solver, ResourceCapabilitySnapshot capability, double lower, double upper, String name) { if (capability.linkState() == LinkState.S3 || !capability.dataTrusted()) return solver.makeNumVar(0, 0, name); double capabilityUpper = capability.resourceType() == ResourceType.LOAD || capability.resourceType() == ResourceType.CHARGING ? Math.max(capability.maximumPower(), capability.currentPower()) : capability.maximumPower(); return solver.makeNumVar(Math.max(lower, capability.minimumPower()), Math.max(0, Math.min(upper, capabilityUpper)), name); }
    private boolean controllable(ResourceCapabilitySnapshot capability) { return capability.linkState() != LinkState.S3 && capability.dataTrusted(); }
    private void addTarget(List<ResourceDispatchTarget> targets, ResourceCapabilitySnapshot capability, double power, DispatchContextSnapshot snapshot, DispatchPlanItem item, OptimizationStatus status) { if (capability.linkState() == LinkState.S3 || !capability.dataTrusted()) return; double target = boundedRoundedTarget(power, capability, snapshot); targets.add(new ResourceDispatchTarget(capability.stationId(), capability.resourceType(), target, directionOf(target), status, 0, snapshot.snapshotId(), Map.of("optimizer", "OR_TOOLS_SCIP", "reasonCode", item.reasonCode()), snapshot.generatedAt())); }
    private double boundedRoundedTarget(double power, ResourceCapabilitySnapshot capability, DispatchContextSnapshot snapshot) { double minimum = capability.resourceType() == ResourceType.GRID ? -snapshot.marketSignalSnapshot().gridExportLimit() : capability.minimumPower(); double maximum = capability.resourceType() == ResourceType.GRID ? Math.min(capability.maximumPower(), snapshot.marketSignalSnapshot().gridImportLimit()) : capability.resourceType() == ResourceType.LOAD || capability.resourceType() == ResourceType.CHARGING ? Math.max(capability.maximumPower(), capability.currentPower()) : capability.maximumPower(); double safeMinimum = Math.ceil(minimum * 10.0 - EPSILON) / 10.0; double safeMaximum = Math.floor(maximum * 10.0 + EPSILON) / 10.0; return Math.max(safeMinimum, Math.min(rounded(power), safeMaximum)); }
    private OptimizationResult failure(OptimizationContext context, OptimizationStatus status, String reason, Map<String, Object> evidence) { return new OptimizationResult(List.of(), "OR_TOOLS_SCIP", runtime.availability().solverVersion(), status, null, 0, null, 0, context.dispatchContextSnapshot().snapshotId(), null, evidence, reason, Map.of(), Map.of()); }
    private int periods(int minutes, int intervalMinutes) { return (int) Math.ceil((double) minutes / intervalMinutes); }
    private double rounded(double value) { return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).doubleValue(); }
    private PowerDirection directionOf(double power) { return power > 0 ? PowerDirection.POSITIVE : power < 0 ? PowerDirection.NEGATIVE : PowerDirection.NEUTRAL; }
    private long elapsedMillis(long startedAt) { return (System.nanoTime() - startedAt) / 1_000_000; }
    private double optimalityGap(MPSolver solver) { double objective = solver.objective().value(); double bound = solver.objective().bestBound(); return Math.abs(objective) < 0.000001 ? 0.0 : Math.abs(objective - bound) / Math.abs(objective); }
    private record Model(MPSolver solver, Variables variables, ResourceCapabilitySnapshot storage, ResourceCapabilitySnapshot grid, ResourceCapabilitySnapshot thermal, ResourceCapabilitySnapshot photovoltaic, ResourceCapabilitySnapshot wind, ResourceCapabilitySnapshot load, ResourceCapabilitySnapshot charging) { }
    private static final class Variables { private final MPVariable[] photovoltaic; private final MPVariable[] wind; private final MPVariable[] thermal; private final MPVariable[] thermalOn; private final MPVariable[] startup; private final MPVariable[] shutdown; private final MPVariable[] gridImport; private final MPVariable[] gridExport; private final MPVariable[] charge; private final MPVariable[] discharge; private final MPVariable[] chargingMode; private final MPVariable[] dischargingMode; private final MPVariable[] loadPower; private final MPVariable[] chargingPower; private final MPVariable[] flexibleReduction; private final MPVariable[] curtailment; private final MPVariable[] soc; private Variables(int count) { photovoltaic = new MPVariable[count]; wind = new MPVariable[count]; thermal = new MPVariable[count]; thermalOn = new MPVariable[count]; startup = new MPVariable[count]; shutdown = new MPVariable[count]; gridImport = new MPVariable[count]; gridExport = new MPVariable[count]; charge = new MPVariable[count]; discharge = new MPVariable[count]; chargingMode = new MPVariable[count]; dischargingMode = new MPVariable[count]; loadPower = new MPVariable[count]; chargingPower = new MPVariable[count]; flexibleReduction = new MPVariable[count]; curtailment = new MPVariable[count]; soc = new MPVariable[count]; } }
}

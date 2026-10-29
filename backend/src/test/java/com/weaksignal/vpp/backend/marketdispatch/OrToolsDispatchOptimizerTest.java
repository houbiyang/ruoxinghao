package com.weaksignal.vpp.backend.marketdispatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.weaksignal.vpp.backend.application.SolverObservabilityService;
import com.weaksignal.vpp.backend.capability.ResourceCapabilitySnapshot;
import com.weaksignal.vpp.backend.capability.StorageConstraint;
import com.weaksignal.vpp.backend.config.OptimizationProperties;
import com.weaksignal.vpp.backend.domain.LinkState;
import com.weaksignal.vpp.backend.domain.QualityCode;
import com.weaksignal.vpp.backend.domain.ResourceType;
import com.weaksignal.vpp.backend.forecast.ForecastPoint;
import com.weaksignal.vpp.backend.forecast.ForecastSnapshot;
import com.weaksignal.vpp.backend.market.MarketSignalSnapshot;
import com.weaksignal.vpp.backend.planning.DispatchContextSnapshot;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/** OR-Tools 多资源调度模型测试。 */
class OrToolsDispatchOptimizerTest {

    /** 应输出包含充电负荷的七类资源目标，并保持落库目标净平衡。 */
    @Test
    void shouldProduceBalancedTargetsForEveryControllableResource() {
        OrToolsDispatchOptimizer optimizer = new OrToolsDispatchOptimizer(
                new OrToolsRuntime(),
                properties(),
                mock(SolverObservabilityService.class)
        );

        OptimizationResult result = optimizer.optimize(new OptimizationContext(snapshot(LinkState.S0), false));

        assertEquals(OptimizationStatus.OPTIMAL, result.status());
        DispatchPlanItem item = result.items().getFirst();
        List<ResourceDispatchTarget> targets = result.targetsByItemId().get(item.itemId());
        assertEquals(7, targets.size());
        assertTrue(targets.stream().anyMatch(target -> target.resourceType() == ResourceType.CHARGING));
        assertTrue(targets.stream().allMatch(target -> Math.abs(target.targetPower()) >= 0.1));
        double signedTargetSum = targets.stream().mapToDouble(target -> signedPower(target.resourceType(), target.targetPower())).sum();
        assertEquals(item.netPowerTarget(), signedTargetSum, 0.7);
    }

    /** S3 资源不应生成远程控制目标。 */
    @Test
    void shouldExcludeS3ResourceFromControlTargets() {
        OrToolsDispatchOptimizer optimizer = new OrToolsDispatchOptimizer(
                new OrToolsRuntime(),
                properties(),
                mock(SolverObservabilityService.class)
        );

        OptimizationResult result = optimizer.optimize(new OptimizationContext(snapshot(LinkState.S3), false));

        assertTrue(result.status() == OptimizationStatus.OPTIMAL || result.status() == OptimizationStatus.FEASIBLE);
        List<ResourceDispatchTarget> targets = result.targetsByItemId().get(result.items().getFirst().itemId());
        assertTrue(targets.stream().noneMatch(target -> target.resourceType() == ResourceType.PHOTOVOLTAIC));
    }

    /** 硬平衡不可行时应返回仅诊断松弛模型计算出的供电缺口。 */
    @Test
    void shouldReturnRelaxedDiagnosticEvidenceWhenHardModelIsInfeasible() {
        OrToolsDispatchOptimizer optimizer = new OrToolsDispatchOptimizer(
                new OrToolsRuntime(),
                properties(),
                mock(SolverObservabilityService.class)
        );

        OptimizationResult result = optimizer.optimize(new OptimizationContext(snapshotWithoutControllableSupply(), false));

        assertEquals(OptimizationStatus.INFEASIBLE, result.status(), result.toString());
        assertEquals("RELAXED_BALANCE", result.constraintEvidence().get("category"));
        assertTrue(((Number) result.constraintEvidence().get("minimumShortageKw")).doubleValue() > 0, result.constraintEvidence().toString());
        assertTrue(result.items().isEmpty());
    }

    /** 无风故障调度应降低风电，并由火电、储能和市电共同完成非零补偿。 */
    @Test
    void shouldCoordinateHealthyResourcesForWindCalmExperiment() {
        OrToolsDispatchOptimizer optimizer = new OrToolsDispatchOptimizer(
                new OrToolsRuntime(),
                properties(),
                mock(SolverObservabilityService.class)
        );

        OptimizationResult result = optimizer.optimize(
                new OptimizationContext(windCalmSnapshot(), false, "ENV_WIND_CALM")
        );

        assertEquals(OptimizationStatus.OPTIMAL, result.status(), result.toString());
        DispatchPlanItem item = result.items().getFirst();
        List<ResourceDispatchTarget> targets = result.targetsByItemId().get(item.itemId());
        assertEquals(0.0, targetPower(targets, ResourceType.WIND), 0.1);
        assertTrue(targetPower(targets, ResourceType.THERMAL) > 0);
        assertTrue(targetPower(targets, ResourceType.STORAGE) > 0);
        assertTrue(targetPower(targets, ResourceType.GRID) > 0);
        assertTrue(targetPower(targets, ResourceType.PHOTOVOLTAIC) > 0);
        assertTrue(targetPower(targets, ResourceType.LOAD) > 0);
        assertTrue(targetPower(targets, ResourceType.CHARGING) > 0);
        assertTrue(targets.stream()
                .filter(target -> target.resourceType() != ResourceType.WIND)
                .allMatch(target -> Math.abs(target.targetPower()) >= 0.1));
        double signedTargetSum = targets.stream()
                .mapToDouble(target -> signedPower(target.resourceType(), target.targetPower()))
                .sum();
        assertEquals(item.netPowerTarget(), signedTargetSum, 0.7);
    }

    private OptimizationProperties properties() {
        return new OptimizationProperties("OR_TOOLS_MILP", 10, 0.01, true, true,
                1.0, 0.8, 2.0, 0.1, 3.0, 4.0, 0.05, 0.1, 60, 60);
    }

    private DispatchContextSnapshot snapshot(LinkState photovoltaicState) {
        OffsetDateTime now = OffsetDateTime.parse("2026-07-27T18:00:00+08:00");
        ForecastPoint point = new ForecastPoint(now, now.plusHours(1), 160, 170, 180,
                70, 80, 90, 50, 60, 70, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE);
        ForecastSnapshot forecast = new ForecastSnapshot("forecast-1", 60, "test", List.of(point), now, now,
                now.plusHours(1), "TEST", QualityCode.GOOD, "v1", now);
        MarketSignalSnapshot market = new MarketSignalSnapshot("market-1", "TEST", now, now.plusHours(1), 0,
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, 300, 100, 0, 0, now, now,
                now.plusHours(1), "TEST", QualityCode.GOOD, "v1", now);
        List<ResourceCapabilitySnapshot> capabilities = List.of(
                capability("grid", ResourceType.GRID, 40, 0, 300, LinkState.S0, null),
                capability("pv", ResourceType.PHOTOVOLTAIC, 70, 0, 80, photovoltaicState, null),
                capability("wind", ResourceType.WIND, 60, 0, 70, LinkState.S0, null),
                capability("thermal", ResourceType.THERMAL, 0, 0, 200, LinkState.S0, null),
                capability("load", ResourceType.LOAD, 130, 0, 210, LinkState.S0, null),
                capability("charging", ResourceType.CHARGING, 40, 0, 130, LinkState.S0, null),
                capability("storage", ResourceType.STORAGE, 0, -80, 100, LinkState.S0,
                        new StorageConstraint(55, 10, 90, 240, 80, 100, 0.95, 0.95,
                                new BigDecimal("0.08"), 0.96, 0, 0))
        );
        return new DispatchContextSnapshot("context-1", now, now, now.plusHours(1), "TEST", QualityCode.GOOD,
                "v1", now, 60, market, forecast, capabilities);
    }

    private DispatchContextSnapshot snapshotWithoutControllableSupply() {
        DispatchContextSnapshot source = snapshot(LinkState.S3);
        List<ResourceCapabilitySnapshot> capabilities = source.resourceCapabilitySnapshots().stream()
                .map(capability -> capability.resourceType() == ResourceType.LOAD || capability.resourceType() == ResourceType.CHARGING
                        ? capability : withLinkState(capability, LinkState.S3))
                .toList();
        return new DispatchContextSnapshot(source.snapshotId(), source.generatedAt(), source.validFrom(), source.validUntil(),
                source.source(), source.qualityCode(), source.version(), source.receivedAt(), source.intervalMinutes(),
                source.marketSignalSnapshot(), source.forecastSnapshot(), capabilities);
    }

    private DispatchContextSnapshot windCalmSnapshot() {
        DispatchContextSnapshot source = snapshot(LinkState.S0);
        List<ResourceCapabilitySnapshot> capabilities = source.resourceCapabilitySnapshots().stream()
                .map(capability -> capability.resourceType() == ResourceType.WIND
                        ? capability("wind", ResourceType.WIND, 0, 0, 0, LinkState.S0, null)
                        : capability)
                .toList();
        return new DispatchContextSnapshot(source.snapshotId(), source.generatedAt(), source.validFrom(), source.validUntil(),
                source.source(), source.qualityCode(), source.version(), source.receivedAt(), source.intervalMinutes(),
                source.marketSignalSnapshot(), source.forecastSnapshot(), capabilities);
    }

    private ResourceCapabilitySnapshot withLinkState(ResourceCapabilitySnapshot source, LinkState linkState) {
        return new ResourceCapabilitySnapshot(source.capabilityId(), source.snapshotGroupId(), source.stationId(),
                source.resourceType(), source.currentPower(), source.availableCapacity(), source.minimumPower(),
                source.maximumPower(), source.maximumRampUp(), source.maximumRampDown(), source.availableEnergyKwh(),
                source.storageConstraint(), source.environmentAssessment(), linkState, source.dataAge(),
                source.capabilityConfidence(), source.dataTrusted(), source.unavailableReason(), source.generatedAt(),
                source.validFrom(), source.validUntil(), source.source(), source.qualityCode(), source.version(), source.receivedAt());
    }

    private ResourceCapabilitySnapshot capability(
            String stationId,
            ResourceType resourceType,
            double currentPower,
            double minimumPower,
            double maximumPower,
            LinkState linkState,
            StorageConstraint storageConstraint
    ) {
        OffsetDateTime now = OffsetDateTime.parse("2026-07-27T18:00:00+08:00");
        return new ResourceCapabilitySnapshot("cap-" + stationId, "group-1", stationId, resourceType,
                currentPower, maximumPower, minimumPower, maximumPower, 100, 100, maximumPower,
                storageConstraint, linkState, 0, 0.98, true, "", now, now, now.plusHours(1),
                "TEST", QualityCode.GOOD, "v1", now);
    }

    private double signedPower(ResourceType resourceType, double power) {
        return switch (resourceType) {
            case PHOTOVOLTAIC, WIND, THERMAL, GRID, STORAGE -> power;
            case LOAD, CHARGING -> -Math.abs(power);
        };
    }

    private double targetPower(List<ResourceDispatchTarget> targets, ResourceType resourceType) {
        return targets.stream()
                .filter(target -> target.resourceType() == resourceType)
                .findFirst()
                .orElseThrow()
                .targetPower();
    }
}

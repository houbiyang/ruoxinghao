package com.weaksignal.vpp.backend.marketdispatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.weaksignal.vpp.backend.capability.ResourceCapabilitySnapshot;
import com.weaksignal.vpp.backend.capability.StorageConstraint;
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

/**
 * 规则型调度优化器测试。
 */
class RuleBasedDispatchOptimizerTest {

    private final RuleBasedDispatchOptimizer optimizer = new RuleBasedDispatchOptimizer();

    @Test
    void shouldGenerateDeterministicBalancedPlanForSurplusScenario() {
        DispatchContextSnapshot snapshot = context(
                55.0,
                140.0,
                320.0,
                240.0,
                150.0,
                120.0,
                90.0,
                60.0,
                80.0,
                20.0
        );

        DispatchPlanItem firstItem = optimizer.optimize(new OptimizationContext(snapshot, false)).items().getFirst();
        DispatchPlanItem secondItem = optimizer.optimize(new OptimizationContext(snapshot, false)).items().getFirst();

        assertEquals(firstItem.netPowerTarget(), secondItem.netPowerTarget());
        assertEquals(firstItem.storageChargePower(), secondItem.storageChargePower());
        assertEquals(firstItem.gridExportPower(), secondItem.gridExportPower());
        DispatchPlanItem item = firstItem;
        assertEquals(0.0, item.netPowerTarget());
        assertTrue(item.storageChargePower() > 0.0);
        assertTrue(item.gridExportPower() >= 0.0);
        assertEquals(
                0.0,
                round(
                        150.0
                                + 120.0
                                + item.storageDischargePower()
                                + item.gridImportPower()
                                - item.storageChargePower()
                                - item.gridExportPower()
                                - 240.0
                                - item.flexibleLoadAdjustment()
                                - item.renewableCurtailment()
                )
        );
    }

    @Test
    void shouldUseDischargeAndGridImportForDeficitScenario() {
        DispatchContextSnapshot snapshot = context(
                68.0,
                140.0,
                320.0,
                260.0,
                70.0,
                90.0,
                60.0,
                40.0,
                80.0,
                20.0
        );

        DispatchPlanItem item = optimizer.optimize(new OptimizationContext(snapshot, false)).items().getFirst();

        assertTrue(item.storageDischargePower() > 0.0);
        assertTrue(item.gridImportPower() >= 0.0);
        assertEquals(0.0, item.netPowerTarget());
    }

    @Test
    void shouldLimitChargeWhenStorageSocIsNearUpperBound() {
        DispatchContextSnapshot snapshot = context(
                89.5,
                140.0,
                320.0,
                180.0,
                200.0,
                100.0,
                90.0,
                50.0,
                80.0,
                20.0
        );

        DispatchPlanItem item = optimizer.optimize(new OptimizationContext(snapshot, false)).items().getFirst();

        assertTrue(item.storageChargePower() <= 1.2);
        assertTrue(item.gridExportPower() > 0.0 || item.renewableCurtailment() > 0.0);
    }

    @Test
    void shouldLimitDischargeWhenStorageSocIsNearLowerBound() {
        DispatchContextSnapshot snapshot = context(
                10.0,
                140.0,
                320.0,
                320.0,
                60.0,
                50.0,
                20.0,
                10.0,
                220.0,
                20.0
        );

        DispatchPlanItem item = optimizer.optimize(new OptimizationContext(snapshot, false)).items().getFirst();

        assertTrue(item.storageDischargePower() <= 0.5);
        assertTrue(item.gridImportPower() > 0.0);
    }

    /** 规则回退的逐资源目标必须与计划净功率保持一致。 */
    @Test
    void shouldKeepFallbackTargetsBalancedWithPlanItem() {
        DispatchContextSnapshot snapshot = context(55, 140, 320, 240, 150, 120, 240, 80, 120, 80);

        OptimizationResult result = optimizer.optimize(new OptimizationContext(snapshot, false));
        DispatchPlanItem item = result.items().getFirst();
        double signedTargetSum = result.targetsByItemId().get(item.itemId()).stream()
                .mapToDouble(target -> switch (target.resourceType()) {
                    case PHOTOVOLTAIC, WIND, THERMAL, GRID, STORAGE -> target.targetPower();
                    case LOAD, CHARGING -> -Math.abs(target.targetPower());
                })
                .sum();

        assertEquals(item.netPowerTarget(), signedTargetSum, 0.7);
    }

    private DispatchContextSnapshot context(
            double storageSoc,
            double storageAvailableCapacity,
            double gridAvailableCapacity,
            double loadP50,
            double photovoltaicP50,
            double windP50,
            double loadAvailableCapacity,
            double chargingAvailableCapacity,
            double gridImportLimit,
            double gridExportLimit
    ) {
        OffsetDateTime now = OffsetDateTime.parse("2026-07-04T08:00:00+08:00");
        return new DispatchContextSnapshot(
                "dispatch-context-1",
                now,
                now,
                now.plusHours(1),
                "TEST_CONTEXT",
                QualityCode.GOOD,
                "dispatch-context-v1",
                now,
                60,
                new MarketSignalSnapshot(
                        "market-1",
                        "TEST_MARKET",
                        now,
                        now.plusHours(1),
                        280.0,
                        new BigDecimal("0.452000"),
                        new BigDecimal("0.486000"),
                        new BigDecimal("0.518000"),
                        gridImportLimit,
                        gridExportLimit,
                        12.0,
                        10.0,
                        now.minusMinutes(5),
                        now,
                        now.plusHours(2),
                        "TEST_MARKET_DATA",
                        QualityCode.GOOD,
                        "market-v1",
                        now.minusMinutes(4)
                ),
                new ForecastSnapshot(
                        "forecast-1",
                        60,
                        "forecast-v1",
                        List.of(new ForecastPoint(
                                now,
                                now.plusHours(1),
                                loadP50 - 10.0,
                                loadP50,
                                loadP50 + 10.0,
                                photovoltaicP50 - 10.0,
                                photovoltaicP50,
                                photovoltaicP50 + 10.0,
                                windP50 - 10.0,
                                windP50,
                                windP50 + 10.0,
                                new BigDecimal("0.438000"),
                                new BigDecimal("0.486000"),
                                new BigDecimal("0.536000")
                        )),
                        now.minusMinutes(10),
                        now,
                        now.plusHours(2),
                        "TEST_FORECAST_DATA",
                        QualityCode.GOOD,
                        "forecast-v1",
                        now.minusMinutes(9)
                ),
                List.of(
                        capability("pv-gobi-01", ResourceType.PHOTOVOLTAIC, photovoltaicP50, photovoltaicP50, 0.0, photovoltaicP50),
                        capability("wind-ridge-02", ResourceType.WIND, windP50, windP50, 0.0, windP50),
                        capability("grid-link-00", ResourceType.GRID, 0.0, gridAvailableCapacity, 0.0, gridAvailableCapacity),
                        storageCapability(storageSoc, storageAvailableCapacity),
                        capability("load-oasis-04", ResourceType.LOAD, loadP50, loadAvailableCapacity, 0.0, loadAvailableCapacity),
                        capability("charge-corridor-05", ResourceType.CHARGING, 40.0, chargingAvailableCapacity, 0.0, chargingAvailableCapacity)
                )
        );
    }

    private ResourceCapabilitySnapshot capability(
            String stationId,
            ResourceType resourceType,
            double currentPower,
            double availableCapacity,
            double minimumPower,
            double maximumPower
    ) {
        OffsetDateTime now = OffsetDateTime.parse("2026-07-04T08:00:00+08:00");
        return new ResourceCapabilitySnapshot(
                "capability-" + stationId,
                "capability-group-1",
                stationId,
                resourceType,
                currentPower,
                availableCapacity,
                minimumPower,
                maximumPower,
                maximumPower,
                maximumPower,
                availableCapacity,
                null,
                LinkState.S0,
                2L,
                0.98,
                true,
                "",
                now,
                now,
                now.plusMinutes(20),
                "REALTIME_TELEMETRY",
                QualityCode.GOOD,
                "capability-v1",
                now
        );
    }

    private ResourceCapabilitySnapshot storageCapability(double soc, double availableCapacity) {
        OffsetDateTime now = OffsetDateTime.parse("2026-07-04T08:00:00+08:00");
        return new ResourceCapabilitySnapshot(
                "capability-storage",
                "capability-group-1",
                "storage-hub-03",
                ResourceType.STORAGE,
                0.0,
                availableCapacity,
                -120.0,
                120.0,
                120.0,
                120.0,
                240.0,
                new StorageConstraint(
                        soc,
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
                now.plusMinutes(20),
                "REALTIME_TELEMETRY",
                QualityCode.GOOD,
                "capability-v1",
                now
        );
    }

    private double round(double value) {
        return BigDecimal.valueOf(value).setScale(1, java.math.RoundingMode.HALF_UP).doubleValue();
    }
}

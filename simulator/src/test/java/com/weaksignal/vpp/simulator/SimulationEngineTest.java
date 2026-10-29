package com.weaksignal.vpp.simulator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 仿真引擎测试。
 */
class SimulationEngineTest {

    @Test
    void shouldGenerateDeterministicSequenceForSameSeed() {
        SimulationConfig config = new SimulationConfig("127.0.0.1", 1883, "user", "password", 1000, 42L, DemoScenario.STABLE, List.of("pv-gobi-01"));
        SimulationEngine left = new SimulationEngine(config);
        SimulationEngine right = new SimulationEngine(config);

        double leftPower = left.nextTick().telemetryPayloads().get(0).activePower();
        double rightPower = right.nextTick().telemetryPayloads().get(0).activePower();

        assertEquals(leftPower, rightPower);
    }

    @Test
    void shouldKeepPowerWithinCapacity() {
        SimulationConfig config = new SimulationConfig("127.0.0.1", 1883, "user", "password", 1000, 43L, DemoScenario.STABLE, StationDefinition.defaultStationIds());
        SimulationEngine engine = new SimulationEngine(config);

        SimulationTick tick = engine.nextTick();
        assertTrue(tick.telemetryPayloads().stream().allMatch(payload -> Math.abs(payload.activePower()) <= payload.availableCapacity()));
    }

    /**
     * 稳定场景下所有场站都应保持非零功率，并在连续采样中产生真实波动。
     */
    @Test
    void shouldKeepEveryStationNonZeroAndFluctuatingInStableScenario() {
        SimulationConfig config = new SimulationConfig(
                "127.0.0.1", 1883, "user", "password", 1000, 430L,
                DemoScenario.STABLE, StationDefinition.defaultStationIds()
        );
        SimulationEngine engine = new SimulationEngine(config);
        Map<String, List<Double>> powerSamplesByStation = new HashMap<>();

        for (int sampleIndex = 0; sampleIndex < 40; sampleIndex += 1) {
            for (TelemetryPayload payload : engine.nextTick().telemetryPayloads()) {
                powerSamplesByStation.computeIfAbsent(payload.stationId(), ignored -> new ArrayList<>())
                        .add(payload.activePower());
            }
        }

        assertEquals(StationDefinition.defaultStationIds().size(), powerSamplesByStation.size());
        assertTrue(powerSamplesByStation.values().stream()
                .flatMap(List::stream)
                .allMatch(power -> Math.abs(power) > 0.0));
        assertTrue(powerSamplesByStation.values().stream()
                .allMatch(samples -> samples.stream().distinct().count() > 1));
    }

    /**
     * 稳定运行进入自然波动区间后，每个场站的功率极差应控制在约十五千瓦以内。
     */
    @Test
    void shouldLimitEveryStationStablePowerRangeToFifteenKilowatts() {
        SimulationConfig config = new SimulationConfig(
                "127.0.0.1", 1883, "user", "password", 1000, 434L,
                DemoScenario.STABLE, StationDefinition.defaultStationIds()
        );
        SimulationEngine engine = new SimulationEngine(config);
        Map<String, List<Double>> powerSamplesByStation = new HashMap<>();

        for (int warmupIndex = 0; warmupIndex < 30; warmupIndex += 1) {
            engine.nextTick();
        }
        for (int sampleIndex = 0; sampleIndex < 120; sampleIndex += 1) {
            for (TelemetryPayload payload : engine.nextTick().telemetryPayloads()) {
                powerSamplesByStation.computeIfAbsent(payload.stationId(), ignored -> new ArrayList<>())
                        .add(payload.activePower());
            }
        }

        assertEquals(StationDefinition.defaultStationIds().size(), powerSamplesByStation.size());
        powerSamplesByStation.forEach((stationId, samples) -> {
            double minimumPower = samples.stream().mapToDouble(Double::doubleValue).min().orElseThrow();
            double maximumPower = samples.stream().mapToDouble(Double::doubleValue).max().orElseThrow();
            double powerRange = maximumPower - minimumPower;
            assertTrue(powerRange <= 15.0, stationId + " 正常功率极差超限：" + powerRange + " kW");
        });
    }

    /**
     * 正常基线的发电、购电和储能出力应与负荷及充电需求保持小范围平衡。
     */
    @Test
    void shouldKeepStableBaselineSupplyDemandDeviationWithinReasonableRange() {
        SimulationConfig config = new SimulationConfig(
                "127.0.0.1", 1883, "user", "password", 1000, 435L,
                DemoScenario.STABLE, StationDefinition.defaultStationIds()
        );
        SimulationEngine engine = new SimulationEngine(config);

        for (int warmupIndex = 0; warmupIndex < 30; warmupIndex += 1) {
            engine.nextTick();
        }
        for (int sampleIndex = 0; sampleIndex < 120; sampleIndex += 1) {
            SimulationTick tick = engine.nextTick();
            double supplyPower = tick.telemetryPayloads().stream()
                    .filter(payload -> !"load".equals(payload.resourceType())
                            && !"charging".equals(payload.resourceType()))
                    .mapToDouble(TelemetryPayload::activePower)
                    .sum();
            double demandPower = tick.telemetryPayloads().stream()
                    .filter(payload -> "load".equals(payload.resourceType())
                            || "charging".equals(payload.resourceType()))
                    .mapToDouble(payload -> Math.abs(payload.activePower()))
                    .sum();

            double signedSupplyDemandDeviation = supplyPower - demandPower;
            double supplyDemandDeviation = Math.abs(signedSupplyDemandDeviation);
            assertTrue(supplyDemandDeviation <= 20.0,
                    "正常基线供需偏差超限：" + signedSupplyDemandDeviation + " kW");
        }
    }

    /**
     * 切回稳定场景时应释放历史零功率调度目标，恢复自然功率曲线。
     */
    @Test
    void shouldClearHistoricalZeroDispatchWhenStableScenarioStarts() {
        SimulationConfig config = new SimulationConfig(
                "127.0.0.1", 1883, "user", "password", 1000, 431L,
                DemoScenario.STABLE, List.of("wind-ridge-02")
        );
        SimulationEngine engine = new SimulationEngine(config);
        OffsetDateTime now = OffsetDateTime.now();
        DispatchControlResult result = engine.applyDispatchControl(
                new DispatchControlCommand(
                        "zero-command", "plan-zero", "dispatch-plan-v1", "item-zero",
                        "wind-ridge-02", 0.0, now.plusSeconds(30), 20.0
                ),
                now
        );

        engine.applyScenario(DemoScenario.STABLE);
        List<Double> recoveredPowerSamples = new ArrayList<>();
        for (int sampleIndex = 0; sampleIndex < 5; sampleIndex += 1) {
            recoveredPowerSamples.add(engine.nextTick().telemetryPayloads().get(0).activePower());
        }

        assertTrue(result.accepted());
        assertTrue(recoveredPowerSamples.stream().allMatch(power -> Math.abs(power) > 0.0));
        assertTrue(recoveredPowerSamples.stream().distinct().count() > 1);
    }

    @Test
    void shouldUseThermalPowerToKeepSupplyAboveDemand() {
        SimulationConfig config = new SimulationConfig(
                "127.0.0.1", 1883, "user", "password", 1000, 431L, DemoScenario.STABLE,
                List.of("thermal-plant-06", "load-oasis-04", "charge-corridor-05")
        );
        SimulationEngine engine = new SimulationEngine(config);

        SimulationTick tick = engine.nextTick();
        double demandPower = tick.telemetryPayloads().stream()
                .filter(payload -> "load".equals(payload.resourceType()) || "charging".equals(payload.resourceType()))
                .mapToDouble(TelemetryPayload::activePower)
                .sum();
        double supplyPower = tick.telemetryPayloads().stream()
                .filter(payload -> !"load".equals(payload.resourceType()) && !"charging".equals(payload.resourceType()))
                .mapToDouble(payload -> Math.max(0.0, payload.activePower()))
                .sum();
        double thermalPower = tick.telemetryPayloads().stream()
                .filter(payload -> "thermal".equals(payload.resourceType()))
                .mapToDouble(TelemetryPayload::activePower)
                .findFirst()
                .orElse(0.0);

        assertTrue(thermalPower > 0.0);
        assertTrue(supplyPower > demandPower);
    }

    @Test
    void shouldKeepExplicitThermalDispatchTargetWithoutReserveOverride() {
        SimulationConfig config = new SimulationConfig(
                "127.0.0.1", 1883, "user", "password", 1000, 432L, DemoScenario.STABLE,
                List.of("thermal-plant-06", "load-oasis-04", "charge-corridor-05")
        );
        SimulationEngine engine = new SimulationEngine(config);
        OffsetDateTime now = OffsetDateTime.now();

        DispatchControlResult result = engine.applyDispatchControl(
                new DispatchControlCommand("thermal-command-1", "plan-1", "dispatch-plan-v1", "item-1",
                        "thermal-plant-06", 0.0, now.plusSeconds(30), 500.0),
                now
        );
        double thermalPower = engine.nextTick().telemetryPayloads().stream()
                .filter(payload -> "thermal".equals(payload.resourceType()))
                .mapToDouble(TelemetryPayload::activePower)
                .findFirst()
                .orElseThrow();

        assertTrue(result.accepted());
        assertEquals(0.0, thermalPower);
    }

    /**
     * 边缘协同模式下电网联络线应双向补齐周期内的实际供需偏差。
     */
    @Test
    void shouldUseEdgeCoordinatedGridToBalanceActualPower() {
        SimulationConfig config = new SimulationConfig(
                "127.0.0.1", 1883, "user", "password", 1000, 433L, DemoScenario.STABLE,
                StationDefinition.defaultStationIds()
        );
        SimulationEngine engine = new SimulationEngine(config);
        OffsetDateTime now = OffsetDateTime.now();

        DispatchControlResult result = engine.applyDispatchControl(
                new DispatchControlCommand("grid-command-1", "plan-1", "dispatch-plan-v1", "item-1",
                        "grid-link-00", 0.0, now.plusSeconds(30), 300.0, "edge-coordination"),
                now
        );
        SimulationTick tick = engine.nextTick();
        double demandPower = tick.telemetryPayloads().stream()
                .filter(payload -> "load".equals(payload.resourceType()) || "charging".equals(payload.resourceType()))
                .mapToDouble(payload -> Math.abs(payload.activePower()))
                .sum();
        double supplyPower = tick.telemetryPayloads().stream()
                .filter(payload -> !"load".equals(payload.resourceType()) && !"charging".equals(payload.resourceType()))
                .mapToDouble(TelemetryPayload::activePower)
                .sum();

        assertTrue(result.accepted());
        assertEquals(demandPower, supplyPower, 0.001);
    }

    @Test
    void shouldKeepStorageSocWithinBounds() {
        SimulationConfig config = new SimulationConfig("127.0.0.1", 1883, "user", "password", 1000, 44L, DemoScenario.STABLE, List.of("storage-hub-03"));
        SimulationEngine engine = new SimulationEngine(config);

        for (int index = 0; index < 20; index += 1) {
            engine.nextTick();
        }

        double soc = engine.nextTick().telemetryPayloads().get(0).metrics().get("soc");
        assertTrue(soc >= 10 && soc <= 90);
    }

    @Test
    void shouldSwitchToLostScenario() {
        SimulationConfig config = new SimulationConfig("127.0.0.1", 1883, "user", "password", 1000, 45L, DemoScenario.STABLE, StationDefinition.defaultStationIds());
        SimulationEngine engine = new SimulationEngine(config);
        engine.applyScenario(DemoScenario.LOST);

        SimulationTick tick = engine.nextTick();
        assertTrue(tick.telemetryPayloads().stream().allMatch(payload -> List.of("S3").contains(payload.linkState())));
    }

    @Test
    void shouldIncreaseSequenceNumberMonotonically() {
        SimulationConfig config = new SimulationConfig("127.0.0.1", 1883, "user", "password", 1000, 46L, DemoScenario.STABLE, List.of("wind-ridge-02"));
        SimulationEngine engine = new SimulationEngine(config);

        long first = engine.nextTick().telemetryPayloads().get(0).sequenceNumber();
        long second = engine.nextTick().telemetryPayloads().get(0).sequenceNumber();

        assertTrue(second > first);
    }

    @Test
    void shouldRampDuringRecoveringScenario() {
        SimulationConfig config = new SimulationConfig("127.0.0.1", 1883, "user", "password", 1000, 47L, DemoScenario.LOST, List.of("load-oasis-04"));
        SimulationEngine engine = new SimulationEngine(config);
        engine.nextTick();
        engine.applyScenario(DemoScenario.RECOVERING);

        double first = engine.nextTick().telemetryPayloads().get(0).activePower();
        double second = engine.nextTick().telemetryPayloads().get(0).activePower();

        assertTrue(Math.abs(second - first) <= 24);
    }

    @Test
    void shouldIncreaseLatencyForLatencyJitterExperiment() {
        SimulationConfig config = new SimulationConfig("127.0.0.1", 1883, "user", "password", 1000, 48L, DemoScenario.STABLE, List.of("pv-gobi-01"));
        SimulationEngine engine = new SimulationEngine(config);
        engine.applyExperiment(new ExperimentCommand("START_EXPERIMENT", "T1_LATENCY_JITTER", "run-1", DemoScenario.DEGRADED, List.of("pv-gobi-01"), java.util.Map.of(), 3, 120, true));

        int latency = engine.nextTick().telemetryPayloads().get(0).latency();

        assertTrue(latency >= 120);
    }

    @Test
    void shouldOnlyChangeTargetCommunicationDataForLatencyExperiment() {
        SimulationConfig config = new SimulationConfig("127.0.0.1", 1883, "user", "password", 1000, 481L, DemoScenario.STABLE, List.of("pv-gobi-01"));
        SimulationEngine baseline = new SimulationEngine(config);
        SimulationEngine latency = new SimulationEngine(config);
        latency.applyExperiment(new ExperimentCommand(
                "START_EXPERIMENT", "COM_LATENCY", "run-latency", DemoScenario.STABLE, List.of("pv-gobi-01"),
                java.util.Map.of("latency", 800.0, "jitter", 120.0), 4, 120, true
        ));

        TelemetryPayload baselinePayload = baseline.nextTick().telemetryPayloads().getFirst();
        TelemetryPayload latencyPayload = latency.nextTick().telemetryPayloads().getFirst();

        assertEquals(baselinePayload.activePower(), latencyPayload.activePower());
        assertTrue(latencyPayload.latency() >= 800);
        assertEquals("COM_LATENCY", latencyPayload.activeFaultCode());
    }

    @Test
    void shouldEmitEnvironmentMeasurementsAndReducePhotovoltaicPowerForOvercast() {
        SimulationConfig config = new SimulationConfig("127.0.0.1", 1883, "user", "password", 1000, 482L, DemoScenario.STABLE, List.of("pv-gobi-01"));
        SimulationEngine engine = new SimulationEngine(config);
        engine.applyExperiment(new ExperimentCommand(
                "START_EXPERIMENT", "ENV_OVERCAST", "run-overcast", DemoScenario.STABLE, List.of("pv-gobi-01"),
                java.util.Map.of("irradiance", 160.0, "cloudCover", 85.0), 4, 120, true
        ));

        TelemetryPayload payload = engine.nextTick().telemetryPayloads().getFirst();

        assertEquals(160.0, payload.metrics().get("irradiance"));
        assertEquals(85.0, payload.metrics().get("cloudCover"));
        assertTrue(payload.availableCapacity() <= 27.0);
    }

    @Test
    void shouldReduceWindPowerAndAvailableCapacityForCalmWind() {
        SimulationConfig config = new SimulationConfig("127.0.0.1", 1883, "user", "password", 1000, 4821L, DemoScenario.STABLE, List.of("wind-ridge-02"));
        SimulationEngine engine = new SimulationEngine(config);
        engine.applyExperiment(new ExperimentCommand(
                "START_EXPERIMENT", "ENV_WIND_CALM", "run-calm", DemoScenario.STABLE, List.of("wind-ridge-02"),
                java.util.Map.of("windSpeed", 0.8, "turbulence", 0.06), 4, 120, true
        ));

        SimulationTick firstTick = engine.nextTick();
        SimulationTick secondTick = engine.nextTick();
        TelemetryPayload payload = secondTick.telemetryPayloads().getFirst();

        assertEquals(0.8, payload.metrics().get("windSpeed"));
        assertEquals(0.06, payload.metrics().get("turbulence"));
        assertEquals(0.0, payload.availableCapacity());
        assertEquals(0.0, firstTick.telemetryPayloads().getFirst().activePower());
        assertEquals(0.0, payload.activePower());
    }

    @Test
    void shouldFollowPiecewiseCubicWindPowerCurveAtBoundaries() {
        assertEquals(0.0, windCapacityAt(2.9));
        assertEquals(0.0, windCapacityAt(3.0));
        double expectedMidpointCapacity = 220.0 * (Math.pow(8.0, 3) - Math.pow(3.0, 3))
                / (Math.pow(12.0, 3) - Math.pow(3.0, 3));
        assertEquals(expectedMidpointCapacity, windCapacityAt(8.0), 0.1);
        assertEquals(220.0, windCapacityAt(12.0));
        assertEquals(220.0, windCapacityAt(25.0));
        assertEquals(0.0, windCapacityAt(25.1));
    }

    @Test
    void shouldApplyCompletePhotovoltaicEnvironmentFormulaWithoutCloudDoubleDerating() {
        SimulationConfig config = new SimulationConfig("127.0.0.1", 1883, "user", "password", 1000, 4822L,
                DemoScenario.STABLE, List.of("pv-gobi-01"));
        SimulationEngine engine = new SimulationEngine(config);
        engine.applyExperiment(new ExperimentCommand(
                "START_EXPERIMENT", "ENV_OVERCAST", "run-pv-formula", DemoScenario.STABLE, List.of("pv-gobi-01"),
                java.util.Map.of(
                        "irradiance", 500.0,
                        "cloudCover", 0.95,
                        "moduleTemperature", 50.0,
                        "conversionEfficiency", 0.9,
                        "soilingLossRate", 0.1,
                        "shadingLossRate", 0.05,
                        "snowCover", 0.1
                ),
                4,
                120,
                true
        ));

        TelemetryPayload payload = engine.nextTick().telemetryPayloads().getFirst();
        double expectedCapacity = 168.0 * 0.5 * 0.9 * (0.9 * (1.0 - 0.1 - 0.05 - 0.7 * 0.1));

        assertEquals(expectedCapacity, payload.availableCapacity(), 0.1);
        assertTrue(payload.activePower() <= payload.availableCapacity());
    }

    @Test
    void shouldApplyStorageClusterAndStateOfHealthCapacityReduction() {
        SimulationConfig config = new SimulationConfig("127.0.0.1", 1883, "user", "password", 1000, 4823L,
                DemoScenario.STABLE, List.of("storage-hub-03"));
        SimulationEngine engine = new SimulationEngine(config);
        engine.applyExperiment(new ExperimentCommand(
                "START_EXPERIMENT", "DEV_STORAGE", "run-storage", DemoScenario.STABLE, List.of("storage-hub-03"),
                java.util.Map.of("onlineClusterCount", 4.0, "remainingSoc", 42.0), 4, 120, true
        ));

        TelemetryPayload payload = engine.nextTick().telemetryPayloads().getFirst();

        assertEquals(160.0 * 4.0 / 8.0 * 0.96, payload.availableCapacity(), 0.1);
        assertTrue(payload.metrics().get("soc") >= 10.0 && payload.metrics().get("soc") <= 90.0);
        assertEquals("S0", payload.linkState());
    }

    @Test
    void shouldReduceDeviceCapacityWithoutChangingCommunicationState() {
        SimulationConfig config = new SimulationConfig("127.0.0.1", 1883, "user", "password", 1000, 483L, DemoScenario.STABLE, List.of("pv-gobi-01"));
        SimulationEngine engine = new SimulationEngine(config);
        engine.applyExperiment(new ExperimentCommand(
                "START_EXPERIMENT", "DEV_INVERTER", "run-inverter", DemoScenario.STABLE, List.of("pv-gobi-01"),
                java.util.Map.of("deratingRate", 0.45), 4, 120, true
        ));

        TelemetryPayload payload = engine.nextTick().telemetryPayloads().getFirst();

        assertTrue(payload.online());
        assertEquals("S0", payload.linkState());
        assertEquals(0.45, payload.metrics().get("deratingRate"));
        assertTrue(payload.availableCapacity() < 120.0);
    }

    private double windCapacityAt(double windSpeed) {
        SimulationConfig config = new SimulationConfig("127.0.0.1", 1883, "user", "password", 1000, 4824L,
                DemoScenario.STABLE, List.of("wind-ridge-02"));
        SimulationEngine engine = new SimulationEngine(config);
        engine.applyExperiment(new ExperimentCommand(
                "START_EXPERIMENT", "ENV_WIND_CALM", "run-wind-boundary", DemoScenario.STABLE,
                List.of("wind-ridge-02"), java.util.Map.of("windSpeed", windSpeed, "turbulence", 0.06),
                4, 120, true
        ));
        return engine.nextTick().telemetryPayloads().getFirst().availableCapacity();
    }

    @Test
    void shouldDropTelemetryForRandomPacketLossExperiment() {
        SimulationConfig config = new SimulationConfig("127.0.0.1", 1883, "user", "password", 1000, 49L, DemoScenario.STABLE, List.of("wind-ridge-02"));
        SimulationEngine engine = new SimulationEngine(config);
        engine.applyExperiment(new ExperimentCommand("START_EXPERIMENT", "T2_RANDOM_PACKET_LOSS", "run-2", DemoScenario.DEGRADED, List.of("wind-ridge-02"), java.util.Map.of(), 3, 120, true));

        int telemetryCount = 0;
        for (int index = 0; index < 4; index += 1) {
            telemetryCount += engine.nextTick().telemetryPayloads().size();
        }

        assertEquals(3, telemetryCount);
    }

    @Test
    void shouldCreateContinuousBurstSequenceGapsForPacketLossExperiment() {
        SimulationConfig config = new SimulationConfig("127.0.0.1", 1883, "user", "password", 1000, 491L, DemoScenario.STABLE, List.of("wind-ridge-02"));
        SimulationEngine engine = new SimulationEngine(config);
        engine.applyExperiment(new ExperimentCommand(
                "START_EXPERIMENT", "COM_PACKET_LOSS", "run-packet-loss", DemoScenario.STABLE, List.of("wind-ridge-02"),
                java.util.Map.of("packetLossRate", 35.0, "burstLength", 6.0), 4, 120, true
        ));

        java.util.List<Long> receivedSequences = new java.util.ArrayList<>();
        for (int index = 0; index < 25; index += 1) {
            engine.nextTick().telemetryPayloads().forEach(payload -> receivedSequences.add(payload.sequenceNumber()));
        }

        assertTrue(receivedSequences.getFirst() > 1);
        assertTrue(receivedSequences.stream().anyMatch(sequence -> sequence > receivedSequences.getFirst()));
    }

    @Test
    void shouldDelayOnlyTargetControlAcknowledgementsDuringLatencyExperiment() {
        SimulationConfig config = new SimulationConfig("127.0.0.1", 1883, "user", "password", 1000, 492L, DemoScenario.STABLE, StationDefinition.defaultStationIds());
        SimulationEngine engine = new SimulationEngine(config);
        engine.applyExperiment(new ExperimentCommand(
                "START_EXPERIMENT", "COM_LATENCY", "run-latency", DemoScenario.STABLE, List.of("wind-ridge-02"),
                java.util.Map.of("latency", 800.0), 4, 120, true
        ));

        assertEquals(800, engine.getControlAcknowledgementDelayMillis("wind-ridge-02"));
        assertEquals(0, engine.getControlAcknowledgementDelayMillis("pv-gobi-01"));
        assertTrue(engine.isControlAcknowledgementDelayActive("wind-ridge-02"));
        engine.stopExperiment("run-latency");
        assertTrue(!engine.isControlAcknowledgementDelayActive("wind-ridge-02"));
    }

    @Test
    void shouldExposeDeterministicCommunicationDelayForOutboundQueue() {
        SimulationConfig config = new SimulationConfig("127.0.0.1", 1883, "user", "password", 1000, 4921L,
                DemoScenario.STABLE, StationDefinition.defaultStationIds());
        SimulationEngine firstEngine = new SimulationEngine(config);
        SimulationEngine secondEngine = new SimulationEngine(config);
        ExperimentCommand command = new ExperimentCommand(
                "START_EXPERIMENT", "COM_COMBINED", "run-delay-queue", DemoScenario.STABLE,
                List.of("pv-gobi-01"), java.util.Map.of("latency", 800.0, "jitter", 120.0),
                4, 120, true
        );
        firstEngine.applyExperiment(command);
        secondEngine.applyExperiment(command);
        firstEngine.nextTick();
        secondEngine.nextTick();

        long firstDelay = firstEngine.getCommunicationDelayMillis("pv-gobi-01");
        long secondDelay = secondEngine.getCommunicationDelayMillis("pv-gobi-01");

        assertTrue(firstDelay >= 800L && firstDelay <= 920L);
        assertEquals(firstDelay, secondDelay);
        assertEquals(firstDelay, firstEngine.getControlAcknowledgementDelayMillis("pv-gobi-01"));
        assertEquals(0L, firstEngine.getCommunicationDelayMillis("wind-ridge-02"));
    }

    @Test
    void shouldContinuePublishingTelemetryDuringAutomaticRecovery() {
        SimulationConfig config = new SimulationConfig("127.0.0.1", 1883, "user", "password", 1000, 493L, DemoScenario.STABLE, List.of("wind-ridge-02"));
        SimulationEngine engine = new SimulationEngine(config);
        engine.applyExperiment(new ExperimentCommand(
                "START_EXPERIMENT", "COM_LATENCY", "run-recovery", DemoScenario.STABLE, List.of("wind-ridge-02"),
                java.util.Map.of("latency", 800.0), 1, 1, true
        ));

        for (int index = 0; index < 10; index += 1) {
            engine.nextTick();
        }

        SimulationTick recoveredTick = engine.nextTick();
        assertEquals("S0", recoveredTick.telemetryPayloads().getFirst().linkState());
    }

    @Test
    void shouldConfirmStableRecoveryWhenExperimentContextIsMissing() {
        SimulationConfig config = new SimulationConfig(
                "127.0.0.1",
                1883,
                "user",
                "password",
                1000,
                4930L,
                DemoScenario.STABLE,
                List.of("wind-ridge-02")
        );
        SimulationEngine engine = new SimulationEngine(config);
        engine.applyScenario(DemoScenario.DEGRADED);

        ScenarioReportPayload report = engine.stopOrConfirmStable(
                "run-recovery-confirmation",
                "COM_LATENCY",
                List.of("wind-ridge-02")
        );

        assertEquals("RECOVERED", report.status());
        assertEquals(DemoScenario.STABLE, engine.getActiveScenario());
    }

    @Test
    void shouldKeepNonTargetStationsStableDuringTargetRecovery() {
        SimulationConfig config = new SimulationConfig("127.0.0.1", 1883, "user", "password", 1000, 4931L,
                DemoScenario.STABLE, StationDefinition.defaultStationIds());
        SimulationEngine engine = new SimulationEngine(config);
        engine.applyExperiment(new ExperimentCommand(
                "START_EXPERIMENT", "COM_OFFLINE", "run-isolated-recovery", DemoScenario.STABLE,
                List.of("wind-ridge-02"), java.util.Map.of(), 4, 120, false
        ));
        engine.nextTick();
        engine.stopExperiment("run-isolated-recovery");

        SimulationTick recoveryTick = engine.nextTick();
        TelemetryPayload targetPayload = recoveryTick.telemetryPayloads().stream()
                .filter(payload -> payload.stationId().equals("wind-ridge-02"))
                .findFirst()
                .orElseThrow();
        TelemetryPayload nonTargetPayload = recoveryTick.telemetryPayloads().stream()
                .filter(payload -> payload.stationId().equals("pv-gobi-01"))
                .findFirst()
                .orElseThrow();

        assertEquals("S4", targetPayload.linkState());
        assertEquals("S0", nonTargetPayload.linkState());
        assertEquals(null, nonTargetPayload.activeFaultCode());
    }

    @Test
    void shouldStopHeartbeatAndTelemetryForFullLossExperiment() {
        SimulationConfig config = new SimulationConfig("127.0.0.1", 1883, "user", "password", 1000, 50L, DemoScenario.STABLE, List.of("pv-gobi-01"));
        SimulationEngine engine = new SimulationEngine(config);
        engine.applyExperiment(new ExperimentCommand("START_EXPERIMENT", "T5_FULL_LOSS_AUTONOMY", "run-3", DemoScenario.LOST, List.of("pv-gobi-01"), java.util.Map.of(), 3, 120, true));

        SimulationTick tick = engine.nextTick();

        assertEquals(0, tick.heartbeatPayloads().size());
        assertEquals(0, tick.telemetryPayloads().size());
    }

    @Test
    void shouldRecoverPredictablyForRecoveryExperiment() {
        SimulationConfig config = new SimulationConfig("127.0.0.1", 1883, "user", "password", 1000, 51L, DemoScenario.STABLE, List.of("pv-gobi-01"));
        SimulationEngine engine = new SimulationEngine(config);
        engine.applyExperiment(new ExperimentCommand("START_EXPERIMENT", "T9_RECOVERY_SYNC", "run-4", DemoScenario.RECOVERING, List.of("pv-gobi-01"), java.util.Map.of(), 3, 120, true));

        String firstState = engine.nextTick().telemetryPayloads().get(0).linkState();
        engine.nextTick();
        String thirdState = engine.nextTick().telemetryPayloads().get(0).linkState();

        assertEquals("S3", firstState);
        assertEquals("S4", thirdState);
    }

    @Test
    void shouldAcceptValidDispatchAndRampTowardTarget() {
        SimulationConfig config = new SimulationConfig("127.0.0.1", 1883, "user", "password", 1000, 52L, DemoScenario.STABLE, List.of("pv-gobi-01"));
        SimulationEngine engine = new SimulationEngine(config);
        OffsetDateTime now = OffsetDateTime.now();
        DispatchControlResult result = engine.applyDispatchControl(
                new DispatchControlCommand("command-1", "plan-1", "dispatch-plan-v1", "item-1", "pv-gobi-01", 20, now.plusSeconds(30), 5),
                now
        );

        double first = engine.nextTick().telemetryPayloads().get(0).activePower();
        double second = engine.nextTick().telemetryPayloads().get(0).activePower();

        assertTrue(result.accepted());
        assertTrue(Math.abs(second - first) <= 5);
        assertTrue(second < first);
    }

    @Test
    void shouldRejectExpiredAndOverCapacityDispatch() {
        SimulationConfig config = new SimulationConfig("127.0.0.1", 1883, "user", "password", 1000, 53L, DemoScenario.STABLE, List.of("wind-ridge-02"));
        SimulationEngine engine = new SimulationEngine(config);
        OffsetDateTime now = OffsetDateTime.now();

        DispatchControlResult expired = engine.applyDispatchControl(
                new DispatchControlCommand("command-2", "plan-1", "dispatch-plan-v1", "item-1", "wind-ridge-02", 100, now.minusSeconds(1), 5),
                now
        );
        DispatchControlResult overCapacity = engine.applyDispatchControl(
                new DispatchControlCommand("command-3", "plan-1", "dispatch-plan-v1", "item-1", "wind-ridge-02", 500, now.plusSeconds(30), 5),
                now
        );

        assertTrue(!expired.accepted());
        assertTrue(!overCapacity.accepted());
    }

    @Test
    void shouldResumeNaturalBaselineAfterDispatchControlIsReleased() {
        SimulationConfig config = new SimulationConfig(
                "127.0.0.1",
                1883,
                "user",
                "password",
                1000,
                531L,
                DemoScenario.STABLE,
                List.of("wind-ridge-02")
        );
        SimulationEngine engine = new SimulationEngine(config);
        OffsetDateTime now = OffsetDateTime.now();
        DispatchControlResult result = engine.applyDispatchControl(
                new DispatchControlCommand(
                        "command-release",
                        "plan-release",
                        "dispatch-plan-v1",
                        "item-release",
                        "wind-ridge-02",
                        30.0,
                        now.plusSeconds(30),
                        5.0
                ),
                now
        );
        engine.nextTick();

        boolean released = engine.releaseDispatchControl("wind-ridge-02", "plan-release");
        double resumedPower = engine.nextTick().telemetryPayloads().getFirst().activePower();

        assertTrue(result.accepted());
        assertTrue(released);
        assertTrue(Math.abs(resumedPower - 30.0) > 0.1);
    }

    @Test
    void shouldRejectDispatchDuringLostScenario() {
        SimulationConfig config = new SimulationConfig("127.0.0.1", 1883, "user", "password", 1000, 54L, DemoScenario.LOST, List.of("pv-gobi-01"));
        SimulationEngine engine = new SimulationEngine(config);
        OffsetDateTime now = OffsetDateTime.now();

        DispatchControlResult result = engine.applyDispatchControl(
                new DispatchControlCommand("command-4", "plan-1", "dispatch-plan-v1", "item-1", "pv-gobi-01", 20, now.plusSeconds(30), 5),
                now
        );

        assertTrue(!result.accepted());
    }

    @Test
    void shouldExecuteLocalAutonomyAndRejoinAfterThreeRecoverySamples() {
        SimulationConfig config = new SimulationConfig(
                "127.0.0.1",
                1883,
                "user",
                "password",
                1000,
                541L,
                DemoScenario.STABLE,
                List.of("wind-ridge-02")
        );
        SimulationEngine engine = new SimulationEngine(config);
        OffsetDateTime now = OffsetDateTime.now();
        DispatchControlResult baselineDispatch = engine.applyDispatchControl(
                new DispatchControlCommand(
                        "command-autonomy-baseline",
                        "plan-autonomy-baseline",
                        "dispatch-plan-v1",
                        "item-autonomy-baseline",
                        "wind-ridge-02",
                        30.0,
                        now.plusSeconds(30),
                        5.0
                ),
                now
        );
        assertTrue(baselineDispatch.accepted());
        engine.nextTick();
        engine.applyExperiment(new ExperimentCommand(
                "START_EXPERIMENT",
                "COM_OFFLINE",
                "run-local-autonomy",
                DemoScenario.STABLE,
                List.of("wind-ridge-02"),
                Map.of(),
                4,
                120,
                false,
                "ADAPTIVE",
                101L
        ));

        SimulationTick offlineTick = engine.nextTick();
        DispatchControlResult rejectedDuringAutonomy = engine.applyDispatchControl(
                new DispatchControlCommand(
                        "command-autonomy-rejected",
                        "plan-autonomy-rejected",
                        "dispatch-plan-v2",
                        "item-autonomy-rejected",
                        "wind-ridge-02",
                        35.0,
                        now.plusSeconds(30),
                        5.0
                ),
                now.plusSeconds(1)
        );

        assertTrue(offlineTick.telemetryPayloads().isEmpty());
        assertTrue(!rejectedDuringAutonomy.accepted());
        engine.stopExperiment("run-local-autonomy");
        ScenarioReportPayload duplicateStop = engine.stopOrConfirmStable(
                "run-local-autonomy",
                "COM_OFFLINE",
                List.of("wind-ridge-02")
        );
        TelemetryPayload firstRecovery = engine.nextTick().telemetryPayloads().getFirst();
        TelemetryPayload secondRecovery = engine.nextTick().telemetryPayloads().getFirst();
        TelemetryPayload thirdRecovery = engine.nextTick().telemetryPayloads().getFirst();

        assertEquals("S4", firstRecovery.linkState());
        assertEquals("RECOVERY_STARTED", duplicateStop.status());
        assertEquals(1.0, firstRecovery.metrics().get("localAutonomyActive"));
        assertEquals(30.0, firstRecovery.metrics().get("localAutonomyReferencePower"));
        assertEquals(2.0, secondRecovery.metrics().get("recoveryStableSampleCount"));
        assertEquals(0.0, thirdRecovery.metrics().get("localAutonomyActive"));
        assertEquals(1.0, thirdRecovery.metrics().get("rejoinedCommunicationGraph"));
        assertEquals(0.0, thirdRecovery.metrics().get("localAutonomyConstraintViolationCount"));
        assertTrue(thirdRecovery.metrics().get("localAutonomyActionCount") >= 3.0);
    }

    @Test
    void shouldExecuteAndDeduplicateFaultAction() {
        SimulationConfig config = new SimulationConfig("127.0.0.1", 1883, "user", "password", 1000, 55L, DemoScenario.STABLE, List.of("pv-gobi-01"));
        SimulationEngine engine = new SimulationEngine(config);
        OffsetDateTime now = OffsetDateTime.now();
        FaultActionCommand command = new FaultActionCommand(
                "action-1", "run-1", "pv-gobi-01", FaultActionType.ENTER_LOCAL_AUTONOMY,
                now, now.plusSeconds(30)
        );

        FaultActionResult first = engine.applyFaultAction(command, now);
        FaultActionResult duplicate = engine.applyFaultAction(command, now.plusSeconds(1));
        DispatchControlResult dispatchResult = engine.applyDispatchControl(
                new DispatchControlCommand("command-5", "plan-1", "dispatch-plan-v1", "item-1", "pv-gobi-01", 30, now.plusSeconds(30), 5),
                now.plusSeconds(1)
        );

        assertEquals(first, duplicate);
        assertTrue(first.accepted());
        assertTrue(!dispatchResult.accepted());
    }

    @Test
    void shouldRejectExpiredFaultAction() {
        SimulationConfig config = new SimulationConfig("127.0.0.1", 1883, "user", "password", 1000, 56L, DemoScenario.STABLE, List.of("pv-gobi-01"));
        SimulationEngine engine = new SimulationEngine(config);
        OffsetDateTime now = OffsetDateTime.now();

        FaultActionResult result = engine.applyFaultAction(
                new FaultActionCommand("action-2", "run-2", "pv-gobi-01", FaultActionType.LIMIT_CONTROL_RAMP,
                        now.minusSeconds(2), now.minusSeconds(1)),
                now
        );

        assertTrue(!result.accepted());
    }

    @Test
    void shouldApplyTelemetryRateLimitAndRecovery() {
        SimulationConfig config = new SimulationConfig("127.0.0.1", 1883, "user", "password", 1000, 57L, DemoScenario.STABLE, List.of("pv-gobi-01"));
        SimulationEngine engine = new SimulationEngine(config);
        OffsetDateTime now = OffsetDateTime.now();
        engine.applyFaultAction(
                new FaultActionCommand("action-3", "run-3", "pv-gobi-01", FaultActionType.REDUCE_TELEMETRY_RATE,
                        now, now.plusSeconds(30)),
                now
        );

        int telemetryCount = 0;
        for (int index = 0; index < 3; index += 1) {
            telemetryCount += engine.nextTick().telemetryPayloads().size();
        }
        FaultActionResult recovery = engine.applyFaultAction(
                new FaultActionCommand("action-4", "run-3", "pv-gobi-01", FaultActionType.START_RATE_LIMITED_RECOVERY,
                        now, now.plusSeconds(30)),
                now
        );

        assertEquals(1, telemetryCount);
        assertTrue(recovery.accepted());
    }

    @Test
    void shouldRejectOlderPlanVersionAndDeduplicateDispatchCommand() {
        SimulationConfig config = new SimulationConfig("127.0.0.1", 1883, "user", "password", 1000, 58L, DemoScenario.STABLE, List.of("pv-gobi-01"));
        SimulationEngine engine = new SimulationEngine(config);
        OffsetDateTime now = OffsetDateTime.now();

        DispatchControlResult first = engine.applyDispatchControl(
                new DispatchControlCommand("command-6", "plan-2", "dispatch-plan-v2", "item-2", "pv-gobi-01", 30, now.plusSeconds(30), 5),
                now
        );
        DispatchControlResult duplicate = engine.applyDispatchControl(
                new DispatchControlCommand("command-6", "plan-2", "dispatch-plan-v2", "item-2", "pv-gobi-01", 30, now.plusSeconds(30), 5),
                now.plusSeconds(1)
        );
        DispatchControlResult olderVersion = engine.applyDispatchControl(
                new DispatchControlCommand("command-7", "plan-1", "dispatch-plan-v1", "item-1", "pv-gobi-01", 20, now.plusSeconds(30), 5),
                now.plusSeconds(2)
        );

        assertEquals(first, duplicate);
        assertTrue(first.accepted());
        assertTrue(!olderVersion.accepted());
    }
}

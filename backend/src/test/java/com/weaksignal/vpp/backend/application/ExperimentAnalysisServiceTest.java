package com.weaksignal.vpp.backend.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weaksignal.vpp.backend.api.dto.ExperimentComparisonResponse;
import com.weaksignal.vpp.backend.domain.LinkState;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.ExperimentRunEntity;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.FaultEventEntity;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.LinkStatusLogEntity;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.TelemetryHistoryEntity;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.ControlCommandEntity;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.DispatchPlanEntity;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.ControlCommandRepository;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.DispatchPlanRepository;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.ExperimentRunRepository;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.FaultEventRepository;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.LinkStatusLogRepository;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.TelemetryHistoryRepository;
import com.weaksignal.vpp.backend.market.MarketSignalService;
import com.weaksignal.vpp.backend.market.MarketSignalSnapshot;
import com.weaksignal.vpp.backend.domain.QualityCode;
import java.math.BigDecimal;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * 实验分析服务测试。
 */
class ExperimentAnalysisServiceTest {

    private static final OffsetDateTime STARTED_AT = OffsetDateTime.parse("2026-06-30T12:00:00+08:00");
    private static final OffsetDateTime ENDED_AT = OffsetDateTime.parse("2026-06-30T12:02:00+08:00");

    @Test
    void shouldReturnExperimentSummary() {
        ExperimentAnalysisService service = createService(stoppedRun(), telemetrySamples(), List.of(), List.of());

        var response = service.getSummary("run-1");

        assertEquals("run-1", response.runId());
        assertEquals(120, response.durationSeconds());
        assertEquals(2, response.sampleCount());
        assertEquals("S1", response.dominantState());
    }

    @Test
    void shouldReadCentralizedControllerAuditFromDispatchPlan() {
        ExperimentRunEntity run = stoppedRun();
        run.setResultSummary("{\"targetStationIds\":[\"pv-gobi-01\"],\"dispatchPlanId\":\"plan-1\"," +
                "\"controlStrategy\":\"CENTRALIZED\"}");
        DispatchPlanEntity plan = new DispatchPlanEntity();
        plan.setPlanId("plan-1");
        plan.setPayloadJson("{\"optimizerName\":\"OR_TOOLS_SCIP\",\"optimizationStatus\":\"OPTIMAL\"," +
                "\"plannedBalanceErrorKw\":0.25}");
        ExperimentAnalysisService service = createService(run, telemetrySamples(), List.of(), List.of(), plan);

        var response = service.getSummary("run-1");

        assertEquals("OR_TOOLS_SCIP", response.controllerType());
        assertEquals(0.25, response.plannedBalanceErrorKw());
        assertEquals(0, response.constraintViolationCount());
    }

    @Test
    void shouldReturnComparisonWithoutDataFailure() {
        ExperimentAnalysisService service = createService(stoppedRun(), List.of(), List.of(), List.of());

        var response = service.getComparison("run-1");

        assertEquals(0, response.before().sampleCount());
        assertEquals(0, response.during().sampleCount());
        assertEquals(0, response.during().averageLatency());
        assertEquals(6, response.derivedMetrics().size());
    }

    @Test
    void shouldReturnDerivedComparisonMetrics() {
        ExperimentAnalysisService service = createService(stoppedRunWithAllTargets(), telemetrySamples(), linkLogs(), List.of());

        var response = service.getComparison("run-1");

        assertEquals(new BigDecimal("164.20"), metricValue(response, "peak-load").duringValue());
        assertEquals(new BigDecimal("4.07"), metricValue(response, "grid-purchase-cost").duringValue());
        assertEquals(new BigDecimal("-2.60"), metricValue(response, "curtailment").improvementValue());
        assertEquals(new BigDecimal("40.00"), metricValue(response, "execution-success-rate").duringValue());
    }

    @Test
    void shouldReturnTimelineWithLinkLog() {
        LinkStatusLogEntity log = new LinkStatusLogEntity();
        log.setStationId("pv-gobi-01");
        log.setCurrentState(LinkState.S1);
        log.setReason("状态进入通信退化");
        log.setOccurredAt(STARTED_AT.plusSeconds(30));
        ExperimentAnalysisService service = createService(stoppedRun(), telemetrySamples(), List.of(log), List.of());

        var response = service.getTimeline("run-1");

        assertEquals(3, response.size());
        assertTrue(response.stream().anyMatch(point -> "状态进入通信退化".equals(point.eventTitle())));
    }

    @Test
    void shouldReturnReplayPoints() {
        ExperimentAnalysisService service = createService(stoppedRun(), telemetrySamples(), List.of(), List.of());

        var response = service.getReplay("run-1", "pv-gobi-01", null, null, 10);

        assertEquals(2, response.size());
        assertEquals("pv-gobi-01", response.getFirst().stationId());
    }

    @Test
    void shouldIncludeThermalStationInAllStationReplay() {
        List<TelemetryHistoryEntity> samples = new java.util.ArrayList<>(telemetrySamples());
        samples.add(telemetry("thermal-plant-06", STARTED_AT.plusSeconds(10), 30, 72, 320, 0.2, LinkState.S0, true, null));
        ExperimentAnalysisService service = createService(stoppedRun(), samples, List.of(), List.of());

        var response = service.getReplay("run-1", null, true, true, null, null, 20);

        assertTrue(response.stream().anyMatch(point -> "thermal-plant-06".equals(point.stationId())));
    }

    /**
     * 已有关联实验遥测时，包含基线的回放仍应保留实验开始前的正常数据。
     */
    @Test
    void shouldKeepBaselineWhenExperimentTelemetryIsIndependentlyLinked() {
        TelemetryHistoryEntity baselineSample = telemetry(
                "pv-gobi-01", STARTED_AT.minusSeconds(30), 20, 100, 168, 0.1, LinkState.S0, true, null
        );
        TelemetryHistoryEntity experimentSample = telemetry(
                "pv-gobi-01", STARTED_AT.plusSeconds(10), 40, 35, 168, 2.0, LinkState.S2, true, null
        );
        experimentSample.setExperimentRunId("run-1");
        ExperimentAnalysisService service = createService(
                stoppedRun(), List.of(baselineSample, experimentSample), List.of(), List.of()
        );

        var response = service.getReplay("run-1", "pv-gobi-01", false, true, null, null, 20);

        assertEquals(2, response.size());
        assertEquals(100.0, response.getFirst().activePower());
        assertEquals(35.0, response.getLast().activePower());
    }

    @Test
    void shouldReturnQualityMetrics() {
        ExperimentAnalysisService service = createService(stoppedRun(), telemetrySamples(), List.of(), List.of());

        var response = service.getQualityMetrics("run-1");

        assertEquals(2, response.trends().size());
        assertEquals(5, response.stateDistribution().size());
        assertEquals(1, response.stationRanking().size());
    }

    @Test
    void shouldUseCurrentTimeForRunningExperimentWindow() {
        ExperimentRunEntity run = runningRun();
        ExperimentAnalysisService service = createService(run, telemetrySamples(), List.of(), List.of());

        var response = service.getSummary("run-1");

        assertEquals(180, response.durationSeconds());
    }

    @Test
    void shouldQueryCurrentExperimentTelemetryByIndependentRunId() {
        TelemetryHistoryEntity currentRunSample = telemetry("pv-gobi-01", STARTED_AT.plusSeconds(10), 90, 120, 168, 1.0, LinkState.S3, false, null);
        currentRunSample.setExperimentRunId("run-1");
        currentRunSample.setActiveFaultCode("COM_OFFLINE");
        TelemetryHistoryEntity otherRunSample = telemetry("pv-gobi-01", STARTED_AT.plusSeconds(10), 10, 120, 168, 0.0, LinkState.S0, true, null);
        otherRunSample.setExperimentRunId("run-2");
        otherRunSample.setActiveFaultCode("COM_OFFLINE");
        ExperimentAnalysisService service = createService(stoppedRun(), List.of(currentRunSample, otherRunSample), List.of(), List.of());

        var response = service.getSummary("run-1");

        assertEquals(1, response.sampleCount());
        assertEquals("S3", response.dominantState());
    }

    @SuppressWarnings("unchecked")
    private ExperimentAnalysisService createService(
            ExperimentRunEntity run,
            List<TelemetryHistoryEntity> telemetrySamples,
            List<LinkStatusLogEntity> linkLogs,
            List<FaultEventEntity> faultEvents
    ) {
        return createService(run, telemetrySamples, linkLogs, faultEvents, null);
    }

    @SuppressWarnings("unchecked")
    private ExperimentAnalysisService createService(
            ExperimentRunEntity run,
            List<TelemetryHistoryEntity> telemetrySamples,
            List<LinkStatusLogEntity> linkLogs,
            List<FaultEventEntity> faultEvents,
            DispatchPlanEntity dispatchPlan
    ) {
        return new ExperimentAnalysisService(
                repositoryProxy(ExperimentRunRepository.class, Map.of("findByRunId", arguments -> Optional.of(run))),
                repositoryProxy(TelemetryHistoryRepository.class, Map.of(
                        "findByStationIdInAndReceivedTimestampBetweenOrderByReceivedTimestampAsc",
                        arguments -> filterTelemetry(
                                telemetrySamples,
                                (java.util.Collection<String>) arguments[0],
                                (OffsetDateTime) arguments[1],
                                (OffsetDateTime) arguments[2]
                        ),
                        "existsByExperimentRunId",
                        arguments -> telemetrySamples.stream().anyMatch(sample -> arguments[0].equals(sample.getExperimentRunId())),
                        "findByExperimentRunIdAndStationIdInAndReceivedTimestampBetweenOrderByReceivedTimestampAsc",
                        arguments -> filterExperimentTelemetry(
                                telemetrySamples,
                                (String) arguments[0],
                                (java.util.Collection<String>) arguments[1],
                                (OffsetDateTime) arguments[2],
                                (OffsetDateTime) arguments[3]
                        )
                )),
                repositoryProxy(LinkStatusLogRepository.class, Map.of(
                        "findByOccurredAtBetweenOrderByOccurredAtAsc",
                        arguments -> filterLinkLogs(linkLogs, (OffsetDateTime) arguments[0], (OffsetDateTime) arguments[1])
                )),
                repositoryProxy(FaultEventRepository.class, Map.of(
                        "findByOccurredAtBetweenOrderByOccurredAtAsc",
                        arguments -> filterEvents(faultEvents, (OffsetDateTime) arguments[0], (OffsetDateTime) arguments[1])
                )),
                repositoryProxy(ControlCommandRepository.class, Map.of(
                        "findBySendTimeBetweenOrderBySendTimeAsc",
                        arguments -> filterCommands(commands(), (OffsetDateTime) arguments[0], (OffsetDateTime) arguments[1]),
                        "findByDispatchPlanIdOrderByCreatedAtAsc",
                        arguments -> List.of()
                )),
                repositoryProxy(DispatchPlanRepository.class, Map.of(
                        "findByPlanId",
                        arguments -> dispatchPlan != null && dispatchPlan.getPlanId().equals(arguments[0])
                                ? Optional.of(dispatchPlan) : Optional.empty()
                )),
                marketSignalService(),
                new ObjectMapper().findAndRegisterModules(),
                Clock.fixed(OffsetDateTime.parse("2026-06-30T12:03:00+08:00").toInstant(), ZoneId.of("Asia/Shanghai"))
        );
    }

    private ExperimentRunEntity stoppedRun() {
        ExperimentRunEntity run = runningRun();
        run.setStatus("STOPPED");
        run.setEndedAt(ENDED_AT);
        return run;
    }

    private ExperimentRunEntity stoppedRunWithAllTargets() {
        ExperimentRunEntity run = stoppedRun();
        run.setResultSummary("{\"targetStationIds\":[\"pv-gobi-01\",\"wind-ridge-02\",\"grid-link-00\",\"storage-hub-03\",\"load-oasis-04\",\"charge-corridor-05\"],\"durationSeconds\":120,\"remark\":\"全量测试\"}");
        return run;
    }

    private ExperimentRunEntity runningRun() {
        ExperimentRunEntity run = new ExperimentRunEntity();
        run.setRunId("run-1");
        run.setScenarioCode("T1_LATENCY_JITTER");
        run.setStatus("RUNNING");
        run.setStartedAt(STARTED_AT);
        run.setResultSummary("{\"targetStationIds\":[\"pv-gobi-01\"],\"durationSeconds\":120,\"remark\":\"测试\"}");
        return run;
    }

    private List<TelemetryHistoryEntity> telemetrySamples() {
        return List.of(
                telemetry("load-oasis-04", STARTED_AT.minusSeconds(40), 62, 150, 180, 0.0, LinkState.S0, true, null),
                telemetry("charge-corridor-05", STARTED_AT.minusSeconds(40), 24, 24, 24, 0.0, LinkState.S0, true, null),
                telemetry("grid-link-00", STARTED_AT.minusSeconds(40), 10, 10, 10, 0.0, LinkState.S0, true, null),
                telemetry("pv-gobi-01", STARTED_AT.minusSeconds(40), 80, 160, 168, 0.1, LinkState.S0, true, null),
                telemetry("wind-ridge-02", STARTED_AT.minusSeconds(40), 90, 170, 178, 0.2, LinkState.S0, true, null),
                telemetry("storage-hub-03", STARTED_AT.minusSeconds(40), 58, 58, 58, 0.0, LinkState.S0, true, 60.0),
                telemetry("load-oasis-04", STARTED_AT.plusSeconds(10), 120, 140, 180, 1.2, LinkState.S1, true, null),
                telemetry("charge-corridor-05", STARTED_AT.plusSeconds(10), 40, 24.2, 24.2, 0.8, LinkState.S1, true, null),
                telemetry("grid-link-00", STARTED_AT.plusSeconds(10), 150, 80, 80, 1.6, LinkState.S1, true, null),
                telemetry("pv-gobi-01", STARTED_AT.plusSeconds(10), 120, 120, 168, 1.2, LinkState.S1, true, null),
                telemetry("wind-ridge-02", STARTED_AT.plusSeconds(10), 130, 140, 178, 2.1, LinkState.S1, true, null),
                telemetry("storage-hub-03", STARTED_AT.plusSeconds(10), 55, 58, 58, 0.5, LinkState.S1, true, 62.0),
                telemetry("load-oasis-04", STARTED_AT.plusSeconds(20), 180, 140, 180, 1.1, LinkState.S4, true, null),
                telemetry("charge-corridor-05", STARTED_AT.plusSeconds(20), 42, 24.2, 24.2, 0.7, LinkState.S4, true, null),
                telemetry("grid-link-00", STARTED_AT.plusSeconds(20), 42, 42, 42, 0.4, LinkState.S4, true, null),
                telemetry("pv-gobi-01", STARTED_AT.plusSeconds(20), 180, 120, 168, 2.1, LinkState.S4, true, null),
                telemetry("wind-ridge-02", STARTED_AT.plusSeconds(20), 40, 140, 178, 0.4, LinkState.S4, true, null),
                telemetry("storage-hub-03", STARTED_AT.plusSeconds(20), 50, 58, 58, 0.4, LinkState.S4, true, 52.0)
        );
    }

    private TelemetryHistoryEntity telemetry(
            String stationId,
            OffsetDateTime receivedAt,
            int latency,
            double activePower,
            double availableCapacity,
            double packetLossRate,
            LinkState linkState,
            boolean online,
            Double soc
    ) {
        TelemetryHistoryEntity entity = new TelemetryHistoryEntity();
        entity.setStationId(stationId);
        entity.setMessageId(stationId + receivedAt);
        entity.setSequenceNumber(receivedAt.toEpochSecond());
        entity.setDeviceTimestamp(receivedAt.minusSeconds(1));
        entity.setReceivedTimestamp(receivedAt);
        entity.setActivePower(activePower);
        entity.setAvailableCapacity(availableCapacity);
        entity.setLatency(latency);
        entity.setPacketLossRate(packetLossRate);
        entity.setDataAge(1);
        entity.setLinkState(linkState);
        entity.setQualityCode("GOOD");
        entity.setOnline(online);
        entity.setPayloadJson(soc == null ? "{}" : "{\"metrics\":{\"soc\":" + soc + "}}");
        return entity;
    }

    private List<LinkStatusLogEntity> linkLogs() {
        LinkStatusLogEntity linkStatusLogEntity = new LinkStatusLogEntity();
        linkStatusLogEntity.setStationId("pv-gobi-01");
        linkStatusLogEntity.setCurrentState(LinkState.S4);
        linkStatusLogEntity.setReason("恢复同步");
        linkStatusLogEntity.setOccurredAt(STARTED_AT.plusSeconds(20));
        return List.of(linkStatusLogEntity);
    }

    private List<ControlCommandEntity> commands() {
        return List.of(
                command("before-1", STARTED_AT.minusSeconds(30), "EXECUTED"),
                command("during-1", STARTED_AT.plusSeconds(5), "EXECUTED"),
                command("during-2", STARTED_AT.plusSeconds(8), "FAILED"),
                command("during-3", STARTED_AT.plusSeconds(12), "REJECTED"),
                command("during-4", STARTED_AT.plusSeconds(16), "FAILED"),
                command("during-5", STARTED_AT.plusSeconds(18), "EXECUTED")
        );
    }

    private ControlCommandEntity command(String commandId, OffsetDateTime sendTime, String status) {
        ControlCommandEntity entity = new ControlCommandEntity();
        entity.setCommandId(commandId);
        entity.setStationId("storage-hub-03");
        entity.setCommandType("POWER_SETPOINT");
        entity.setCommandPayload("{}");
        entity.setSendTime(sendTime);
        entity.setExpireAt(sendTime.plusSeconds(30));
        entity.setStatus(status);
        entity.setCreatedAt(sendTime);
        return entity;
    }

    private List<TelemetryHistoryEntity> filterTelemetry(
            List<TelemetryHistoryEntity> samples,
            java.util.Collection<String> stationIds,
            OffsetDateTime from,
            OffsetDateTime to
    ) {
        return samples.stream()
                .filter(sample -> stationIds.contains(sample.getStationId()))
                .filter(sample -> !sample.getReceivedTimestamp().isBefore(from) && !sample.getReceivedTimestamp().isAfter(to))
                .toList();
    }

    private List<TelemetryHistoryEntity> filterExperimentTelemetry(
            List<TelemetryHistoryEntity> samples,
            String experimentRunId,
            java.util.Collection<String> stationIds,
            OffsetDateTime from,
            OffsetDateTime to
    ) {
        return samples.stream()
                .filter(sample -> experimentRunId.equals(sample.getExperimentRunId()))
                .filter(sample -> stationIds.contains(sample.getStationId()))
                .filter(sample -> !sample.getReceivedTimestamp().isBefore(from) && !sample.getReceivedTimestamp().isAfter(to))
                .toList();
    }

    private List<LinkStatusLogEntity> filterLinkLogs(List<LinkStatusLogEntity> logs, OffsetDateTime from, OffsetDateTime to) {
        return logs.stream()
                .filter(log -> !log.getOccurredAt().isBefore(from) && !log.getOccurredAt().isAfter(to))
                .toList();
    }

    private List<FaultEventEntity> filterEvents(List<FaultEventEntity> events, OffsetDateTime from, OffsetDateTime to) {
        return events.stream()
                .filter(event -> !event.getOccurredAt().isBefore(from) && !event.getOccurredAt().isAfter(to))
                .toList();
    }

    private List<ControlCommandEntity> filterCommands(List<ControlCommandEntity> commands, OffsetDateTime from, OffsetDateTime to) {
        return commands.stream()
                .filter(command -> !command.getSendTime().isBefore(from) && !command.getSendTime().isAfter(to))
                .toList();
    }

    private MarketSignalService marketSignalService() {
        MarketSignalService marketSignalService = mock(MarketSignalService.class);
        when(marketSignalService.getLatestActiveSnapshot()).thenReturn(new MarketSignalSnapshot(
                "market-1",
                "REALTIME",
                STARTED_AT.minusMinutes(1),
                STARTED_AT.plusMinutes(1),
                0,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("2.000000"),
                120,
                80,
                30,
                10,
                STARTED_AT.minusMinutes(1),
                STARTED_AT.minusMinutes(1),
                STARTED_AT.plusMinutes(10),
                "TEST",
                QualityCode.GOOD,
                "v1",
                STARTED_AT.minusMinutes(1)
        ));
        return marketSignalService;
    }

    private ExperimentComparisonResponse.DerivedMetricResponse metricValue(ExperimentComparisonResponse response, String metricCode) {
        return response.derivedMetrics().stream()
                .filter(metric -> metricCode.equals(metric.metricCode()))
                .findFirst()
                .orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private <T> T repositoryProxy(Class<T> repositoryType, Map<String, java.util.function.Function<Object[], Object>> handlers) {
        return (T) Proxy.newProxyInstance(
                repositoryType.getClassLoader(),
                new Class<?>[]{repositoryType},
                (proxy, method, args) -> {
                    java.util.function.Function<Object[], Object> handler = handlers.get(method.getName());
                    if (handler != null) {
                        return handler.apply(args == null ? new Object[0] : args);
                    }
                    if (method.getName().equals("toString")) {
                        return repositoryType.getSimpleName() + "Proxy";
                    }
                    return null;
                }
        );
    }
}

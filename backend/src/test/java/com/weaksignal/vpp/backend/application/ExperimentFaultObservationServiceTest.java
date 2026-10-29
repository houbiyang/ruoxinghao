package com.weaksignal.vpp.backend.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weaksignal.vpp.backend.domain.LinkState;
import com.weaksignal.vpp.backend.domain.ResourceType;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.ExperimentRunEntity;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.ExperimentRunRepository;
import com.weaksignal.vpp.backend.mqtt.MqttGateway;
import com.weaksignal.vpp.backend.mqtt.dto.ScenarioReportMessage;
import com.weaksignal.vpp.backend.mqtt.dto.TelemetryReportMessage;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * 实验故障观测服务测试。
 */
class ExperimentFaultObservationServiceTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-07-06T12:40:30+08:00");

    @Test
    void shouldObserveOfflineEvidenceAndScheduleOrchestration() throws Exception {
        ExperimentRunRepository repository = mock(ExperimentRunRepository.class);
        ExperimentClosedLoopOrchestrationService closedLoop = mock(ExperimentClosedLoopOrchestrationService.class);
        ExperimentRunEntity run = run("INJECTING", "2026-07-06T12:41:00+08:00");
        when(repository.findByStatusIn(any())).thenReturn(List.of(run));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ExperimentFaultObservationService service = service(repository, closedLoop);
        service.observeLinkState("storage-hub-03", LinkState.S3, NOW);
        service.observeLinkState("storage-hub-03", LinkState.S3, NOW.plusSeconds(1));

        assertEquals("FAULT_OBSERVED", run.getStatus());
        com.fasterxml.jackson.databind.JsonNode summary = new ObjectMapper().findAndRegisterModules()
                .readTree(run.getResultSummary());
        assertEquals(
                OffsetDateTime.parse("2026-07-06T12:41:00+08:00").toEpochSecond(),
                summary.path("orchestrationScheduledAt").asLong()
        );
        assertEquals(
                OffsetDateTime.parse("2026-07-06T12:42:00+08:00").toEpochSecond(),
                summary.path("orchestrationStageEndsAt").asLong()
        );
        verify(closedLoop, never()).triggerIfEvidenceObserved(any());
    }

    /**
     * 故障从注入开始持续满一分钟后，生命周期检查才允许触发智能体调度。
     */
    @Test
    void shouldTriggerOrchestrationOneMinuteAfterInjectionStarted() {
        ExperimentRunRepository repository = mock(ExperimentRunRepository.class);
        ExperimentClosedLoopOrchestrationService closedLoop = mock(ExperimentClosedLoopOrchestrationService.class);
        ExperimentRunEntity run = run("FAULT_OBSERVED", "2026-07-06T12:41:00+08:00");
        run.setResultSummary(run.getResultSummary().replace(
                "\"orchestrationStatus\":\"PENDING\"",
                "\"faultObservedAt\":\"2026-07-06T12:39:29+08:00\",\"orchestrationScheduledAt\":\"2026-07-06T12:40:29+08:00\",\"orchestrationStatus\":\"PENDING\""
        ));
        when(repository.findByStatusIn(any())).thenReturn(List.of(run));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service(repository, closedLoop).inspectLifecycle();

        verify(closedLoop).triggerIfEvidenceObserved("run-1");
    }

    /**
     * 智能体调度阶段未持续满一分钟时，不允许自动进入故障恢复。
     */
    @Test
    void shouldKeepSchedulingStageActiveForOneMinute() {
        ExperimentRunRepository repository = mock(ExperimentRunRepository.class);
        ExperimentClosedLoopOrchestrationService closedLoop = mock(ExperimentClosedLoopOrchestrationService.class);
        ExperimentRunEntity run = run("FAULT_OBSERVED", "2026-07-06T12:41:00+08:00");
        run.setResultSummary(run.getResultSummary().replace(
                "\"orchestrationStatus\":\"PENDING\"",
                "\"orchestrationScheduledAt\":\"2026-07-06T12:39:31+08:00\","
                        + "\"orchestrationStageEndsAt\":\"2026-07-06T12:40:31+08:00\","
                        + "\"orchestrationStatus\":\"COMPLETED\""
        ));
        when(repository.findByStatusIn(any())).thenReturn(List.of(run));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service(repository, closedLoop).inspectLifecycle();

        assertEquals("FAULT_OBSERVED", run.getStatus());
    }

    /**
     * 智能体调度阶段持续满一分钟后，自动恢复才可以启动。
     */
    @Test
    void shouldStartRecoveryAfterOneMinuteSchedulingStage() {
        ExperimentRunRepository repository = mock(ExperimentRunRepository.class);
        ExperimentClosedLoopOrchestrationService closedLoop = mock(ExperimentClosedLoopOrchestrationService.class);
        ExperimentRunEntity run = run("FAULT_OBSERVED", "2026-07-06T12:41:00+08:00");
        run.setResultSummary(run.getResultSummary().replace(
                "\"orchestrationStatus\":\"PENDING\"",
                "\"orchestrationScheduledAt\":\"2026-07-06T12:39:29+08:00\","
                        + "\"orchestrationStageEndsAt\":\"2026-07-06T12:40:29+08:00\","
                        + "\"orchestrationStatus\":\"COMPLETED\""
        ));
        when(repository.findByStatusIn(any())).thenReturn(List.of(run));
        when(repository.findByRunId("run-1")).thenReturn(Optional.of(run));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service(repository, closedLoop).inspectLifecycle();

        assertEquals("RECOVERING", run.getStatus());
    }

    @Test
    void shouldStopWithoutOrchestrationWhenInjectionDeadlineExpires() {
        ExperimentRunRepository repository = mock(ExperimentRunRepository.class);
        ExperimentClosedLoopOrchestrationService closedLoop = mock(ExperimentClosedLoopOrchestrationService.class);
        ExperimentRunEntity run = run("INJECTING", "2026-07-06T12:40:00+08:00");
        when(repository.findByStatusIn(any())).thenReturn(List.of(run));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service(repository, closedLoop).inspectLifecycle();

        assertEquals("INJECTION_NOT_OBSERVED", run.getStatus());
        verify(closedLoop, never()).triggerIfEvidenceObserved(any());
    }

    @Test
    void shouldCompleteOnlyAfterRecoveryReportAndActualStableState() {
        ExperimentRunRepository repository = mock(ExperimentRunRepository.class);
        ExperimentClosedLoopOrchestrationService closedLoop = mock(ExperimentClosedLoopOrchestrationService.class);
        ExperimentRunEntity run = run("RECOVERING", "2026-07-06T12:41:00+08:00");
        addRejoinedEvidence(run, "storage-hub-03");
        when(repository.findByRunId("run-1")).thenReturn(Optional.of(run));
        when(repository.findByStatusIn(any())).thenReturn(List.of(run));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ExperimentFaultObservationService service = service(repository, closedLoop);
        service.handleScenarioReport(new ScenarioReportMessage(
                "run-1", "COM_OFFLINE", "RECOVERED", List.of("storage-hub-03"), NOW, null
        ));
        assertEquals("RECOVERING", run.getStatus());
        service.observeLinkState("storage-hub-03", LinkState.S4, NOW.plusSeconds(1));
        assertEquals("RECOVERING", run.getStatus());
        observeStableLinkSamples(service, "storage-hub-03", NOW.plusSeconds(7));
        assertEquals("COMPLETED", run.getStatus());
    }

    @Test
    void shouldNotCompleteWhenStableStateArrivesWithoutRecoveryReport() {
        ExperimentRunRepository repository = mock(ExperimentRunRepository.class);
        ExperimentClosedLoopOrchestrationService closedLoop = mock(ExperimentClosedLoopOrchestrationService.class);
        ExperimentRunEntity run = run("RECOVERING", "2026-07-06T12:41:00+08:00");
        when(repository.findByRunId("run-1")).thenReturn(Optional.of(run));
        when(repository.findByStatusIn(any())).thenReturn(List.of(run));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ExperimentFaultObservationService service = service(repository, closedLoop);
        observeStableLinkSamples(service, "storage-hub-03", NOW);

        assertEquals("RECOVERING", run.getStatus());
        assertTrue(run.getResultSummary().contains("\"recoveryReportStatus\":null"));
    }

    /**
     * COM_OFFLINE 只有 S0 稳定遥测而没有 S4 重新入图证据时不得完成。
     */
    @Test
    void shouldWaitForCommunicationGraphRejoinEvidence() {
        ExperimentRunRepository repository = mock(ExperimentRunRepository.class);
        ExperimentClosedLoopOrchestrationService closedLoop = mock(ExperimentClosedLoopOrchestrationService.class);
        ExperimentRunEntity run = run("RECOVERING", "2026-07-06T12:41:00+08:00");
        run.setResultSummary(run.getResultSummary().replace(
                "\"recoveryReportStatus\":null", "\"recoveryReportStatus\":\"RECOVERED\""));
        when(repository.findByStatusIn(any())).thenReturn(List.of(run));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        observeStableLinkSamples(service(repository, closedLoop), "storage-hub-03", NOW.plusSeconds(1));

        assertEquals("RECOVERING", run.getStatus());
    }

    /**
     * 恢复期持续超过上限且没有重新入图证据时，必须形成失败终态。
     */
    @Test
    void shouldFailWhenRecoveryEvidenceTimesOut() {
        ExperimentRunRepository repository = mock(ExperimentRunRepository.class);
        ExperimentClosedLoopOrchestrationService closedLoop = mock(ExperimentClosedLoopOrchestrationService.class);
        ExperimentRunEntity run = run("RECOVERING", "2026-07-06T12:41:00+08:00");
        run.setResultSummary(run.getResultSummary().replace(
                "\"recoveryReportStatus\":null",
                "\"recoveryStartedAt\":\"2026-07-06T12:38:00+08:00\",\"recoveryReportStatus\":null"
        ));
        when(repository.findByStatusIn(any())).thenReturn(List.of(run));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service(repository, closedLoop).inspectLifecycle();

        assertEquals("FAILED", run.getStatus());
        assertTrue(run.getResultSummary().contains("RECOVERY_TIMEOUT"));
        verify(repository).save(run);
    }

    @Test
    void shouldRequireSimulatorRecoveryReportWhenStableTelemetryArrives() {
        ExperimentRunRepository repository = mock(ExperimentRunRepository.class);
        ExperimentClosedLoopOrchestrationService closedLoop = mock(ExperimentClosedLoopOrchestrationService.class);
        ExperimentRunEntity run = environmentRun("ENV_WIND_CALM", "wind-ridge-02", Map.of("windSpeed", 0.8));
        run.setStatus("RECOVERING");
        when(repository.findByRunId("run-1")).thenReturn(Optional.of(run));
        when(repository.findByStatusIn(any())).thenReturn(List.of(run));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ExperimentFaultObservationService service = service(repository, closedLoop);
        for (int sampleIndex = 0; sampleIndex < 3; sampleIndex += 1) {
            service.observeTelemetry(
                    telemetry("wind-ridge-02", sampleIndex + 1, 30, null),
                    LinkState.S0,
                    NOW.plusSeconds(sampleIndex)
            );
        }

        assertEquals("RECOVERING", run.getStatus());
    }

    @Test
    void shouldIgnoreInvalidAndDuplicateRecoveryReports() {
        ExperimentRunRepository repository = mock(ExperimentRunRepository.class);
        ExperimentClosedLoopOrchestrationService closedLoop = mock(ExperimentClosedLoopOrchestrationService.class);
        ExperimentRunEntity run = run("RECOVERING", "2026-07-06T12:41:00+08:00");
        when(repository.findByRunId("run-1")).thenReturn(Optional.of(run));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ExperimentFaultObservationService service = service(repository, closedLoop);
        service.handleScenarioReport(new ScenarioReportMessage(
                "run-1", "COM_OFFLINE", "RECOVERED", List.of("other-station"), NOW, null
        ));
        assertTrue(run.getResultSummary().contains("\"recoveryReportStatus\":null"));
        service.handleScenarioReport(new ScenarioReportMessage(
                "run-1", "COM_OFFLINE", "RECOVERED", List.of("storage-hub-03"), NOW, null
        ));
        String summaryAfterFirstReport = run.getResultSummary();
        service.handleScenarioReport(new ScenarioReportMessage(
                "run-1", "COM_OFFLINE", "RECOVERED", List.of("storage-hub-03"), NOW.plusSeconds(1), null
        ));
        assertEquals(summaryAfterFirstReport, run.getResultSummary());
    }

    @Test
    void shouldStartRecoveryAfterOrchestrationFailedWithoutOverwritingFailure() {
        ExperimentRunRepository repository = mock(ExperimentRunRepository.class);
        ExperimentClosedLoopOrchestrationService closedLoop = mock(ExperimentClosedLoopOrchestrationService.class);
        ExperimentRunEntity run = run("FAULT_OBSERVED", "2026-07-06T12:41:00+08:00");
        run.setResultSummary(run.getResultSummary().replace("\"orchestrationStatus\":\"PENDING\"", "\"orchestrationStatus\":\"FAILED\""));
        when(repository.findByRunId("run-1")).thenReturn(Optional.of(run));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertTrue(service(repository, closedLoop).beginRecovery("run-1"));

        assertEquals("RECOVERING", run.getStatus());
        assertTrue(run.getResultSummary().contains("\"orchestrationStatus\":\"FAILED\""));
    }

    /**
     * 物理链路恢复完成但智能体调度失败时，实验应结束为失败终态，不能持续停留在恢复中。
     */
    @Test
    void shouldFinishAsFailedAfterPhysicalRecoveryWhenOrchestrationFailed() {
        ExperimentRunRepository repository = mock(ExperimentRunRepository.class);
        ExperimentClosedLoopOrchestrationService closedLoop = mock(ExperimentClosedLoopOrchestrationService.class);
        ExperimentRunEntity run = run("RECOVERING", "2026-07-06T12:41:00+08:00");
        addRejoinedEvidence(run, "storage-hub-03");
        run.setResultSummary(run.getResultSummary()
                .replace("\"recoveryReportStatus\":null", "\"recoveryReportStatus\":\"RECOVERED\"")
                .replace("\"orchestrationStatus\":\"PENDING\"", "\"orchestrationStatus\":\"FAILED\""));
        when(repository.findByStatusIn(any())).thenReturn(List.of(run));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        observeStableLinkSamples(service(repository, closedLoop), "storage-hub-03", NOW.plusSeconds(1));

        assertEquals("FAILED", run.getStatus());
        assertEquals(NOW.plusSeconds(3), run.getEndedAt());
        assertTrue(run.getResultSummary().contains("\"recoveryReportStatus\":\"RECOVERED\""));
        assertTrue(run.getResultSummary().contains("\"failureReason\":\"闭环重调度执行失败\""));
    }

    /**
     * 用户重复停止已恢复的实验时，应跳过缺失的调度收敛结果并进入已停止终态。
     */
    @Test
    void shouldIdempotentlyStopRecoveredExperimentWithoutConvergenceResult() {
        ExperimentRunRepository repository = mock(ExperimentRunRepository.class);
        ExperimentClosedLoopOrchestrationService closedLoop = mock(ExperimentClosedLoopOrchestrationService.class);
        ExperimentRunEntity run = run("RECOVERING", "2026-07-06T12:41:00+08:00");
        run.setResultSummary("""
                {"targetStationIds":["storage-hub-03"],"faultEvidenceConfirmed":true,"recoveryReportStatus":"RECOVERED","recoveredLinkStates":{"storage-hub-03":{"linkState":"S0"}},"orchestrationStatus":"FAILED","failureReason":"预测快照已过期"}
                """);
        addRejoinedEvidence(run, "storage-hub-03");
        when(repository.findByRunId("run-1")).thenReturn(Optional.of(run));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        boolean accepted = service(repository, closedLoop).beginRecovery("run-1", true);

        assertTrue(accepted);
        assertEquals("STOPPED", run.getStatus());
        assertEquals(NOW, run.getEndedAt());
        assertTrue(run.getResultSummary().contains("\"manualStopRequested\":true"));
        assertTrue(run.getResultSummary().contains("\"lifecycleStatus\":\"STOPPED\""));
    }

    @Test
    void shouldWaitForManualStopWhenAutomaticRecoveryIsDisabled() {
        ExperimentRunRepository repository = mock(ExperimentRunRepository.class);
        ExperimentClosedLoopOrchestrationService closedLoop = mock(ExperimentClosedLoopOrchestrationService.class);
        ExperimentRunEntity run = run("FAULT_OBSERVED", "2026-07-06T12:41:00+08:00");
        run.setResultSummary(run.getResultSummary()
                .replace("\"durationSeconds\":120,\"automaticRecovery\":true", "\"durationSeconds\":10,\"automaticRecovery\":false"));
        when(repository.findByStatusIn(any())).thenReturn(List.of(run));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service(repository, closedLoop).inspectLifecycle();

        assertEquals("FAULT_OBSERVED", run.getStatus());
        verify(closedLoop, never()).triggerIfEvidenceObserved(any());
    }

    @Test
    void shouldObserveLatencyOnlyAfterThreeConsecutiveThresholdSamples() {
        ExperimentRunRepository repository = mock(ExperimentRunRepository.class);
        ExperimentClosedLoopOrchestrationService closedLoop = mock(ExperimentClosedLoopOrchestrationService.class);
        ExperimentRunEntity run = communicationRun("COM_LATENCY", Map.of("latency", 800.0, "jitter", 120.0));
        when(repository.findByRunId("run-1")).thenReturn(Optional.of(run));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        ExperimentFaultObservationService service = service(repository, closedLoop);

        service.observeTelemetry(telemetry("wind-ridge-02", 1, 820, "COM_LATENCY"), LinkState.S1, NOW);
        service.observeTelemetry(telemetry("wind-ridge-02", 2, 810, "COM_LATENCY"), LinkState.S1, NOW.plusSeconds(1));
        assertEquals("INJECTING", run.getStatus());
        service.observeTelemetry(telemetry("wind-ridge-02", 3, 860, "COM_LATENCY"), LinkState.S1, NOW.plusSeconds(2));

        assertEquals("FAULT_OBSERVED", run.getStatus());
        assertTrue(run.getResultSummary().contains("\"actualJitter\":50.0"));
        verify(closedLoop, never()).triggerIfEvidenceObserved(any());
    }

    @Test
    void shouldIgnoreNonTargetLatencyTelemetryAndSingleAbnormalSample() {
        ExperimentRunRepository repository = mock(ExperimentRunRepository.class);
        ExperimentClosedLoopOrchestrationService closedLoop = mock(ExperimentClosedLoopOrchestrationService.class);
        ExperimentRunEntity run = communicationRun("COM_LATENCY", Map.of("latency", 800.0));
        when(repository.findByRunId("run-1")).thenReturn(Optional.of(run));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        ExperimentFaultObservationService service = service(repository, closedLoop);

        service.observeTelemetry(telemetry("pv-gobi-01", 1, 900, "COM_LATENCY"), LinkState.S1, NOW);
        service.observeTelemetry(telemetry("wind-ridge-02", 1, 900, "COM_LATENCY"), LinkState.S1, NOW);

        assertEquals("INJECTING", run.getStatus());
        verify(closedLoop, never()).triggerIfEvidenceObserved(any());
    }

    @Test
    void shouldObservePacketLossFromSequenceGapsInsteadOfReportedRate() {
        ExperimentRunRepository repository = mock(ExperimentRunRepository.class);
        ExperimentClosedLoopOrchestrationService closedLoop = mock(ExperimentClosedLoopOrchestrationService.class);
        ExperimentRunEntity run = communicationRun("COM_PACKET_LOSS", Map.of("packetLossRate", 35.0, "burstLength", 6.0));
        when(repository.findByRunId("run-1")).thenReturn(Optional.of(run));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        ExperimentFaultObservationService service = service(repository, closedLoop);

        service.observeTelemetry(telemetry("wind-ridge-02", 1, 30, "COM_PACKET_LOSS"), LinkState.S1, NOW);
        service.observeTelemetry(telemetry("wind-ridge-02", 10, 30, "COM_PACKET_LOSS"), LinkState.S1, NOW.plusSeconds(9));

        assertEquals("FAULT_OBSERVED", run.getStatus());
        assertTrue(run.getResultSummary().contains("\"actualPacketLossRate\":"));
        verify(closedLoop, never()).triggerIfEvidenceObserved(any());
    }

    @Test
    void shouldObserveEachEnvironmentScenarioOnlyWithActualMeasurementsAndDerating() {
        assertEnvironmentObserved("ENV_WIND_CALM", "wind-ridge-02", Map.of("windSpeed", 0.8, "turbulence", 0.06), 0.0, 0.0);
        assertEnvironmentObserved("ENV_OVERCAST", "pv-gobi-01", Map.of("irradiance", 160.0, "cloudCover", 0.85), 20.0, 26.0);
        assertEnvironmentObserved("ENV_HEAT", "pv-gobi-01", Map.of("ambientTemperature", 46.0, "moduleTemperature", 65.0), 80.0, 127.0);
        assertEnvironmentObserved("ENV_DUST", "pv-gobi-01", Map.of("dustDensity", 18.0, "visibility", 2.0, "conversionEfficiency", 0.62), 80.0, 104.0);
    }

    @Test
    void shouldNotObserveEnvironmentFaultWhenCriticalMeasurementIsMissing() {
        ExperimentRunRepository repository = mock(ExperimentRunRepository.class);
        ExperimentClosedLoopOrchestrationService closedLoop = mock(ExperimentClosedLoopOrchestrationService.class);
        ExperimentRunEntity run = environmentRun("ENV_OVERCAST", "pv-gobi-01", Map.of("irradiance", 160.0, "cloudCover", 0.85));
        when(repository.findByRunId("run-1")).thenReturn(Optional.of(run));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service(repository, closedLoop).observeTelemetry(
                environmentTelemetry("pv-gobi-01", "ENV_OVERCAST", Map.of("irradiance", 160.0), 20.0, 26.0), LinkState.S0, NOW
        );

        assertEquals("INJECTING", run.getStatus());
        verify(closedLoop, never()).triggerIfEvidenceObserved(any());
    }

    @Test
    void shouldPreserveRecoveredReportReceivedBeforeRecoveryStarts() {
        ExperimentRunRepository repository = mock(ExperimentRunRepository.class);
        ExperimentClosedLoopOrchestrationService closedLoop = mock(ExperimentClosedLoopOrchestrationService.class);
        ExperimentRunEntity run = run("FAULT_OBSERVED", "2026-07-06T12:41:00+08:00");
        addRejoinedEvidence(run, "storage-hub-03");
        when(repository.findByRunId("run-1")).thenReturn(Optional.of(run));
        when(repository.findByStatusIn(any())).thenReturn(List.of(run));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        ExperimentFaultObservationService service = service(repository, closedLoop);

        service.handleScenarioReport(new ScenarioReportMessage("run-1", "COM_OFFLINE", "RECOVERED", List.of("storage-hub-03"), NOW, null));
        assertTrue(run.getResultSummary().contains("\"recoveryReportStatus\":\"RECOVERED\""));
        assertTrue(service.beginRecovery("run-1"));
        observeStableLinkSamples(service, "storage-hub-03", NOW.plusSeconds(1));

        assertEquals("COMPLETED", run.getStatus());
    }

    /**
     * 模拟器恢复报告缺失时，恢复开始十秒后的连续 S0 遥测应作为兜底证据结束实验。
     */
    @Test
    void shouldCompleteFromStableTelemetryWhenRecoveryReportIsMissing() {
        ExperimentRunRepository repository = mock(ExperimentRunRepository.class);
        ExperimentClosedLoopOrchestrationService closedLoop = mock(ExperimentClosedLoopOrchestrationService.class);
        ExperimentRunEntity run = run("RECOVERING", "2026-07-06T12:41:00+08:00");
        addRejoinedEvidence(run, "storage-hub-03");
        run.setResultSummary(run.getResultSummary().replace(
                "\"recoveryReportStatus\":null",
                "\"recoveryStartedAt\":\"2026-07-06T12:40:00+08:00\",\"recoveryReportStatus\":null"
        ));
        when(repository.findByStatusIn(any())).thenReturn(List.of(run));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        observeStableLinkSamples(service(repository, closedLoop), "storage-hub-03", NOW.plusSeconds(1));

        assertEquals("COMPLETED", run.getStatus());
        assertTrue(run.getResultSummary().contains("\"recoveryReportStatus\":\"RECOVERED\""));
        assertTrue(run.getResultSummary().contains("\"recoveryEvidenceSource\":\"STABLE_TELEMETRY_FALLBACK\""));
    }

    @Test
    void shouldPersistFailureReasonWhenRecoveryFinishesAfterConvergenceFailure() {
        ExperimentRunRepository repository = mock(ExperimentRunRepository.class);
        ExperimentClosedLoopOrchestrationService closedLoop = mock(ExperimentClosedLoopOrchestrationService.class);
        ExperimentRunEntity run = run("FAULT_OBSERVED", "2026-07-06T12:41:00+08:00");
        addRejoinedEvidence(run, "storage-hub-03");
        run.setResultSummary(run.getResultSummary().replace(
                "\"convergenceStatus\":\"TARGET_REACHED\"",
                "\"convergenceStatus\":\"FAILED\""
        ));
        when(repository.findByRunId("run-1")).thenReturn(Optional.of(run));
        when(repository.findByStatusIn(any())).thenReturn(List.of(run));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        ExperimentFaultObservationService service = service(repository, closedLoop);

        service.handleScenarioReport(new ScenarioReportMessage(
                "run-1", "COM_OFFLINE", "RECOVERED", List.of("storage-hub-03"), NOW, null
        ));
        assertTrue(service.beginRecovery("run-1"));
        observeStableLinkSamples(service, "storage-hub-03", NOW.plusSeconds(1));

        assertEquals("FAILED", run.getStatus());
        assertTrue(run.getResultSummary().contains("\"failureReason\":\"调度控制未达到可控范围：FAILED\""));
        assertTrue(run.getResultSummary().contains("\"recoveredLinkStates\""));
        verify(repository, atLeastOnce()).save(run);
    }

    private void assertEnvironmentObserved(
            String scenarioCode,
            String stationId,
            Map<String, Double> metrics,
            double activePower,
            double availableCapacity
    ) {
        ExperimentRunRepository repository = mock(ExperimentRunRepository.class);
        ExperimentClosedLoopOrchestrationService closedLoop = mock(ExperimentClosedLoopOrchestrationService.class);
        ExperimentRunEntity run = environmentRun(scenarioCode, stationId, metrics);
        when(repository.findByRunId("run-1")).thenReturn(Optional.of(run));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ExperimentFaultObservationService service = service(repository, closedLoop);
        for (int sampleIndex = 0; sampleIndex < 3; sampleIndex += 1) {
            service.observeTelemetry(
                    environmentTelemetry(stationId, scenarioCode, metrics, activePower, availableCapacity),
                    LinkState.S0,
                    NOW.plusSeconds(sampleIndex)
            );
            if (sampleIndex < 2) {
                assertEquals("INJECTING", run.getStatus());
            }
        }

        assertEquals("FAULT_OBSERVED", run.getStatus());
        assertTrue(run.getResultSummary().contains("\"consecutiveCount\":3"));
        verify(closedLoop, never()).triggerIfEvidenceObserved(any());
    }

    private ExperimentFaultObservationService service(
            ExperimentRunRepository repository,
            ExperimentClosedLoopOrchestrationService closedLoop
    ) {
        return new ExperimentFaultObservationService(repository, closedLoop, mock(MqttGateway.class),
                new ObjectMapper().findAndRegisterModules(), Clock.fixed(NOW.toInstant(), ZoneId.of("Asia/Shanghai")));
    }

    private void observeStableLinkSamples(
            ExperimentFaultObservationService service,
            String stationId,
            OffsetDateTime startedAt
    ) {
        for (int sampleIndex = 0; sampleIndex < 3; sampleIndex += 1) {
            service.observeLinkState(stationId, LinkState.S0, startedAt.plusSeconds(sampleIndex));
        }
    }

    private void addRejoinedEvidence(ExperimentRunEntity run, String stationId) {
        run.setResultSummary(run.getResultSummary().replace(
                "\"orchestrationStatus\"",
                "\"localAutonomyEvidence\":{\"" + stationId
                        + "\":{\"rejoinedCommunicationGraph\":true}},\"orchestrationStatus\""
        ));
    }

    private ExperimentRunEntity run(String status, String deadline) {
        ExperimentRunEntity run = new ExperimentRunEntity();
        run.setRunId("run-1");
        run.setScenarioCode("COM_OFFLINE");
        run.setStatus(status);
        run.setStartedAt(NOW.minusSeconds(30));
        run.setResultSummary("""
                {"targetStationIds":["storage-hub-03"],"durationSeconds":120,"automaticRecovery":true,"injectionStartedAt":"2026-07-06T12:40:00+08:00","injectionDeadline":"%s","evidenceStatus":"FAULT_OBSERVED","faultEvidenceConfirmed":true,"convergenceStatus":"TARGET_REACHED","observedLinkStates":{},"recoveredLinkStates":{},"recoveryReportStatus":null,"orchestrationStatus":"PENDING"}
                """.formatted(deadline));
        return run;
    }

    private ExperimentRunEntity communicationRun(String scenarioCode, java.util.Map<String, Double> faultParameters) {
        ExperimentRunEntity run = run("INJECTING", "2026-07-06T12:41:00+08:00");
        run.setScenarioCode(scenarioCode);
        run.setResultSummary(run.getResultSummary().replace("storage-hub-03", "wind-ridge-02")
                .replace("\"orchestrationStatus\":\"PENDING\"", "\"faultParameters\":" + faultParametersJson(faultParameters) + ",\"orchestrationStatus\":\"PENDING\""));
        return run;
    }

    private ExperimentRunEntity environmentRun(String scenarioCode, String stationId, Map<String, Double> faultParameters) {
        ExperimentRunEntity run = run("INJECTING", "2026-07-06T12:41:00+08:00");
        run.setScenarioCode(scenarioCode);
        run.setResultSummary(run.getResultSummary().replace("storage-hub-03", stationId)
                .replace("\"orchestrationStatus\":\"PENDING\"", "\"faultParameters\":" + faultParametersJson(faultParameters) + ",\"orchestrationStatus\":\"PENDING\""));
        return run;
    }

    private String faultParametersJson(java.util.Map<String, Double> faultParameters) {
        return faultParameters.entrySet().stream()
                .map(entry -> "\"" + entry.getKey() + "\":" + entry.getValue())
                .collect(java.util.stream.Collectors.joining(",", "{", "}"));
    }

    private TelemetryReportMessage telemetry(String stationId, long sequenceNumber, int latency, String activeFaultCode) {
        return new TelemetryReportMessage(
                "message-" + stationId + "-" + sequenceNumber, stationId, "测试场站", ResourceType.WIND,
                sequenceNumber, NOW, NOW, 1, 1, latency, 0.0, true, "GOOD", LinkState.S1,
                "run-1", activeFaultCode, java.util.Map.of()
        );
    }

    private TelemetryReportMessage environmentTelemetry(
            String stationId,
            String scenarioCode,
            Map<String, Double> metrics,
            double activePower,
            double availableCapacity
    ) {
        return new TelemetryReportMessage(
                "environment-message-" + scenarioCode, stationId, "测试场站", ResourceType.PHOTOVOLTAIC,
                1, NOW, NOW, activePower, availableCapacity, 30, 0.0, true, "GOOD", LinkState.S0,
                "run-1", scenarioCode, metrics
        );
    }
}

package com.weaksignal.vpp.backend.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weaksignal.vpp.backend.api.ApiException;
import com.weaksignal.vpp.backend.api.dto.DispatchPreviewResponse;
import com.weaksignal.vpp.backend.api.dto.DispatchRequest;
import com.weaksignal.vpp.backend.domain.ControlMode;
import com.weaksignal.vpp.backend.domain.DispatchCommandStatus;
import com.weaksignal.vpp.backend.domain.LinkState;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.ControlCommandEntity;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.TelemetrySnapshotEntity;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.CommandAckRepository;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.ControlCommandRepository;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.TelemetrySnapshotRepository;
import com.weaksignal.vpp.backend.mqtt.MqttGateway;
import com.weaksignal.vpp.backend.mqtt.dto.CommandAckMessage;
import com.weaksignal.vpp.backend.mqtt.dto.DispatchControlReleaseMessage;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 规则型调度服务测试。
 */
class DispatchServiceTest {

    private TelemetrySnapshotRepository telemetrySnapshotRepository;
    private ControlCommandRepository controlCommandRepository;
    private CommandAckRepository commandAckRepository;
    private MqttGateway mqttGateway;
    private DispatchService dispatchService;
    private Clock clock;

    @BeforeEach
    void setUp() {
        telemetrySnapshotRepository = mock(TelemetrySnapshotRepository.class);
        controlCommandRepository = mock(ControlCommandRepository.class);
        commandAckRepository = mock(CommandAckRepository.class);
        mqttGateway = mock(MqttGateway.class);
        clock = Clock.fixed(Instant.parse("2026-07-01T02:00:00Z"), ZoneId.of("Asia/Shanghai"));
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        dispatchService = new DispatchService(
                telemetrySnapshotRepository,
                controlCommandRepository,
                commandAckRepository,
                mqttGateway,
                objectMapper,
                clock
        );
        when(commandAckRepository.findByCommandIdOrderByAckTimeAsc(anyString())).thenReturn(List.of());
    }

    @Test
    void shouldGenerateStablePlanWithConfiguredLoadFactors() {
        when(telemetrySnapshotRepository.findAll()).thenReturn(List.of(snapshot("pv-gobi-01", 200, LinkState.S0, true)));

        var firstResult = dispatchService.generatePlan();
        var secondResult = dispatchService.generatePlan();

        assertEquals(firstResult, secondResult);
        assertEquals(24, firstResult.size());
        assertTrue(firstResult.stream().anyMatch(point -> point.loadFactor() == 0.55));
        assertTrue(firstResult.stream().anyMatch(point -> point.loadFactor() == 0.92));
    }

    @Test
    void shouldAllocateByConstrainedCapacity() {
        TelemetrySnapshotEntity stable = snapshot("pv-gobi-01", 100, LinkState.S0, true);
        TelemetrySnapshotEntity degraded = snapshot("wind-ridge-02", 100, LinkState.S1, true);
        when(telemetrySnapshotRepository.findByStationIdIn(any())).thenReturn(List.of(stable, degraded));

        DispatchPreviewResponse response = dispatchService.preview(request(100, List.of("pv-gobi-01", "wind-ridge-02")));

        assertEquals(54.1, response.allocations().get(0).targetPower());
        assertEquals(45.9, response.allocations().get(1).targetPower());
    }

    @Test
    void shouldApplySevereAndLostLinkConstraints() {
        TelemetrySnapshotEntity severe = snapshot("wind-ridge-02", 100, LinkState.S2, true);
        TelemetrySnapshotEntity lost = snapshot("pv-gobi-01", 100, LinkState.S3, true);
        when(telemetrySnapshotRepository.findByStationIdIn(any())).thenReturn(List.of(severe, lost));

        DispatchPreviewResponse response = dispatchService.preview(request(50, List.of("wind-ridge-02", "pv-gobi-01")));

        assertEquals(60, response.allocations().get(0).constrainedCapacity());
        assertTrue(response.allocations().stream().anyMatch(allocation -> !allocation.eligible()));
    }

    @Test
    void shouldRejectTargetAboveConstrainedCapacity() {
        when(telemetrySnapshotRepository.findByStationIdIn(any()))
                .thenReturn(List.of(snapshot("pv-gobi-01", 100, LinkState.S2, true)));

        assertThrows(ApiException.class, () -> dispatchService.preview(request(70, List.of("pv-gobi-01"))));
    }

    @Test
    void shouldMarkPublishedCommandAsSent() {
        when(telemetrySnapshotRepository.findByStationIdIn(any()))
                .thenReturn(List.of(snapshot("pv-gobi-01", 100, LinkState.S0, true)));
        when(mqttGateway.publishDispatchControl(any())).thenReturn(true);

        var result = dispatchService.execute(request(60, List.of("pv-gobi-01")));

        assertEquals(DispatchCommandStatus.SENT.name(), result.commands().get(0).status());
    }

    @Test
    void shouldAdvanceAcknowledgementWithoutTerminalRollback() {
        ControlCommandEntity command = command(DispatchCommandStatus.SENT);
        when(controlCommandRepository.findByCommandId("command-1")).thenReturn(Optional.of(command));
        when(commandAckRepository.existsByCommandIdAndAckStage(anyString(), anyString())).thenReturn(false);
        OffsetDateTime now = OffsetDateTime.now(clock);

        dispatchService.handleAcknowledgement(new CommandAckMessage("command-1", "pv-gobi-01", "EXECUTED", true, "完成", now));
        dispatchService.handleAcknowledgement(new CommandAckMessage("command-1", "pv-gobi-01", "RECEIVED", true, "迟到回执", now.plusSeconds(1)));

        assertEquals(DispatchCommandStatus.EXECUTED.name(), command.getStatus());
    }

    @Test
    void shouldMarkExpiredPendingCommandsAsTimeout() {
        ControlCommandEntity command = command(DispatchCommandStatus.ACCEPTED);
        when(controlCommandRepository.findByStatusInAndExpireAtBefore(any(), any())).thenReturn(List.of(command));

        int updated = dispatchService.markExpiredCommands();

        assertEquals(1, updated);
        assertEquals(DispatchCommandStatus.TIMEOUT.name(), command.getStatus());
    }

    @Test
    void shouldReleaseControlsForEveryStationInPlan() {
        ControlCommandEntity photovoltaicCommand = command(DispatchCommandStatus.EXECUTED);
        photovoltaicCommand.setDispatchPlanId("plan-1");
        ControlCommandEntity windCommand = command(DispatchCommandStatus.EXECUTED);
        windCommand.setCommandId("command-2");
        windCommand.setStationId("wind-ridge-02");
        windCommand.setDispatchPlanId("plan-1");
        when(controlCommandRepository.findByDispatchPlanIdOrderByCreatedAtAsc("plan-1"))
                .thenReturn(List.of(photovoltaicCommand, windCommand));
        when(mqttGateway.publishDispatchControlRelease(any())).thenReturn(true);

        boolean released = dispatchService.releasePlannedControls("plan-1");

        ArgumentCaptor<DispatchControlReleaseMessage> messageCaptor =
                ArgumentCaptor.forClass(DispatchControlReleaseMessage.class);
        verify(mqttGateway, org.mockito.Mockito.times(2)).publishDispatchControlRelease(messageCaptor.capture());
        assertTrue(released);
        assertTrue(messageCaptor.getAllValues().stream()
                .allMatch(message -> "plan-1".equals(message.dispatchPlanId())));
    }

    @Test
    void shouldIgnoreUnknownAcknowledgement() {
        when(controlCommandRepository.findByCommandId("unknown")).thenReturn(Optional.empty());

        dispatchService.handleAcknowledgement(new CommandAckMessage(
                "unknown",
                "pv-gobi-01",
                "EXECUTED",
                true,
                "完成",
                OffsetDateTime.now(clock)
        ));

        verify(commandAckRepository, never()).save(any());
    }

    private DispatchRequest request(double targetPower, List<String> stationIds) {
        return new DispatchRequest(
                targetPower,
                ControlMode.EDGE_COORDINATION,
                stationIds,
                0,
                200,
                18,
                30,
                true
        );
    }

    private TelemetrySnapshotEntity snapshot(String stationId, double capacity, LinkState linkState, boolean online) {
        TelemetrySnapshotEntity snapshot = new TelemetrySnapshotEntity();
        snapshot.setStationId(stationId);
        snapshot.setActivePower(capacity * 0.4);
        snapshot.setAvailableCapacity(capacity);
        snapshot.setLinkState(linkState);
        snapshot.setOnline(online);
        return snapshot;
    }

    private ControlCommandEntity command(DispatchCommandStatus status) {
        ControlCommandEntity command = new ControlCommandEntity();
        command.setCommandId("command-1");
        command.setStationId("pv-gobi-01");
        command.setCommandType("SET_ACTIVE_POWER");
        command.setCommandPayload("{}");
        command.setStatus(status.name());
        command.setSendTime(OffsetDateTime.now(clock));
        command.setExpireAt(OffsetDateTime.now(clock).plusSeconds(30));
        command.setCreatedAt(OffsetDateTime.now(clock));
        return command;
    }
}

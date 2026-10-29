package com.weaksignal.vpp.backend.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weaksignal.vpp.backend.config.MpcConvergenceProperties;
import com.weaksignal.vpp.backend.api.dto.DispatchContextSnapshotResponse;
import com.weaksignal.vpp.backend.api.dto.DispatchPlanCreateRequest;
import com.weaksignal.vpp.backend.api.dto.DispatchPlanResponse;
import com.weaksignal.vpp.backend.api.dto.MarketAgentRunResponse;
import com.weaksignal.vpp.backend.domain.DispatchCommandStatus;
import com.weaksignal.vpp.backend.domain.DispatchConvergenceStatus;
import com.weaksignal.vpp.backend.domain.ResourceType;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.ControlCommandEntity;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.DispatchConvergenceRoundEntity;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.ExperimentRunEntity;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.StationEntity;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.TelemetrySnapshotEntity;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.ControlCommandRepository;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.DispatchConvergenceRoundRepository;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.ExperimentRunRepository;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.StationRepository;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.TelemetrySnapshotRepository;
import com.weaksignal.vpp.backend.marketagent.MarketSafetyDecision;
import com.weaksignal.vpp.backend.marketdispatch.OptimizationResult;
import com.weaksignal.vpp.backend.marketdispatch.OptimizationStatus;
import com.weaksignal.vpp.backend.planning.DispatchContextSnapshotService;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** MPC 执行后遥测收敛判定测试。 */
class DispatchConvergenceServiceTest {

    /** 无符号总和相等但实际供需不平衡时，不得判定目标已收敛。 */
    @Test
    void shouldNotReachTargetWhenSignedSupplyDemandIsUnbalanced() {
        OffsetDateTime now = OffsetDateTime.parse("2026-07-27T18:00:10+08:00");
        DispatchConvergenceRoundRepository roundRepository = mock(DispatchConvergenceRoundRepository.class);
        ControlCommandRepository commandRepository = mock(ControlCommandRepository.class);
        TelemetrySnapshotRepository telemetryRepository = mock(TelemetrySnapshotRepository.class);
        StationRepository stationRepository = mock(StationRepository.class);
        DispatchConvergenceRoundEntity round = round(now.minusSeconds(10));
        when(roundRepository.findFirstByExperimentRunIdOrderByRoundNumberDesc("run-1")).thenReturn(Optional.of(round));
        when(roundRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(commandRepository.findByDispatchPlanIdOrderByCreatedAtAsc("plan-1")).thenReturn(List.of(
                command("grid", 100, now.minusSeconds(5)),
                command("load", 100, now.minusSeconds(5))
        ));
        when(telemetryRepository.findByStationIdIn(any())).thenReturn(List.of(
                telemetry("grid", 101, now.minusSeconds(1)),
                telemetry("load", 99, now.minusSeconds(1))
        ));
        when(stationRepository.findByStationId("grid")).thenReturn(Optional.of(station("grid", ResourceType.GRID)));
        when(stationRepository.findByStationId("load")).thenReturn(Optional.of(station("load", ResourceType.LOAD)));
        when(stationRepository.findAll()).thenReturn(List.of(
                station("grid", ResourceType.GRID), station("load", ResourceType.LOAD)));
        MpcConvergenceProperties properties = new MpcConvergenceProperties(1, 1, 2, 1,
                Duration.ofSeconds(45), Duration.ZERO, Duration.ofMinutes(2), Duration.ofHours(1),
                3, Duration.ofMinutes(10), Duration.ofSeconds(30), Duration.ofSeconds(30));
        DispatchConvergenceService service = new DispatchConvergenceService(roundRepository,
                mock(ExperimentRunRepository.class), commandRepository, telemetryRepository, stationRepository,
                mock(DispatchContextSnapshotService.class), mock(MarketDispatchPlanningService.class),
                mock(MarketAgentOrchestrationService.class), mock(SolverObservabilityService.class),
                new ObjectMapper().findAndRegisterModules(), Clock.fixed(now.toInstant(), ZoneId.of("Asia/Shanghai")), properties);

        service.observeTelemetry("run-1", "plan-1", "snapshot-1", "agent-1", "batch-1");

        assertEquals(DispatchConvergenceStatus.TRACKING.name(), round.getStatus());
        assertEquals(0, round.getConsecutiveSuccessCount());
    }

    /**
     * 达到目标时应将遥测计算出的供需误差写入实验摘要。
     *
     * @throws Exception JSON 解析异常
     */
    @Test
    void shouldPersistActualBalanceErrorWhenTargetIsReached() throws Exception {
        OffsetDateTime now = OffsetDateTime.parse("2026-07-27T18:00:10+08:00");
        DispatchConvergenceRoundRepository roundRepository = mock(DispatchConvergenceRoundRepository.class);
        ExperimentRunRepository experimentRunRepository = mock(ExperimentRunRepository.class);
        ControlCommandRepository commandRepository = mock(ControlCommandRepository.class);
        TelemetrySnapshotRepository telemetryRepository = mock(TelemetrySnapshotRepository.class);
        StationRepository stationRepository = mock(StationRepository.class);
        DispatchConvergenceRoundEntity round = round(now.minusSeconds(10));
        ExperimentRunEntity run = new ExperimentRunEntity();
        run.setRunId("run-1");
        run.setResultSummary("{\"existingEvidence\":true}");
        when(roundRepository.findFirstByExperimentRunIdOrderByRoundNumberDesc("run-1")).thenReturn(Optional.of(round));
        when(roundRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(experimentRunRepository.findByRunId("run-1")).thenReturn(Optional.of(run));
        when(experimentRunRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(commandRepository.findByDispatchPlanIdOrderByCreatedAtAsc("plan-1"))
                .thenReturn(List.of(command("grid", 100, now.minusSeconds(5))));
        when(telemetryRepository.findByStationIdIn(any())).thenReturn(List.of(
                telemetry("grid", 100, now.minusSeconds(1)),
                telemetry("load", 100, now.minusSeconds(1))
        ));
        when(stationRepository.findByStationId("grid")).thenReturn(Optional.of(station("grid", ResourceType.GRID)));
        when(stationRepository.findByStationId("load")).thenReturn(Optional.of(station("load", ResourceType.LOAD)));
        when(stationRepository.findAll()).thenReturn(List.of(
                station("grid", ResourceType.GRID), station("load", ResourceType.LOAD)));
        MpcConvergenceProperties properties = new MpcConvergenceProperties(1, 1, 2, 1,
                Duration.ofSeconds(45), Duration.ZERO, Duration.ofMinutes(2), Duration.ofHours(1),
                3, Duration.ofMinutes(10), Duration.ofSeconds(30), Duration.ofSeconds(30));
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        DispatchConvergenceService service = new DispatchConvergenceService(roundRepository,
                experimentRunRepository, commandRepository, telemetryRepository, stationRepository,
                mock(DispatchContextSnapshotService.class), mock(MarketDispatchPlanningService.class),
                mock(MarketAgentOrchestrationService.class), mock(SolverObservabilityService.class),
                objectMapper, Clock.fixed(now.toInstant(), ZoneId.of("Asia/Shanghai")), properties);

        service.observeTelemetry("run-1", "plan-1", "snapshot-1", "agent-1", "batch-1");

        assertEquals(DispatchConvergenceStatus.TARGET_REACHED.name(), round.getStatus());
        assertEquals(0.0, objectMapper.readTree(run.getResultSummary())
                .path("postDispatchTelemetryValidation").path("actualBalanceErrorKw").asDouble());
    }

    /** 命令执行后的稳定等待期内不得消费重调度轮次。 */
    @Test
    void shouldWaitForExecutionStabilizationBeforeEvaluatingTelemetry() {
        OffsetDateTime now = OffsetDateTime.parse("2026-07-27T18:00:10+08:00");
        DispatchConvergenceRoundRepository roundRepository = mock(DispatchConvergenceRoundRepository.class);
        ControlCommandRepository commandRepository = mock(ControlCommandRepository.class);
        TelemetrySnapshotRepository telemetryRepository = mock(TelemetrySnapshotRepository.class);
        DispatchConvergenceRoundEntity round = round(now.minusSeconds(30));
        when(roundRepository.findFirstByExperimentRunIdOrderByRoundNumberDesc("run-1")).thenReturn(Optional.of(round));
        when(roundRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(commandRepository.findByDispatchPlanIdOrderByCreatedAtAsc("plan-1"))
                .thenReturn(List.of(command("grid", 100, now.minusSeconds(5))));
        MpcConvergenceProperties properties = new MpcConvergenceProperties(1, 1, 2, 3,
                Duration.ofSeconds(45), Duration.ofSeconds(15), Duration.ofMinutes(2), Duration.ofSeconds(15),
                3, Duration.ofMinutes(10), Duration.ofSeconds(30), Duration.ofSeconds(15));
        DispatchConvergenceService service = new DispatchConvergenceService(roundRepository,
                mock(ExperimentRunRepository.class), commandRepository, telemetryRepository, mock(StationRepository.class),
                mock(DispatchContextSnapshotService.class), mock(MarketDispatchPlanningService.class),
                mock(MarketAgentOrchestrationService.class), mock(SolverObservabilityService.class),
                new ObjectMapper().findAndRegisterModules(), Clock.fixed(now.toInstant(), ZoneId.of("Asia/Shanghai")), properties);

        service.observeTelemetry("run-1", "plan-1", "snapshot-1", "agent-1", "batch-1");

        assertEquals(DispatchConvergenceStatus.WAITING_TELEMETRY.name(), round.getStatus());
        assertEquals(1, round.getRoundNumber());
        verify(telemetryRepository, never()).findByStationIdIn(any());
    }

    /** 稳定等待结束后的观测窗口内出现瞬时误差时，不得立即消耗滚动重调度轮次。 */
    @Test
    void shouldObserveTelemetryBeforeStartingRollingReoptimization() {
        OffsetDateTime now = OffsetDateTime.parse("2026-07-27T18:00:30+08:00");
        DispatchConvergenceRoundRepository roundRepository = mock(DispatchConvergenceRoundRepository.class);
        ControlCommandRepository commandRepository = mock(ControlCommandRepository.class);
        TelemetrySnapshotRepository telemetryRepository = mock(TelemetrySnapshotRepository.class);
        StationRepository stationRepository = mock(StationRepository.class);
        DispatchContextSnapshotService snapshotService = mock(DispatchContextSnapshotService.class);
        DispatchConvergenceRoundEntity round = round(now.minusMinutes(1));
        when(roundRepository.findFirstByExperimentRunIdOrderByRoundNumberDesc("run-1")).thenReturn(Optional.of(round));
        when(roundRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(commandRepository.findByDispatchPlanIdOrderByCreatedAtAsc("plan-1"))
                .thenReturn(List.of(command("grid", 100, now.minusSeconds(20))));
        when(telemetryRepository.findByStationIdIn(any())).thenReturn(List.of(
                telemetry("grid", 102, now.minusSeconds(1)),
                telemetry("load", 100, now.minusSeconds(1))
        ));
        when(stationRepository.findByStationId("grid")).thenReturn(Optional.of(station("grid", ResourceType.GRID)));
        when(stationRepository.findByStationId("load")).thenReturn(Optional.of(station("load", ResourceType.LOAD)));
        when(stationRepository.findAll()).thenReturn(List.of(
                station("grid", ResourceType.GRID), station("load", ResourceType.LOAD)));
        MpcConvergenceProperties properties = new MpcConvergenceProperties(1, 1, 2, 3,
                Duration.ofSeconds(45), Duration.ofSeconds(15), Duration.ofMinutes(2), Duration.ofSeconds(15),
                3, Duration.ofMinutes(10), Duration.ofSeconds(30), Duration.ofSeconds(15));
        DispatchConvergenceService service = new DispatchConvergenceService(roundRepository,
                mock(ExperimentRunRepository.class), commandRepository, telemetryRepository, stationRepository,
                snapshotService, mock(MarketDispatchPlanningService.class),
                mock(MarketAgentOrchestrationService.class), mock(SolverObservabilityService.class),
                new ObjectMapper().findAndRegisterModules(), Clock.fixed(now.toInstant(), ZoneId.of("Asia/Shanghai")), properties);

        service.observeTelemetry("run-1", "plan-1", "snapshot-1", "agent-1", "batch-1");

        assertEquals(DispatchConvergenceStatus.TRACKING.name(), round.getStatus());
        assertEquals(1, round.getRoundNumber());
        verify(snapshotService, never()).createExperimentSnapshot();
    }

    /**
     * 自适应实验滚动重调度时必须继续使用分布式优化结果。
     */
    @Test
    void shouldKeepAdaptiveStrategyDuringRollingReoptimization() {
        OffsetDateTime now = OffsetDateTime.parse("2026-07-27T18:00:10+08:00");
        DispatchConvergenceRoundRepository roundRepository = mock(DispatchConvergenceRoundRepository.class);
        ExperimentRunRepository experimentRunRepository = mock(ExperimentRunRepository.class);
        ControlCommandRepository commandRepository = mock(ControlCommandRepository.class);
        TelemetrySnapshotRepository telemetryRepository = mock(TelemetrySnapshotRepository.class);
        StationRepository stationRepository = mock(StationRepository.class);
        DispatchContextSnapshotService snapshotService = mock(DispatchContextSnapshotService.class);
        MarketDispatchPlanningService planningService = mock(MarketDispatchPlanningService.class);
        MarketAgentOrchestrationService agentService = mock(MarketAgentOrchestrationService.class);
        SolverObservabilityService observabilityService = mock(SolverObservabilityService.class);
        DistributedExperimentControlService distributedService = mock(DistributedExperimentControlService.class);
        DispatchConvergenceRoundEntity round = round(now.minusSeconds(10));
        ExperimentRunEntity run = new ExperimentRunEntity();
        run.setRunId("run-1");
        run.setScenarioCode("T0_STABLE_BASELINE");
        run.setResultSummary("{\"controlStrategy\":\"ADAPTIVE\"}");
        when(roundRepository.findFirstByExperimentRunIdOrderByRoundNumberDesc("run-1")).thenReturn(Optional.of(round));
        when(roundRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(experimentRunRepository.findByRunId("run-1")).thenReturn(Optional.of(run));
        when(experimentRunRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(commandRepository.findByDispatchPlanIdOrderByCreatedAtAsc("plan-1"))
                .thenReturn(List.of(command("grid", 100, now.minusSeconds(5))));
        when(telemetryRepository.findByStationIdIn(any())).thenReturn(List.of(
                telemetry("grid", 110, now.minusSeconds(1)),
                telemetry("load", 100, now.minusSeconds(1))
        ));
        when(stationRepository.findAll()).thenReturn(List.of(
                station("grid", ResourceType.GRID), station("load", ResourceType.LOAD)));
        when(stationRepository.findByStationId("grid")).thenReturn(Optional.of(station("grid", ResourceType.GRID)));
        when(stationRepository.findByStationId("load")).thenReturn(Optional.of(station("load", ResourceType.LOAD)));
        DispatchContextSnapshotResponse snapshot = mock(DispatchContextSnapshotResponse.class);
        when(snapshot.snapshotId()).thenReturn("snapshot-2");
        when(snapshotService.createExperimentSnapshot()).thenReturn(snapshot);
        OptimizationResult optimizationResult = mock(OptimizationResult.class);
        when(optimizationResult.status()).thenReturn(OptimizationStatus.FEASIBLE);
        when(distributedService.optimize(run, snapshot)).thenReturn(optimizationResult);
        DispatchPlanResponse plan = mock(DispatchPlanResponse.class);
        when(plan.planId()).thenReturn("plan-2");
        when(planningService.createFromOptimizationResult(any(DispatchPlanCreateRequest.class), any(OptimizationResult.class)))
                .thenReturn(plan);
        when(agentService.analyze(any())).thenReturn(new MarketAgentRunResponse(
                "agent-2", "snapshot-2", "plan-2", false, List.of(),
                new MarketSafetyDecision("REJECTED", false, false, List.of(), "测试拒绝"), now
        ));
        MpcConvergenceProperties properties = new MpcConvergenceProperties(1, 1, 2, 3,
                Duration.ofSeconds(45), Duration.ZERO, Duration.ofMinutes(2), Duration.ZERO,
                3, Duration.ofMinutes(10), Duration.ofSeconds(30), Duration.ofSeconds(30));
        DispatchConvergenceService service = new DispatchConvergenceService(roundRepository,
                experimentRunRepository, commandRepository, telemetryRepository, stationRepository,
                snapshotService, planningService, agentService, observabilityService,
                new ObjectMapper().findAndRegisterModules(), Clock.fixed(now.toInstant(), ZoneId.of("Asia/Shanghai")), properties);
        service.setDistributedExperimentControlService(distributedService);

        service.observeTelemetry("run-1", "plan-1", "snapshot-1", "agent-1", "batch-1");

        verify(distributedService).optimize(run, snapshot);
        verify(planningService).createFromOptimizationResult(any(DispatchPlanCreateRequest.class),
                any(OptimizationResult.class));
        verify(planningService, never()).create(any(DispatchPlanCreateRequest.class));
    }

    private DispatchConvergenceRoundEntity round(OffsetDateTime startedAt) {
        DispatchConvergenceRoundEntity round = new DispatchConvergenceRoundEntity();
        round.setRoundId("round-1"); round.setExperimentRunId("run-1"); round.setRoundNumber(1);
        round.setSourcePlanId("plan-1"); round.setReplacementPlanId("plan-1"); round.setExecutionBatchId("batch-1");
        round.setStatus(DispatchConvergenceStatus.WAITING_TELEMETRY.name()); round.setTriggerReason("INITIAL_EXECUTION");
        round.setConsecutiveSuccessCount(0); round.setStartedAt(startedAt); round.setAuditJson("{\"events\":[]}");
        return round;
    }

    private ControlCommandEntity command(String stationId, double targetPower, OffsetDateTime sendTime) {
        ControlCommandEntity command = new ControlCommandEntity();
        command.setCommandId("command-" + stationId); command.setStationId(stationId); command.setDispatchPlanId("plan-1");
        command.setStatus(DispatchCommandStatus.EXECUTED.name()); command.setCommandPayload("{\"targetPower\":" + targetPower + "}");
        command.setSendTime(sendTime); command.setExpireAt(sendTime.plusMinutes(1)); command.setCreatedAt(sendTime);
        return command;
    }

    private TelemetrySnapshotEntity telemetry(String stationId, double activePower, OffsetDateTime receivedAt) {
        TelemetrySnapshotEntity telemetry = new TelemetrySnapshotEntity();
        telemetry.setStationId(stationId); telemetry.setActivePower(activePower); telemetry.setReceivedTimestamp(receivedAt);
        return telemetry;
    }

    private StationEntity station(String stationId, ResourceType resourceType) {
        StationEntity station = new StationEntity(); station.setStationId(stationId); station.setResourceType(resourceType);
        station.setEnabled(true); return station;
    }
}

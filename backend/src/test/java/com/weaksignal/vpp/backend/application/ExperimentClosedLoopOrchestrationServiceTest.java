package com.weaksignal.vpp.backend.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weaksignal.vpp.backend.api.ApiException;
import com.weaksignal.vpp.backend.api.dto.DispatchContextSnapshotResponse;
import com.weaksignal.vpp.backend.api.dto.DispatchPlanCreateRequest;
import com.weaksignal.vpp.backend.api.dto.DispatchPlanResponse;
import com.weaksignal.vpp.backend.api.dto.MarketAgentRunResponse;
import com.weaksignal.vpp.backend.domain.DispatchPlanStatus;
import com.weaksignal.vpp.backend.domain.LinkState;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.ExperimentRunEntity;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.TelemetryHistoryEntity;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.ExperimentRunRepository;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.TelemetryHistoryRepository;
import com.weaksignal.vpp.backend.marketagent.MarketSafetyDecision;
import com.weaksignal.vpp.backend.marketdispatch.OptimizationResult;
import com.weaksignal.vpp.backend.marketdispatch.OptimizationStatus;
import com.weaksignal.vpp.backend.planning.DispatchContextSnapshotService;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;

/**
 * 实验闭环重调度编排服务测试。
 */
class ExperimentClosedLoopOrchestrationServiceTest {

    @Test
    void shouldTriggerClosedLoopAfterFaultEvidenceIsObserved() {
        ExperimentRunRepository experimentRunRepository = mock(ExperimentRunRepository.class);
        TelemetryHistoryRepository telemetryHistoryRepository = mock(TelemetryHistoryRepository.class);
        DispatchContextSnapshotService dispatchContextSnapshotService = mock(DispatchContextSnapshotService.class);
        MarketDispatchPlanningService marketDispatchPlanningService = mock(MarketDispatchPlanningService.class);
        MarketAgentOrchestrationService marketAgentOrchestrationService = mock(MarketAgentOrchestrationService.class);

        ExperimentRunEntity runEntity = runningExperiment(summaryJson(List.of("pv-gobi-01", "wind-ridge-02")));
        when(experimentRunRepository.findByRunId("run-1")).thenReturn(Optional.of(runEntity));
        when(experimentRunRepository.save(any(ExperimentRunEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, ExperimentRunEntity.class));
        DispatchContextSnapshotResponse snapshotResponse = mock(DispatchContextSnapshotResponse.class);
        when(snapshotResponse.snapshotId()).thenReturn("snapshot-1");
        when(dispatchContextSnapshotService.createExperimentSnapshot()).thenReturn(snapshotResponse);

        DispatchPlanResponse draftPlan = mock(DispatchPlanResponse.class);
        when(draftPlan.planId()).thenReturn("plan-1");
        when(draftPlan.status()).thenReturn(DispatchPlanStatus.DRAFT);
        when(marketDispatchPlanningService.create(any())).thenReturn(draftPlan);

        MarketAgentRunResponse agentRunResponse = new MarketAgentRunResponse(
                "agent-run-1",
                "snapshot-1",
                "plan-1",
                false,
                List.of(),
                new MarketSafetyDecision("PASSED", true, false, List.of("KEEP_EDGE_COORDINATION"), "通过安全门"),
                OffsetDateTime.parse("2026-07-06T12:40:12+08:00"),
                "sequential-v2",
                "market-agent-set-v2",
                true,
                false
        );
        when(marketAgentOrchestrationService.analyze(any())).thenReturn(agentRunResponse);

        DispatchPlanResponse activePlan = mock(DispatchPlanResponse.class);
        when(activePlan.status()).thenReturn(DispatchPlanStatus.ACTIVE);
        when(marketDispatchPlanningService.getPlan("plan-1")).thenReturn(activePlan);
        when(marketDispatchPlanningService.executeNext("plan-1"))
                .thenReturn(new com.weaksignal.vpp.backend.api.dto.DispatchPlanExecutionResponse(
                        "plan-1",
                        "dispatch-plan-v1",
                        DispatchPlanStatus.ACTIVE,
                        "batch-1",
                        1,
                        0,
                        OffsetDateTime.parse("2026-07-06T12:40:13+08:00"),
                        List.of()
                ));

        ExperimentClosedLoopOrchestrationService service = new ExperimentClosedLoopOrchestrationService(
                experimentRunRepository,
                telemetryHistoryRepository,
                dispatchContextSnapshotService,
                marketDispatchPlanningService,
                marketAgentOrchestrationService,
                new ObjectMapper().findAndRegisterModules(),
                Clock.fixed(OffsetDateTime.parse("2026-07-06T12:40:12+08:00").toInstant(), ZoneId.of("Asia/Shanghai"))
        );

        service.triggerIfEvidenceObserved("run-1");

        verify(dispatchContextSnapshotService).createExperimentSnapshot();
        verify(marketDispatchPlanningService).create(
                new DispatchPlanCreateRequest("snapshot-1", true, "T5_FULL_LOSS_AUTONOMY")
        );
        verify(marketAgentOrchestrationService).analyze(any());
        verify(marketDispatchPlanningService).validate("plan-1");
        verify(marketDispatchPlanningService).approve("plan-1");
        verify(marketDispatchPlanningService).activate("plan-1");
        verify(marketDispatchPlanningService).executeNext("plan-1");
    }

    @Test
    void shouldSkipClosedLoopBeforeFaultEvidenceIsObserved() {
        ExperimentRunRepository experimentRunRepository = mock(ExperimentRunRepository.class);
        TelemetryHistoryRepository telemetryHistoryRepository = mock(TelemetryHistoryRepository.class);
        DispatchContextSnapshotService dispatchContextSnapshotService = mock(DispatchContextSnapshotService.class);
        MarketDispatchPlanningService marketDispatchPlanningService = mock(MarketDispatchPlanningService.class);
        MarketAgentOrchestrationService marketAgentOrchestrationService = mock(MarketAgentOrchestrationService.class);

        ExperimentRunEntity runEntity = runningExperiment(summaryJson(List.of("pv-gobi-01", "wind-ridge-02")));
        runEntity.setStatus("INJECTING");
        when(experimentRunRepository.findByRunId("run-1")).thenReturn(Optional.of(runEntity));

        ExperimentClosedLoopOrchestrationService service = new ExperimentClosedLoopOrchestrationService(
                experimentRunRepository,
                telemetryHistoryRepository,
                dispatchContextSnapshotService,
                marketDispatchPlanningService,
                marketAgentOrchestrationService,
                new ObjectMapper().findAndRegisterModules(),
                Clock.fixed(OffsetDateTime.parse("2026-07-06T12:40:12+08:00").toInstant(), ZoneId.of("Asia/Shanghai"))
        );

        service.triggerIfEvidenceObserved("run-1");

        verify(dispatchContextSnapshotService, never()).createExperimentSnapshot();
        verify(marketDispatchPlanningService, never()).create(any());
        verify(marketAgentOrchestrationService, never()).analyze(any());
    }

    @Test
    void shouldRouteAdaptiveStrategyThroughPersistedDistributedOptimizationResult() {
        ExperimentRunRepository repository = mock(ExperimentRunRepository.class);
        TelemetryHistoryRepository telemetryRepository = mock(TelemetryHistoryRepository.class);
        DispatchContextSnapshotService snapshotService = mock(DispatchContextSnapshotService.class);
        MarketDispatchPlanningService planningService = mock(MarketDispatchPlanningService.class);
        MarketAgentOrchestrationService agentService = mock(MarketAgentOrchestrationService.class);
        DistributedExperimentControlService distributedService = mock(DistributedExperimentControlService.class);
        String distributedSummary = summaryJson(List.of("storage-hub-03")).replace(
                "\"expectedLinkState\"",
                "\"controlStrategy\":\"ADAPTIVE\",\"randomSeed\":101,\"expectedLinkState\""
        );
        ExperimentRunEntity run = runningExperiment(distributedSummary);
        when(repository.findByRunId("run-1")).thenReturn(Optional.of(run));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        DispatchContextSnapshotResponse snapshot = mock(DispatchContextSnapshotResponse.class);
        when(snapshot.snapshotId()).thenReturn("snapshot-distributed");
        when(snapshotService.createExperimentSnapshot()).thenReturn(snapshot);
        OptimizationResult optimizationResult = mock(OptimizationResult.class);
        when(optimizationResult.status()).thenReturn(OptimizationStatus.FEASIBLE);
        when(optimizationResult.optimizationRound()).thenReturn(32);
        when(distributedService.optimize(run, snapshot)).thenReturn(optimizationResult);
        DispatchPlanResponse distributedPlan = plan("plan-distributed", DispatchPlanStatus.DRAFT);
        when(planningService.createFromOptimizationResult(any(), any())).thenReturn(distributedPlan);
        when(agentService.analyze(any())).thenReturn(rejectedAgentRun("snapshot-distributed", "plan-distributed"));
        ExperimentClosedLoopOrchestrationService orchestrationService = service(
                repository, telemetryRepository, snapshotService, planningService, agentService
        );
        orchestrationService.setDistributedExperimentControlService(distributedService);

        orchestrationService.triggerIfEvidenceObserved("run-1");

        verify(distributedService).optimize(run, snapshot);
        verify(planningService).createFromOptimizationResult(any(DispatchPlanCreateRequest.class),
                org.mockito.ArgumentMatchers.same(optimizationResult));
        verify(planningService, never()).create(any());
        verify(planningService, never()).executeNext(any());
    }

    @Test
    void shouldNotDispatchControlWhenSafetyGateRejectsPlan() {
        ExperimentRunRepository repository = mock(ExperimentRunRepository.class);
        TelemetryHistoryRepository telemetryRepository = mock(TelemetryHistoryRepository.class);
        DispatchContextSnapshotService snapshotService = mock(DispatchContextSnapshotService.class);
        MarketDispatchPlanningService planningService = mock(MarketDispatchPlanningService.class);
        MarketAgentOrchestrationService agentService = mock(MarketAgentOrchestrationService.class);
        ExperimentRunEntity run = runningExperiment(summaryJson(List.of("storage-hub-03")));
        when(repository.findByRunId("run-1")).thenReturn(Optional.of(run));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        DispatchContextSnapshotResponse snapshot = mock(DispatchContextSnapshotResponse.class);
        when(snapshot.snapshotId()).thenReturn("snapshot-1");
        when(snapshotService.createExperimentSnapshot()).thenReturn(snapshot);
        DispatchPlanResponse draftPlan = plan("plan-1", DispatchPlanStatus.DRAFT);
        when(planningService.create(any())).thenReturn(draftPlan);
        when(agentService.analyze(any())).thenReturn(rejectedAgentRun("snapshot-1", "plan-1"));

        service(repository, telemetryRepository, snapshotService, planningService, agentService).triggerIfEvidenceObserved("run-1");

        verify(planningService, never()).validate("plan-1");
        verify(planningService, never()).approve("plan-1");
        verify(planningService, never()).activate("plan-1");
        verify(planningService, never()).executeNext("plan-1");
        org.junit.jupiter.api.Assertions.assertTrue(run.getResultSummary().contains("\"safetyGatePassed\":false"));
        org.junit.jupiter.api.Assertions.assertTrue(run.getResultSummary().contains("\"executionBatchId\":null"));
    }

    @Test
    void shouldCreateAndExecuteOnlyReplacementPlanAfterFirstExpiration() {
        ExperimentRunRepository repository = mock(ExperimentRunRepository.class);
        TelemetryHistoryRepository telemetryRepository = mock(TelemetryHistoryRepository.class);
        DispatchContextSnapshotService snapshotService = mock(DispatchContextSnapshotService.class);
        MarketDispatchPlanningService planningService = mock(MarketDispatchPlanningService.class);
        MarketAgentOrchestrationService agentService = mock(MarketAgentOrchestrationService.class);
        ExperimentRunEntity run = runningExperiment(summaryJson(List.of("storage-hub-03")));
        when(repository.findByRunId("run-1")).thenReturn(Optional.of(run));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        DispatchContextSnapshotResponse firstSnapshot = mock(DispatchContextSnapshotResponse.class);
        DispatchContextSnapshotResponse replacementSnapshot = mock(DispatchContextSnapshotResponse.class);
        when(firstSnapshot.snapshotId()).thenReturn("snapshot-1");
        when(replacementSnapshot.snapshotId()).thenReturn("snapshot-2");
        when(snapshotService.createExperimentSnapshot()).thenReturn(firstSnapshot, replacementSnapshot);
        DispatchPlanResponse firstPlan = plan("plan-1", DispatchPlanStatus.DRAFT);
        DispatchPlanResponse replacementPlan = plan("plan-2", DispatchPlanStatus.DRAFT);
        when(planningService.create(any())).thenReturn(firstPlan, replacementPlan);
        when(agentService.analyze(any())).thenReturn(agentRun("snapshot-1", "plan-1"), agentRun("snapshot-2", "plan-2"));
        OffsetDateTime detectedAt = OffsetDateTime.parse("2026-07-06T12:40:15+08:00");
        when(planningService.activate("plan-1")).thenThrow(expired("plan-1", detectedAt));
        DispatchPlanResponse activeReplacement = plan("plan-2", DispatchPlanStatus.ACTIVE);
        when(planningService.getPlan("plan-2")).thenReturn(activeReplacement);
        when(planningService.executeNext("plan-2")).thenReturn(new com.weaksignal.vpp.backend.api.dto.DispatchPlanExecutionResponse(
                "plan-2", "dispatch-plan-v2", DispatchPlanStatus.ACTIVE, "batch-2", 1, 0, detectedAt, List.of()));

        service(repository, telemetryRepository, snapshotService, planningService, agentService).triggerIfEvidenceObserved("run-1");

        verify(snapshotService, org.mockito.Mockito.times(2)).createExperimentSnapshot();
        verify(planningService, never()).executeNext("plan-1");
        verify(planningService).executeNext("plan-2");
        org.junit.jupiter.api.Assertions.assertTrue(run.getResultSummary().contains("\"orchestrationRetryCount\":1"));
        org.junit.jupiter.api.Assertions.assertTrue(run.getResultSummary().contains("\"detectedAt\""));
    }

    @Test
    void shouldRetryAfterDispatchInputExpiresAndKeepReplacementAgentRun() {
        ExperimentRunRepository repository = mock(ExperimentRunRepository.class);
        TelemetryHistoryRepository telemetryRepository = mock(TelemetryHistoryRepository.class);
        DispatchContextSnapshotService snapshotService = mock(DispatchContextSnapshotService.class);
        MarketDispatchPlanningService planningService = mock(MarketDispatchPlanningService.class);
        MarketAgentOrchestrationService agentService = mock(MarketAgentOrchestrationService.class);
        ExperimentRunEntity run = runningExperiment(summaryJson(List.of("storage-hub-03")));
        when(repository.findByRunId("run-1")).thenReturn(Optional.of(run));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        DispatchContextSnapshotResponse firstSnapshot = mock(DispatchContextSnapshotResponse.class);
        DispatchContextSnapshotResponse replacementSnapshot = mock(DispatchContextSnapshotResponse.class);
        when(firstSnapshot.snapshotId()).thenReturn("snapshot-1");
        when(replacementSnapshot.snapshotId()).thenReturn("snapshot-2");
        when(snapshotService.createExperimentSnapshot()).thenReturn(firstSnapshot, replacementSnapshot);
        DispatchPlanResponse firstPlan = plan("plan-1", DispatchPlanStatus.DRAFT);
        DispatchPlanResponse replacementPlan = plan("plan-2", DispatchPlanStatus.DRAFT);
        when(planningService.create(any())).thenReturn(firstPlan, replacementPlan);
        when(agentService.analyze(any())).thenReturn(agentRun("snapshot-1", "plan-1"), agentRun("snapshot-2", "plan-2"));
        OffsetDateTime detectedAt = OffsetDateTime.parse("2026-07-06T12:40:15+08:00");
        when(planningService.activate("plan-1")).thenThrow(inputExpired("plan-1", "snapshot-1", detectedAt));
        DispatchPlanResponse activeReplacement = plan("plan-2", DispatchPlanStatus.ACTIVE);
        when(planningService.getPlan("plan-2")).thenReturn(activeReplacement);
        when(planningService.executeNext("plan-2")).thenReturn(new com.weaksignal.vpp.backend.api.dto.DispatchPlanExecutionResponse(
                "plan-2", "dispatch-plan-v2", DispatchPlanStatus.ACTIVE, "batch-2", 1, 0, detectedAt, List.of()));

        service(repository, telemetryRepository, snapshotService, planningService, agentService).triggerIfEvidenceObserved("run-1");

        verify(snapshotService, org.mockito.Mockito.times(2)).createExperimentSnapshot();
        verify(planningService).executeNext("plan-2");
        org.junit.jupiter.api.Assertions.assertTrue(run.getResultSummary().contains("\"retryReason\":\"DISPATCH_INPUT_EXPIRED\""));
        org.junit.jupiter.api.Assertions.assertTrue(run.getResultSummary().contains("\"agentRunId\":\"agent-plan-2\""));
    }

    @Test
    void shouldFailAfterSecondExpirationWithoutCreatingThirdPlan() {
        ExperimentRunRepository repository = mock(ExperimentRunRepository.class);
        TelemetryHistoryRepository telemetryRepository = mock(TelemetryHistoryRepository.class);
        DispatchContextSnapshotService snapshotService = mock(DispatchContextSnapshotService.class);
        MarketDispatchPlanningService planningService = mock(MarketDispatchPlanningService.class);
        MarketAgentOrchestrationService agentService = mock(MarketAgentOrchestrationService.class);
        ExperimentRunEntity run = runningExperiment(summaryJson(List.of("storage-hub-03")));
        when(repository.findByRunId("run-1")).thenReturn(Optional.of(run));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        DispatchContextSnapshotResponse firstSnapshot = mock(DispatchContextSnapshotResponse.class);
        DispatchContextSnapshotResponse replacementSnapshot = mock(DispatchContextSnapshotResponse.class);
        when(firstSnapshot.snapshotId()).thenReturn("snapshot-1");
        when(replacementSnapshot.snapshotId()).thenReturn("snapshot-2");
        when(snapshotService.createExperimentSnapshot()).thenReturn(firstSnapshot, replacementSnapshot);
        DispatchPlanResponse firstPlan = plan("plan-1", DispatchPlanStatus.DRAFT);
        DispatchPlanResponse replacementPlan = plan("plan-2", DispatchPlanStatus.DRAFT);
        when(planningService.create(any())).thenReturn(firstPlan, replacementPlan);
        when(agentService.analyze(any())).thenReturn(agentRun("snapshot-1", "plan-1"), agentRun("snapshot-2", "plan-2"));
        when(planningService.activate("plan-1")).thenThrow(expired("plan-1", OffsetDateTime.parse("2026-07-06T12:40:15+08:00")));
        when(planningService.activate("plan-2")).thenThrow(expired("plan-2", OffsetDateTime.parse("2026-07-06T12:40:16+08:00")));

        service(repository, telemetryRepository, snapshotService, planningService, agentService).triggerIfEvidenceObserved("run-1");

        verify(snapshotService, org.mockito.Mockito.times(2)).createExperimentSnapshot();
        verify(planningService, never()).executeNext(any());
        org.junit.jupiter.api.Assertions.assertTrue(run.getResultSummary().contains("\"orchestrationStatus\":\"FAILED\""));
        org.junit.jupiter.api.Assertions.assertTrue(run.getResultSummary().contains("\"expiredPlanId\":\"plan-1\""));
        org.junit.jupiter.api.Assertions.assertTrue(run.getResultSummary().contains("\"expiredPlanId\":\"plan-2\""));
    }

    private ExperimentClosedLoopOrchestrationService service(ExperimentRunRepository repository, TelemetryHistoryRepository telemetryRepository,
            DispatchContextSnapshotService snapshotService, MarketDispatchPlanningService planningService, MarketAgentOrchestrationService agentService) {
        return new ExperimentClosedLoopOrchestrationService(repository, telemetryRepository, snapshotService, planningService, agentService,
                new ObjectMapper().findAndRegisterModules(), Clock.fixed(OffsetDateTime.parse("2026-07-06T12:40:12+08:00").toInstant(), ZoneId.of("Asia/Shanghai")));
    }

    private DispatchPlanResponse plan(String planId, DispatchPlanStatus status) {
        DispatchPlanResponse response = mock(DispatchPlanResponse.class);
        when(response.planId()).thenReturn(planId);
        when(response.status()).thenReturn(status);
        return response;
    }

    private MarketAgentRunResponse agentRun(String snapshotId, String planId) {
        return new MarketAgentRunResponse("agent-" + planId, snapshotId, planId, false, List.of(),
                new MarketSafetyDecision("PASSED", true, false, List.of(), "通过"), OffsetDateTime.parse("2026-07-06T12:40:12+08:00"),
                "sequential-v2", "market-agent-set-v2", true, false);
    }

    private MarketAgentRunResponse rejectedAgentRun(String snapshotId, String planId) {
        return new MarketAgentRunResponse("agent-" + planId, snapshotId, planId, false, List.of(),
                new MarketSafetyDecision("REJECTED", false, false, List.of("BLOCK_UNSAFE_ACTIONS"), "安全门拒绝"),
                OffsetDateTime.parse("2026-07-06T12:40:12+08:00"),
                "sequential-v2", "market-agent-set-v2", true, false);
    }

    private ApiException expired(String planId, OffsetDateTime detectedAt) {
        return new ApiException(HttpStatus.CONFLICT, "DISPATCH_PLAN_EXPIRED", "计划过期",
                java.util.Map.of("planId", planId, "validUntil", detectedAt.minusSeconds(1), "detectedAt", detectedAt));
    }

    private ApiException inputExpired(String planId, String snapshotId, OffsetDateTime detectedAt) {
        return new ApiException(HttpStatus.CONFLICT, "DISPATCH_INPUT_EXPIRED", "调度输入快照或计划已过期，不能下发控制指令",
                java.util.Map.of("planId", planId, "snapshotId", snapshotId, "validUntil", detectedAt.minusSeconds(1), "detectedAt", detectedAt));
    }

    private ExperimentRunEntity runningExperiment(String summaryJson) {
        ExperimentRunEntity runEntity = new ExperimentRunEntity();
        runEntity.setRunId("run-1");
        runEntity.setScenarioCode("T5_FULL_LOSS_AUTONOMY");
        runEntity.setStatus("FAULT_OBSERVED");
        runEntity.setStartedAt(OffsetDateTime.parse("2026-07-06T12:40:00+08:00"));
        runEntity.setResultSummary(summaryJson);
        return runEntity;
    }

    private String summaryJson(List<String> targetStationIds) {
        return """
                {"targetStationIds":["%s"],"durationSeconds":120,"remark":"","orchestrationStatus":"PENDING","orchestrationTriggeredAt":null,"orchestrationCompletedAt":null,"dispatchContextSnapshotId":null,"dispatchPlanId":null,"agentRunId":null,"planStatus":null,"executionBatchId":null,"safetyGatePassed":null,"failureReason":null,"expectedLinkState":"S3"}
                """.formatted(String.join("\",\"", targetStationIds));
    }

    private TelemetryHistoryEntity telemetry(String stationId) {
        TelemetryHistoryEntity telemetryHistoryEntity = new TelemetryHistoryEntity();
        telemetryHistoryEntity.setStationId(stationId);
        telemetryHistoryEntity.setReceivedTimestamp(OffsetDateTime.parse("2026-07-06T12:40:10+08:00"));
        telemetryHistoryEntity.setLinkState(LinkState.S1);
        return telemetryHistoryEntity;
    }
}

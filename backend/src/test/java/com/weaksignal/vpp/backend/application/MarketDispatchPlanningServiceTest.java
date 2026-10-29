package com.weaksignal.vpp.backend.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weaksignal.vpp.backend.api.ApiException;
import com.weaksignal.vpp.backend.api.dto.DispatchContextSnapshotResponse;
import com.weaksignal.vpp.backend.api.dto.DispatchPlanCreateRequest;
import com.weaksignal.vpp.backend.api.dto.DispatchPlanResponse;
import com.weaksignal.vpp.backend.api.dto.ForecastPointResponse;
import com.weaksignal.vpp.backend.api.dto.ForecastSnapshotResponse;
import com.weaksignal.vpp.backend.api.dto.MarketSignalResponse;
import com.weaksignal.vpp.backend.api.dto.ResourceCapabilityResponse;
import com.weaksignal.vpp.backend.api.dto.StorageConstraintResponse;
import com.weaksignal.vpp.backend.domain.DispatchPlanStatus;
import com.weaksignal.vpp.backend.domain.LinkState;
import com.weaksignal.vpp.backend.domain.QualityCode;
import com.weaksignal.vpp.backend.domain.ResourceType;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.DispatchPlanEntity;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.DispatchPlanItemEntity;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.DispatchPlanItemRepository;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.DispatchPlanRepository;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.TelemetrySnapshotRepository;
import com.weaksignal.vpp.backend.marketdispatch.DispatchOptimizer;
import com.weaksignal.vpp.backend.marketdispatch.DispatchPlanItem;
import com.weaksignal.vpp.backend.marketdispatch.OptimizationResult;
import com.weaksignal.vpp.backend.marketdispatch.OptimizationStatus;
import com.weaksignal.vpp.backend.marketdispatch.PowerDirection;
import com.weaksignal.vpp.backend.marketdispatch.ResourceDispatchTarget;
import com.weaksignal.vpp.backend.marketdispatch.RuleBasedDispatchOptimizer;
import com.weaksignal.vpp.backend.planning.DispatchContextSnapshotService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 市场调度计划服务测试。
 */
class MarketDispatchPlanningServiceTest {

    private DispatchContextSnapshotService dispatchContextSnapshotService;
    private DispatchPlanRepository dispatchPlanRepository;
    private DispatchPlanItemRepository dispatchPlanItemRepository;
    private TelemetrySnapshotRepository telemetrySnapshotRepository;
    private DispatchService dispatchService;
    private MarketDispatchPlanningService marketDispatchPlanningService;
    private Clock clock;

    @BeforeEach
    void setUp() {
        dispatchContextSnapshotService = mock(DispatchContextSnapshotService.class);
        dispatchPlanRepository = mock(DispatchPlanRepository.class);
        dispatchPlanItemRepository = mock(DispatchPlanItemRepository.class);
        telemetrySnapshotRepository = mock(TelemetrySnapshotRepository.class);
        dispatchService = mock(DispatchService.class);
        DispatchOptimizer dispatchOptimizer = new RuleBasedDispatchOptimizer();
        clock = Clock.fixed(Instant.parse("2026-07-04T00:00:00Z"), ZoneId.of("Asia/Shanghai"));
        marketDispatchPlanningService = new MarketDispatchPlanningService(
                dispatchContextSnapshotService,
                dispatchPlanRepository,
                dispatchPlanItemRepository,
                telemetrySnapshotRepository,
                dispatchOptimizer,
                dispatchService,
                new ObjectMapper().findAndRegisterModules(),
                clock
        );
    }

    @Test
    void shouldRejectExpiredDispatchContextSnapshot() {
        when(dispatchContextSnapshotService.getSnapshot("expired-context")).thenReturn(response(false));

        ApiException exception = assertThrows(
                ApiException.class,
                () -> marketDispatchPlanningService.preview(new DispatchPlanCreateRequest("expired-context", false))
        );

        assertEquals("DISPATCH_CONTEXT_EXPIRED", exception.getCode());
    }

    @Test
    void shouldPreviewWithGeneratedSnapshotWhenRequestDoesNotProvideSnapshotId() {
        when(dispatchContextSnapshotService.createSnapshot()).thenReturn(response(true));

        DispatchPlanResponse response = marketDispatchPlanningService.preview(new DispatchPlanCreateRequest(null, false));

        assertEquals(DispatchPlanStatus.DRAFT, response.status());
        verify(dispatchContextSnapshotService).createSnapshot();
    }

    @Test
    void shouldApproveValidatedPlan() {
        DispatchPlanEntity entity = new DispatchPlanEntity();
        entity.setPlanId("dispatch-plan-1");
        entity.setInputSnapshotId("context-1");
        entity.setIntervalMinutes(60);
        entity.setConservativePlan(false);
        entity.setStatus(DispatchPlanStatus.VALIDATED.name());
        entity.setSource("TEST_CONTEXT");
        entity.setQualityCode(QualityCode.GOOD.name());
        entity.setVersion("dispatch-plan-v1");
        entity.setGeneratedAt(OffsetDateTime.parse("2026-07-04T08:00:00+08:00"));
        entity.setValidFrom(OffsetDateTime.parse("2026-07-04T08:00:00+08:00"));
        entity.setValidUntil(OffsetDateTime.parse("2026-07-04T09:00:00+08:00"));
        entity.setReceivedAt(OffsetDateTime.parse("2026-07-04T08:00:00+08:00"));
        entity.setPayloadJson("{}");
        entity.setCreatedAt(OffsetDateTime.parse("2026-07-04T08:00:00+08:00"));
        entity.setUpdatedAt(OffsetDateTime.parse("2026-07-04T08:00:00+08:00"));
        when(dispatchPlanRepository.findByPlanId("dispatch-plan-1")).thenReturn(Optional.of(entity));

        DispatchPlanResponse response = marketDispatchPlanningService.approve("dispatch-plan-1");

        assertEquals(DispatchPlanStatus.APPROVED, response.status());
    }

    @Test
    void shouldActivateApprovedPlan() {
        DispatchPlanEntity entity = approvedPlan("dispatch-plan-2");
        when(dispatchPlanRepository.findByPlanId("dispatch-plan-2")).thenReturn(Optional.of(entity));
        when(dispatchPlanRepository.findByStatusOrderByGeneratedAtDesc(DispatchPlanStatus.ACTIVE.name())).thenReturn(List.of());
        when(dispatchPlanItemRepository.findByPlanIdOrderByItemOrderAsc("dispatch-plan-2")).thenReturn(List.of());

        DispatchPlanResponse response = marketDispatchPlanningService.activate("dispatch-plan-2");

        assertEquals(DispatchPlanStatus.ACTIVE, response.status());
    }

    /**
     * 查询最新计划时应立即修正已经超过有效期的活动计划。
     */
    @Test
    void shouldExpireLatestActivePlanWhenValidityHasEnded() {
        DispatchPlanEntity entity = approvedPlan("dispatch-plan-expired");
        entity.setStatus(DispatchPlanStatus.ACTIVE.name());
        entity.setValidUntil(OffsetDateTime.parse("2026-07-04T07:59:59+08:00"));
        when(dispatchPlanRepository.findFirstByOrderByGeneratedAtDesc()).thenReturn(Optional.of(entity));
        when(dispatchPlanRepository.findByPlanId(entity.getPlanId())).thenReturn(Optional.of(entity));
        when(dispatchPlanItemRepository.findByPlanIdOrderByItemOrderAsc(entity.getPlanId())).thenReturn(List.of());

        DispatchPlanResponse response = marketDispatchPlanningService.getLatestPlan();

        assertEquals(DispatchPlanStatus.EXPIRED, response.status());
        verify(dispatchPlanRepository).save(entity);
        verify(dispatchService).invalidatePendingCommandsForPlan(entity.getPlanId());
    }

    /**
     * 周期扫描应批量失效超过有效期的活动计划。
     */
    @Test
    void shouldExpireActivePlansOnScheduledScan() {
        DispatchPlanEntity entity = approvedPlan("dispatch-plan-scheduled-expired");
        entity.setStatus(DispatchPlanStatus.ACTIVE.name());
        entity.setValidUntil(OffsetDateTime.parse("2026-07-04T07:59:59+08:00"));
        when(dispatchPlanRepository.findByStatusAndValidUntilLessThanEqual(
                DispatchPlanStatus.ACTIVE.name(),
                OffsetDateTime.parse("2026-07-04T08:00:00+08:00")
        )).thenReturn(List.of(entity));

        int expiredCount = marketDispatchPlanningService.expireActivePlans();

        assertEquals(1, expiredCount);
        assertEquals(DispatchPlanStatus.EXPIRED.name(), entity.getStatus());
        verify(dispatchService).invalidatePendingCommandsForPlan(entity.getPlanId());
    }

    @Test
    void shouldExecuteNextPlanItemThroughDispatchService() {
        DispatchPlanEntity entity = approvedPlan("dispatch-plan-3");
        entity.setStatus(DispatchPlanStatus.ACTIVE.name());
        DispatchPlanItemEntity itemEntity = new DispatchPlanItemEntity();
        itemEntity.setItemId("item-1");
        itemEntity.setPlanId("dispatch-plan-3");
        itemEntity.setInputSnapshotId("context-1");
        itemEntity.setIntervalStart(OffsetDateTime.parse("2026-07-04T08:00:00+08:00"));
        itemEntity.setIntervalEnd(OffsetDateTime.parse("2026-07-04T09:00:00+08:00"));
        itemEntity.setNetPowerTarget(120.0);
        itemEntity.setGridImportPower(10.0);
        itemEntity.setGridExportPower(0.0);
        itemEntity.setStorageChargePower(0.0);
        itemEntity.setStorageDischargePower(30.0);
        itemEntity.setFlexibleLoadAdjustment(20.0);
        itemEntity.setUpwardReserve(12.0);
        itemEntity.setDownwardReserve(10.0);
        itemEntity.setRenewableCurtailment(4.0);
        itemEntity.setExpectedCost(new BigDecimal("10.000000"));
        itemEntity.setExpectedRevenue(new BigDecimal("12.000000"));
        itemEntity.setRiskCost(new BigDecimal("1.000000"));
        itemEntity.setTargetPowersJson("""
                [{"stationId":"charge-corridor-05","resourceType":"charging","targetPower":0.0,"powerDirection":"NEUTRAL","optimizationStatus":"OPTIMAL","optimizationRound":0,"snapshotId":"context-1","constraintEvidence":{},"generatedAt":"2026-07-04T08:00:00+08:00"},
                {"stationId":"grid-link-00","resourceType":"grid","targetPower":10.0,"powerDirection":"POSITIVE","optimizationStatus":"OPTIMAL","optimizationRound":0,"snapshotId":"context-1","constraintEvidence":{},"generatedAt":"2026-07-04T08:00:00+08:00"},
                {"stationId":"load-oasis-04","resourceType":"load","targetPower":40.0,"powerDirection":"POSITIVE","optimizationStatus":"OPTIMAL","optimizationRound":0,"snapshotId":"context-1","constraintEvidence":{},"generatedAt":"2026-07-04T08:00:00+08:00"},
                {"stationId":"pv-gobi-01","resourceType":"photovoltaic","targetPower":60.0,"powerDirection":"POSITIVE","optimizationStatus":"OPTIMAL","optimizationRound":0,"snapshotId":"context-1","constraintEvidence":{},"generatedAt":"2026-07-04T08:00:00+08:00"},
                {"stationId":"storage-hub-03","resourceType":"storage","targetPower":30.0,"powerDirection":"POSITIVE","optimizationStatus":"OPTIMAL","optimizationRound":0,"snapshotId":"context-1","constraintEvidence":{},"generatedAt":"2026-07-04T08:00:00+08:00"},
                {"stationId":"wind-ridge-02","resourceType":"wind","targetPower":60.0,"powerDirection":"POSITIVE","optimizationStatus":"OPTIMAL","optimizationRound":0,"snapshotId":"context-1","constraintEvidence":{},"generatedAt":"2026-07-04T08:00:00+08:00"}]
                """);
        itemEntity.setReasonCode("TEST");
        itemEntity.setItemOrder(0);
        itemEntity.setCreatedAt(OffsetDateTime.parse("2026-07-04T08:00:00+08:00"));
        when(dispatchPlanRepository.findByPlanId("dispatch-plan-3")).thenReturn(Optional.of(entity));
        when(dispatchPlanItemRepository.findByPlanIdOrderByItemOrderAsc("dispatch-plan-3")).thenReturn(List.of(itemEntity));
        when(dispatchContextSnapshotService.getSnapshot("context-1")).thenReturn(response(true));
        when(dispatchService.getCommandsByPlanId("dispatch-plan-3")).thenReturn(List.of());
        when(dispatchService.executePlannedCommands(any())).thenReturn(new com.weaksignal.vpp.backend.api.dto.DispatchExecuteResponse(
                "batch-1",
                OffsetDateTime.parse("2026-07-04T08:00:00+08:00"),
                List.of()
        ));

        marketDispatchPlanningService.executeNext("dispatch-plan-3");

        verify(dispatchService).executePlannedCommands(any());
    }

    /** 不平衡的逐资源目标必须在计划进入 VALIDATED 前被拒绝。 */
    @Test
    void shouldKeepPlanDraftWhenPersistedTargetsAreUnbalanced() {
        DispatchPlanEntity entity = approvedPlan("dispatch-plan-unbalanced");
        entity.setStatus(DispatchPlanStatus.DRAFT.name());
        DispatchPlanItemEntity item = new DispatchPlanItemEntity();
        item.setItemId("item-unbalanced");
        item.setPlanId(entity.getPlanId());
        item.setInputSnapshotId("context-1");
        item.setNetPowerTarget(0.0);
        item.setTargetPowersJson("""
                [{"stationId":"grid-link-00","resourceType":"grid","targetPower":10.0,"powerDirection":"POSITIVE","optimizationStatus":"OPTIMAL","optimizationRound":0,"snapshotId":"context-1","constraintEvidence":{},"generatedAt":"2026-07-04T08:00:00+08:00"}]
                """);
        when(dispatchPlanRepository.findByPlanId(entity.getPlanId())).thenReturn(Optional.of(entity));
        when(dispatchPlanItemRepository.findByPlanIdOrderByItemOrderAsc(entity.getPlanId())).thenReturn(List.of(item));
        when(dispatchContextSnapshotService.getSnapshot("context-1")).thenReturn(response(true));

        ApiException exception = assertThrows(ApiException.class,
                () -> marketDispatchPlanningService.validate(entity.getPlanId()));

        assertEquals("DISPATCH_TARGET_BALANCE_UNSATISFIED", exception.getCode());
        assertEquals(DispatchPlanStatus.DRAFT.name(), entity.getStatus());
    }

    /**
     * 外部控制器目标应作为唯一事实来源写入计划项，并保存同一目标摘要。
     */
    @Test
    void shouldPersistExternalTargetsAndMatchingSummaryHash() throws Exception {
        OffsetDateTime generatedAt = OffsetDateTime.parse("2026-07-04T08:00:00+08:00");
        DispatchPlanItem item = optimizationItem("distributed-item-1", generatedAt);
        List<ResourceDispatchTarget> targets = List.of(new ResourceDispatchTarget(
                "grid-link-00", ResourceType.GRID, 32.5, PowerDirection.POSITIVE,
                OptimizationStatus.FEASIBLE, 12, "context-1", java.util.Map.of("controllerType", "DISTRIBUTED"), generatedAt
        ));
        String targetHash = ResourceDispatchTarget.summaryHash(targets);
        OptimizationResult optimizationResult = new OptimizationResult(
                List.of(item), "DISTRIBUTED_ADAPTIVE", "1", OptimizationStatus.FEASIBLE,
                0.0, 25L, 0.0, 12, "context-1", 0.0, java.util.Map.of(), null,
                java.util.Map.of(item.itemId(), targets), java.util.Map.of(item.itemId(), targetHash)
        );
        when(dispatchContextSnapshotService.getSnapshot("context-1")).thenReturn(response(true));

        marketDispatchPlanningService.createFromOptimizationResult(
                new DispatchPlanCreateRequest("context-1", false), optimizationResult);

        ArgumentCaptor<DispatchPlanItemEntity> itemCaptor = ArgumentCaptor.forClass(DispatchPlanItemEntity.class);
        verify(dispatchPlanItemRepository).save(itemCaptor.capture());
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        List<ResourceDispatchTarget> persistedTargets = objectMapper.readValue(
                itemCaptor.getValue().getTargetPowersJson(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, ResourceDispatchTarget.class)
        );
        assertEquals(1, persistedTargets.size());
        assertEquals(targets.getFirst().stationId(), persistedTargets.getFirst().stationId());
        assertEquals(targets.getFirst().targetPower(), persistedTargets.getFirst().targetPower());
        assertEquals(targets.getFirst().generatedAt().toInstant(), persistedTargets.getFirst().generatedAt().toInstant());
        assertEquals(targetHash, ResourceDispatchTarget.summaryHash(persistedTargets));

        ArgumentCaptor<DispatchPlanEntity> planCaptor = ArgumentCaptor.forClass(DispatchPlanEntity.class);
        verify(dispatchPlanRepository).save(planCaptor.capture());
        assertTrue(planCaptor.getValue().getPayloadJson().contains(targetHash));
    }

    /**
     * 目标摘要与目标内容不一致时不得创建计划。
     */
    @Test
    void shouldRejectExternalTargetsWithMismatchedHash() {
        OffsetDateTime generatedAt = OffsetDateTime.parse("2026-07-04T08:00:00+08:00");
        DispatchPlanItem item = optimizationItem("distributed-item-invalid", generatedAt);
        List<ResourceDispatchTarget> targets = List.of(new ResourceDispatchTarget(
                "grid-link-00", ResourceType.GRID, 32.5, PowerDirection.POSITIVE,
                OptimizationStatus.FEASIBLE, 12, "context-1", java.util.Map.of(), generatedAt
        ));
        OptimizationResult optimizationResult = new OptimizationResult(
                List.of(item), "DISTRIBUTED_ADAPTIVE", "1", OptimizationStatus.FEASIBLE,
                0.0, 25L, 0.0, 12, "context-1", 0.0, java.util.Map.of(), null,
                java.util.Map.of(item.itemId(), targets), java.util.Map.of(item.itemId(), "invalid-hash")
        );
        when(dispatchContextSnapshotService.getSnapshot("context-1")).thenReturn(response(true));

        ApiException exception = assertThrows(ApiException.class, () -> marketDispatchPlanningService
                .createFromOptimizationResult(new DispatchPlanCreateRequest("context-1", false), optimizationResult));

        assertEquals("DISPATCH_TARGET_HASH_MISMATCH", exception.getCode());
    }

    private DispatchPlanItem optimizationItem(String itemId, OffsetDateTime generatedAt) {
        return new DispatchPlanItem(
                itemId, "context-1", generatedAt, generatedAt.plusHours(1), 32.5,
                32.5, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "DISTRIBUTED", 0
        );
    }

    private DispatchPlanEntity approvedPlan(String planId) {
        DispatchPlanEntity entity = new DispatchPlanEntity();
        entity.setPlanId(planId);
        entity.setInputSnapshotId("context-1");
        entity.setIntervalMinutes(60);
        entity.setConservativePlan(false);
        entity.setStatus(DispatchPlanStatus.APPROVED.name());
        entity.setSource("TEST_CONTEXT");
        entity.setQualityCode(QualityCode.GOOD.name());
        entity.setVersion("dispatch-plan-v1");
        entity.setGeneratedAt(OffsetDateTime.parse("2026-07-04T08:00:00+08:00"));
        entity.setValidFrom(OffsetDateTime.parse("2026-07-04T08:00:00+08:00"));
        entity.setValidUntil(OffsetDateTime.parse("2026-07-04T09:00:00+08:00"));
        entity.setReceivedAt(OffsetDateTime.parse("2026-07-04T08:00:00+08:00"));
        entity.setPayloadJson("{}");
        entity.setCreatedAt(OffsetDateTime.parse("2026-07-04T08:00:00+08:00"));
        entity.setUpdatedAt(OffsetDateTime.parse("2026-07-04T08:00:00+08:00"));
        return entity;
    }

    private DispatchContextSnapshotResponse response(boolean active) {
        OffsetDateTime now = OffsetDateTime.parse("2026-07-04T08:00:00+08:00");
        OffsetDateTime validUntil = active ? now.plusHours(1) : now.minusSeconds(1);
        return new DispatchContextSnapshotResponse(
                "context-1",
                now,
                now,
                validUntil,
                "TEST_CONTEXT",
                QualityCode.GOOD,
                "dispatch-context-v1",
                now,
                60,
                new MarketSignalResponse(
                        "market-1",
                        "TEST_MARKET",
                        now,
                        now.plusHours(1),
                        280.0,
                        new BigDecimal("0.452000"),
                        new BigDecimal("0.486000"),
                        new BigDecimal("0.518000"),
                        80.0,
                        20.0,
                        12.0,
                        10.0,
                        now,
                        now,
                        validUntil,
                        "TEST_MARKET_DATA",
                        QualityCode.GOOD,
                        "market-v1",
                        now
                ),
                new ForecastSnapshotResponse(
                        "forecast-1",
                        60,
                        "forecast-v1",
                        List.of(new ForecastPointResponse(
                                now,
                                now.plusHours(1),
                                170.0,
                                180.0,
                                190.0,
                                60.0,
                                70.0,
                                80.0,
                                50.0,
                                60.0,
                                70.0,
                                new BigDecimal("0.438000"),
                                new BigDecimal("0.486000"),
                                new BigDecimal("0.536000")
                        )),
                        now,
                        now,
                        validUntil,
                        "TEST_FORECAST_DATA",
                        QualityCode.GOOD,
                        "forecast-v1",
                        now
                ),
                List.of(
                        new ResourceCapabilityResponse(
                                "cap-storage",
                                "cap-group-1",
                                "storage-hub-03",
                                ResourceType.STORAGE,
                                0.0,
                                120.0,
                                -120.0,
                                120.0,
                                120.0,
                                120.0,
                                240.0,
                                new StorageConstraintResponse(
                                        55.0,
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
                                validUntil,
                                "REALTIME_TELEMETRY",
                                QualityCode.GOOD,
                                "capability-v1",
                                now
                        ),
                        new ResourceCapabilityResponse(
                                "cap-grid",
                                "cap-group-1",
                                "grid-link-00",
                                ResourceType.GRID,
                                0.0,
                                320.0,
                                0.0,
                                320.0,
                                120.0,
                                120.0,
                                320.0,
                                null,
                                LinkState.S0,
                                2L,
                                0.98,
                                true,
                                "",
                                now,
                                now,
                                validUntil,
                                "REALTIME_TELEMETRY",
                                QualityCode.GOOD,
                                "capability-v1",
                                now
                        ),
                        new ResourceCapabilityResponse(
                                "cap-load",
                                "cap-group-1",
                                "load-oasis-04",
                                ResourceType.LOAD,
                                260.0,
                                90.0,
                                0.0,
                                90.0,
                                30.0,
                                30.0,
                                90.0,
                                null,
                                LinkState.S0,
                                2L,
                                0.98,
                                true,
                                "",
                                now,
                                now,
                                validUntil,
                                "REALTIME_TELEMETRY",
                                QualityCode.GOOD,
                                "capability-v1",
                                now
                        ),
                        new ResourceCapabilityResponse(
                                "cap-charge",
                                "cap-group-1",
                                "charge-corridor-05",
                                ResourceType.CHARGING,
                                40.0,
                                30.0,
                                0.0,
                                30.0,
                                20.0,
                                20.0,
                                30.0,
                                null,
                                LinkState.S0,
                                2L,
                                0.98,
                                true,
                                "",
                                now,
                                now,
                                validUntil,
                                "REALTIME_TELEMETRY",
                                QualityCode.GOOD,
                                "capability-v1",
                                now
                        ),
                        new ResourceCapabilityResponse(
                                "cap-pv",
                                "cap-group-1",
                                "pv-gobi-01",
                                ResourceType.PHOTOVOLTAIC,
                                70.0,
                                70.0,
                                0.0,
                                70.0,
                                40.0,
                                40.0,
                                70.0,
                                null,
                                LinkState.S0,
                                2L,
                                0.98,
                                true,
                                "",
                                now,
                                now,
                                validUntil,
                                "REALTIME_TELEMETRY",
                                QualityCode.GOOD,
                                "capability-v1",
                                now
                        ),
                        new ResourceCapabilityResponse(
                                "cap-wind",
                                "cap-group-1",
                                "wind-ridge-02",
                                ResourceType.WIND,
                                60.0,
                                60.0,
                                0.0,
                                60.0,
                                30.0,
                                30.0,
                                60.0,
                                null,
                                LinkState.S0,
                                2L,
                                0.98,
                                true,
                                "",
                                now,
                                now,
                                validUntil,
                                "REALTIME_TELEMETRY",
                                QualityCode.GOOD,
                                "capability-v1",
                                now
                        )
                )
        );
    }
}

package com.weaksignal.vpp.backend.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weaksignal.vpp.backend.api.ApiException;
import com.weaksignal.vpp.backend.api.dto.MarketBidCreateRequest;
import com.weaksignal.vpp.backend.api.dto.MarketBidResponse;
import com.weaksignal.vpp.backend.api.dto.MarketBidSegmentRequest;
import com.weaksignal.vpp.backend.api.dto.MarketClearingResponse;
import com.weaksignal.vpp.backend.api.dto.MarketClearingTestRequest;
import com.weaksignal.vpp.backend.api.dto.SettlementCalculationRequest;
import com.weaksignal.vpp.backend.api.dto.SettlementResultResponse;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.DispatchPlanEntity;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.DispatchPlanItemEntity;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.MarketBidEntity;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.MarketBidSegmentEntity;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.MarketClearingResultEntity;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.TelemetryHistoryEntity;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.DispatchPlanItemRepository;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.DispatchPlanRepository;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.MarketBidRepository;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.MarketBidSegmentRepository;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.MarketClearingResultRepository;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.MarketMeteringRecordRepository;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.SettlementResultRepository;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.TelemetryHistoryRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 测试市场结算服务测试。
 */
class MarketSettlementServiceTest {

    private MarketBidRepository marketBidRepository;
    private MarketBidSegmentRepository marketBidSegmentRepository;
    private MarketClearingResultRepository marketClearingResultRepository;
    private MarketMeteringRecordRepository marketMeteringRecordRepository;
    private SettlementResultRepository settlementResultRepository;
    private DispatchPlanRepository dispatchPlanRepository;
    private DispatchPlanItemRepository dispatchPlanItemRepository;
    private TelemetryHistoryRepository telemetryHistoryRepository;
    private MarketSettlementService marketSettlementService;

    @BeforeEach
    void setUp() {
        marketBidRepository = mock(MarketBidRepository.class);
        marketBidSegmentRepository = mock(MarketBidSegmentRepository.class);
        marketClearingResultRepository = mock(MarketClearingResultRepository.class);
        marketMeteringRecordRepository = mock(MarketMeteringRecordRepository.class);
        settlementResultRepository = mock(SettlementResultRepository.class);
        dispatchPlanRepository = mock(DispatchPlanRepository.class);
        dispatchPlanItemRepository = mock(DispatchPlanItemRepository.class);
        telemetryHistoryRepository = mock(TelemetryHistoryRepository.class);
        Clock clock = Clock.fixed(Instant.parse("2026-07-04T08:00:00Z"), ZoneId.of("Asia/Shanghai"));
        marketSettlementService = new MarketSettlementService(
                marketBidRepository,
                marketBidSegmentRepository,
                marketClearingResultRepository,
                marketMeteringRecordRepository,
                settlementResultRepository,
                dispatchPlanRepository,
                dispatchPlanItemRepository,
                telemetryHistoryRepository,
                new ObjectMapper().findAndRegisterModules(),
                clock
        );
    }

    @Test
    void shouldCreateBidWithDeterministicSegments() {
        MarketBidCreateRequest request = new MarketBidCreateRequest(
                "DAY_AHEAD",
                "virtual-power-plant",
                OffsetDateTime.parse("2026-07-04T16:00:00+08:00"),
                OffsetDateTime.parse("2026-07-04T17:00:00+08:00"),
                120.0,
                "dispatch-context-1",
                "bid-v1",
                OffsetDateTime.parse("2026-07-04T18:00:00+08:00"),
                true,
                List.of(
                        new MarketBidSegmentRequest(50.0, new BigDecimal("0.520000")),
                        new MarketBidSegmentRequest(40.0, new BigDecimal("0.560000"))
                )
        );

        MarketBidResponse response = marketSettlementService.createBid(request);

        assertEquals(90.0, response.totalQuantity());
        assertEquals("CREATED", response.status());
        assertEquals(2, response.segments().size());
        verify(marketBidRepository).save(any());
        verify(marketBidSegmentRepository).saveAll(any());
    }

    @Test
    void shouldRejectBidWhenCapacityExceeded() {
        MarketBidCreateRequest request = new MarketBidCreateRequest(
                "DAY_AHEAD",
                "virtual-power-plant",
                OffsetDateTime.parse("2026-07-04T16:00:00+08:00"),
                OffsetDateTime.parse("2026-07-04T17:00:00+08:00"),
                80.0,
                "dispatch-context-1",
                "bid-v1",
                OffsetDateTime.parse("2026-07-04T18:00:00+08:00"),
                true,
                List.of(
                        new MarketBidSegmentRequest(50.0, new BigDecimal("0.520000")),
                        new MarketBidSegmentRequest(40.0, new BigDecimal("0.560000"))
                )
        );

        ApiException exception = assertThrows(ApiException.class, () -> marketSettlementService.createBid(request));

        assertEquals("MARKET_BID_CAPACITY_EXCEEDED", exception.getCode());
    }

    @Test
    void shouldCreateTestClearingWithWeightedPrice() {
        MarketBidEntity bidEntity = new MarketBidEntity();
        bidEntity.setBidId("bid-1");
        bidEntity.setMarketType("DAY_AHEAD");
        bidEntity.setAggregateObject("virtual-power-plant");
        bidEntity.setIntervalStart(OffsetDateTime.parse("2026-07-04T16:00:00+08:00"));
        bidEntity.setIntervalEnd(OffsetDateTime.parse("2026-07-04T17:00:00+08:00"));
        bidEntity.setAvailableCapacity(100.0);
        bidEntity.setTotalQuantity(90.0);
        bidEntity.setInputSnapshotId("dispatch-context-1");
        bidEntity.setBidVersion("bid-v1");
        bidEntity.setStatus("CREATED");
        bidEntity.setValidUntil(OffsetDateTime.parse("2026-07-04T18:00:00+08:00"));
        bidEntity.setTestData(true);
        bidEntity.setCreatedAt(OffsetDateTime.parse("2026-07-04T08:00:00+08:00"));
        when(marketBidRepository.findByBidId("bid-1")).thenReturn(Optional.of(bidEntity));
        when(marketBidSegmentRepository.findByBidIdOrderBySegmentOrderAsc("bid-1")).thenReturn(List.of(
                segment("bid-1", 1, 50.0, "0.500000"),
                segment("bid-1", 2, 40.0, "0.600000")
        ));

        MarketClearingResponse response = marketSettlementService.createTestClearing(
                new MarketClearingTestRequest("bid-1", 5.0, "trade-1")
        );

        assertEquals(90.0, response.clearedPower());
        assertEquals(new BigDecimal("0.544444"), response.clearedPrice());
        assertEquals(5.0, response.ancillaryServicePower());
        verify(marketClearingResultRepository).save(any());
    }

    @Test
    void shouldCalculateSettlementFromTelemetryHistory() {
        DispatchPlanEntity planEntity = new DispatchPlanEntity();
        planEntity.setPlanId("plan-1");
        planEntity.setValidFrom(OffsetDateTime.parse("2026-07-04T16:00:00+08:00"));
        planEntity.setValidUntil(OffsetDateTime.parse("2026-07-04T17:00:00+08:00"));
        when(dispatchPlanRepository.findByPlanId("plan-1")).thenReturn(Optional.of(planEntity));
        when(dispatchPlanItemRepository.findByPlanIdOrderByItemOrderAsc("plan-1")).thenReturn(List.of(planItem()));

        MarketClearingResultEntity clearingEntity = new MarketClearingResultEntity();
        clearingEntity.setClearingId("clearing-1");
        clearingEntity.setBidId("bid-1");
        clearingEntity.setMarketType("DAY_AHEAD");
        clearingEntity.setIntervalStart(OffsetDateTime.parse("2026-07-04T16:00:00+08:00"));
        clearingEntity.setIntervalEnd(OffsetDateTime.parse("2026-07-04T17:00:00+08:00"));
        clearingEntity.setClearedPower(100.0);
        clearingEntity.setClearedPrice(new BigDecimal("0.500000"));
        clearingEntity.setAncillaryServicePower(10.0);
        clearingEntity.setExternalTradeId("trade-1");
        clearingEntity.setTestData(true);
        clearingEntity.setCreatedAt(OffsetDateTime.parse("2026-07-04T08:00:00+08:00"));
        when(marketClearingResultRepository.findByClearingId("clearing-1")).thenReturn(Optional.of(clearingEntity));
        when(settlementResultRepository.findByPlanIdAndClearingId("plan-1", "clearing-1")).thenReturn(Optional.empty());
        when(telemetryHistoryRepository.findByReceivedTimestampBetweenOrderByReceivedTimestampAsc(any(), any(), any())).thenReturn(List.of(
                telemetry("pv-gobi-01", 70.0),
                telemetry("wind-ridge-02", 45.0),
                telemetry("grid-link-00", -10.0)
        ));

        SettlementResultResponse response = marketSettlementService.calculateSettlement(
                new SettlementCalculationRequest("plan-1", "clearing-1")
        );

        assertEquals("plan-1", response.planId());
        assertEquals("clearing-1", response.clearingId());
        assertFalse(response.meteringRecords().isEmpty());
        assertTrue(response.netRevenue().compareTo(BigDecimal.ZERO) > 0);
        verify(settlementResultRepository).save(any());
        verify(marketMeteringRecordRepository).saveAll(any());
    }

    @Test
    void shouldRejectDuplicateSettlement() {
        when(settlementResultRepository.findByPlanIdAndClearingId("plan-1", "clearing-1")).thenReturn(Optional.of(mock(
                com.weaksignal.vpp.backend.infrastructure.persistence.entity.SettlementResultEntity.class
        )));

        ApiException exception = assertThrows(
                ApiException.class,
                () -> marketSettlementService.calculateSettlement(new SettlementCalculationRequest("plan-1", "clearing-1"))
        );

        assertEquals("SETTLEMENT_ALREADY_EXISTS", exception.getCode());
    }

    private DispatchPlanItemEntity planItem() {
        DispatchPlanItemEntity entity = new DispatchPlanItemEntity();
        entity.setItemId("item-1");
        entity.setPlanId("plan-1");
        entity.setInputSnapshotId("dispatch-context-1");
        entity.setIntervalStart(OffsetDateTime.parse("2026-07-04T16:00:00+08:00"));
        entity.setIntervalEnd(OffsetDateTime.parse("2026-07-04T17:00:00+08:00"));
        entity.setNetPowerTarget(95.0);
        entity.setGridImportPower(12.0);
        entity.setGridExportPower(8.0);
        entity.setStorageChargePower(5.0);
        entity.setStorageDischargePower(18.0);
        entity.setFlexibleLoadAdjustment(10.0);
        entity.setUpwardReserve(6.0);
        entity.setDownwardReserve(4.0);
        entity.setRenewableCurtailment(3.0);
        entity.setExpectedCost(new BigDecimal("10.000000"));
        entity.setExpectedRevenue(new BigDecimal("15.000000"));
        entity.setRiskCost(new BigDecimal("1.500000"));
        entity.setReasonCode("TEST");
        entity.setItemOrder(0);
        entity.setCreatedAt(OffsetDateTime.parse("2026-07-04T08:00:00+08:00"));
        return entity;
    }

    private MarketBidSegmentEntity segment(String bidId, int order, double quantity, String price) {
        MarketBidSegmentEntity entity = new MarketBidSegmentEntity();
        entity.setBidId(bidId);
        entity.setSegmentOrder(order);
        entity.setQuantity(quantity);
        entity.setPrice(new BigDecimal(price));
        entity.setCreatedAt(OffsetDateTime.parse("2026-07-04T08:00:00+08:00"));
        return entity;
    }

    private TelemetryHistoryEntity telemetry(String stationId, double activePower) {
        TelemetryHistoryEntity entity = new TelemetryHistoryEntity();
        entity.setStationId(stationId);
        entity.setMessageId("msg-" + stationId);
        entity.setSequenceNumber(1L);
        entity.setDeviceTimestamp(OffsetDateTime.parse("2026-07-04T16:20:00+08:00"));
        entity.setReceivedTimestamp(OffsetDateTime.parse("2026-07-04T16:20:01+08:00"));
        entity.setActivePower(activePower);
        entity.setAvailableCapacity(Math.abs(activePower) + 20.0);
        entity.setLatency(20);
        entity.setPacketLossRate(0.1);
        entity.setDataAge(1L);
        entity.setQualityCode("GOOD");
        entity.setOnline(true);
        entity.setPayloadJson("{}");
        return entity;
    }
}

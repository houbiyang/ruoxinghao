package com.weaksignal.vpp.backend.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.weaksignal.vpp.backend.api.dto.DispatchPlanItemResponse;
import com.weaksignal.vpp.backend.api.dto.DispatchPlanResponse;
import com.weaksignal.vpp.backend.api.dto.ForecastPointResponse;
import com.weaksignal.vpp.backend.api.dto.ForecastSnapshotResponse;
import com.weaksignal.vpp.backend.api.dto.MarketDashboardOverviewResponse;
import com.weaksignal.vpp.backend.api.dto.MarketMeteringRecordResponse;
import com.weaksignal.vpp.backend.api.dto.MarketSignalResponse;
import com.weaksignal.vpp.backend.api.dto.ResourceCapabilityResponse;
import com.weaksignal.vpp.backend.api.dto.SettlementResultResponse;
import com.weaksignal.vpp.backend.api.dto.StorageConstraintResponse;
import com.weaksignal.vpp.backend.capability.ResourceCapabilityService;
import com.weaksignal.vpp.backend.domain.DispatchPlanStatus;
import com.weaksignal.vpp.backend.domain.LinkState;
import com.weaksignal.vpp.backend.domain.QualityCode;
import com.weaksignal.vpp.backend.domain.ResourceType;
import com.weaksignal.vpp.backend.forecast.ForecastService;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.MarketBidEntity;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.MarketClearingResultEntity;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.MarketNewsEntity;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.MarketSignalSnapshotEntity;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.MarketBidRepository;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.MarketClearingResultRepository;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.MarketNewsRepository;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.MarketSignalSnapshotRepository;
import com.weaksignal.vpp.backend.market.MarketSignalService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.math.RoundingMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 市场与电价页面查询服务测试。
 */
class MarketDashboardQueryServiceTest {

    private static final OffsetDateTime LATEST_START = OffsetDateTime.parse("2026-07-10T16:00:00+08:00");

    private MarketSignalService marketSignalService;
    private ForecastService forecastService;
    private ResourceCapabilityService resourceCapabilityService;
    private MarketDispatchPlanningService marketDispatchPlanningService;
    private MarketSettlementService marketSettlementService;
    private MarketSignalSnapshotRepository marketSignalSnapshotRepository;
    private MarketBidRepository marketBidRepository;
    private MarketClearingResultRepository marketClearingResultRepository;
    private MarketNewsRepository marketNewsRepository;
    private MarketDashboardQueryService marketDashboardQueryService;

    @BeforeEach
    void setUp() {
        marketSignalService = mock(MarketSignalService.class);
        forecastService = mock(ForecastService.class);
        resourceCapabilityService = mock(ResourceCapabilityService.class);
        marketDispatchPlanningService = mock(MarketDispatchPlanningService.class);
        marketSettlementService = mock(MarketSettlementService.class);
        marketSignalSnapshotRepository = mock(MarketSignalSnapshotRepository.class);
        marketBidRepository = mock(MarketBidRepository.class);
        marketClearingResultRepository = mock(MarketClearingResultRepository.class);
        marketNewsRepository = mock(MarketNewsRepository.class);

        marketDashboardQueryService = new MarketDashboardQueryService(
                marketSignalService,
                forecastService,
                resourceCapabilityService,
                marketDispatchPlanningService,
                marketSettlementService,
                marketSignalSnapshotRepository,
                marketBidRepository,
                marketClearingResultRepository,
                marketNewsRepository,
                Clock.fixed(Instant.parse("2026-07-10T08:05:00Z"), ZoneId.of("Asia/Shanghai"))
        );
    }

    @Test
    void shouldBuildOverviewWithDeterministicDistributionAndRecommendations() {
        List<MarketSignalSnapshotEntity> fullSignals = buildSignals(168);
        List<MarketSignalSnapshotEntity> latestTwentyFourSignals = withUtcOffset(fullSignals.subList(fullSignals.size() - 24, fullSignals.size()));
        List<MarketClearingResultEntity> latestTwentyFourClearings = buildClearings(24);

        when(marketSignalService.getLatestSnapshot()).thenReturn(buildLatestSignalResponse());
        when(forecastService.getLatestSnapshot()).thenReturn(buildForecastSnapshot());
        when(marketDispatchPlanningService.getLatestPlan()).thenReturn(buildDispatchPlan());
        when(marketSettlementService.getLatestSettlement()).thenReturn(buildSettlement());
        when(resourceCapabilityService.getLatestCapabilities()).thenReturn(buildCapabilities());
        when(marketSignalSnapshotRepository.findBySourceAndVersionAndIntervalStartGreaterThanEqualAndIntervalStartLessThanOrderByIntervalStartAsc(
                any(), any(), any(), any()))
                .thenReturn(latestTwentyFourSignals);
        when(marketSignalSnapshotRepository.findBySourceAndVersionOrderByIntervalStartDesc(any(), any(), any()))
                .thenReturn(reversed(latestTwentyFourSignals));
        when(marketClearingResultRepository.findAllByOrderByIntervalStartDesc(any())).thenReturn(reversed(latestTwentyFourClearings));
        when(marketClearingResultRepository.findFirstByOrderByCreatedAtDesc()).thenReturn(Optional.of(latestTwentyFourClearings.get(23)));
        when(marketBidRepository.findFirstByOrderByCreatedAtDesc()).thenReturn(Optional.of(buildLatestBid()));
        when(marketNewsRepository.findAllByOrderByPublishedAtDesc(any())).thenReturn(buildNews());

        MarketDashboardOverviewResponse response = marketDashboardQueryService.getOverview("24H", 5);

        assertEquals("西北电力市场", response.summary().marketName());
        assertEquals("24H", response.priceTrend().range());
        assertEquals(24, response.priceTrend().points().size());
        MarketDashboardOverviewResponse.PriceTrendPoint firstPoint = response.priceTrend().points().get(0);
        assertFalse(firstPoint.forecastTodayPrice().equals(firstPoint.dayAheadPrice()));
        assertFalse(firstPoint.forecastTodayPrice().equals(firstPoint.forecastTomorrowPrice()));
        assertEquals(5, response.distribution().size());
        assertTrue(response.distribution().stream().anyMatch(item -> item.percentage().compareTo(BigDecimal.ZERO) > 0));
        assertEquals(3, response.recommendations().size());
        assertTrue(response.recommendations().get(0).value().compareTo(BigDecimal.ZERO) > 0);
        assertTrue(response.predictionAnalysis().conclusion().contains("低价时段充电"));
        assertEquals(5, response.news().size());
    }

    @Test
    void shouldDownsampleSevenDayTrendWhenRangeIsSevenDay() {
        List<MarketSignalSnapshotEntity> fullSignals = buildSignals(168);
        List<MarketClearingResultEntity> latestTwentyFourClearings = buildClearings(24);

        when(marketSignalService.getLatestSnapshot()).thenReturn(buildLatestSignalResponse());
        when(forecastService.getLatestSnapshot()).thenReturn(buildForecastSnapshot());
        when(marketDispatchPlanningService.getLatestPlan()).thenReturn(buildDispatchPlan());
        when(marketSettlementService.getLatestSettlement()).thenReturn(buildSettlement());
        when(resourceCapabilityService.getLatestCapabilities()).thenReturn(buildCapabilities());
        when(marketSignalSnapshotRepository.findBySourceAndVersionAndIntervalStartGreaterThanEqualAndIntervalStartLessThanOrderByIntervalStartAsc(
                any(), any(), any(), any()))
                .thenReturn(fullSignals);
        when(marketSignalSnapshotRepository.findBySourceAndVersionOrderByIntervalStartDesc(any(), any(), any()))
                .thenReturn(reversed(fullSignals.subList(fullSignals.size() - 24, fullSignals.size())));
        when(marketClearingResultRepository.findAllByOrderByIntervalStartDesc(any())).thenReturn(reversed(latestTwentyFourClearings));
        when(marketClearingResultRepository.findFirstByOrderByCreatedAtDesc()).thenReturn(Optional.of(latestTwentyFourClearings.get(23)));
        when(marketBidRepository.findFirstByOrderByCreatedAtDesc()).thenReturn(Optional.of(buildLatestBid()));
        when(marketNewsRepository.findAllByOrderByPublishedAtDesc(any())).thenReturn(buildNews());

        MarketDashboardOverviewResponse response = marketDashboardQueryService.getOverview("7D", 3);

        assertEquals("7D", response.priceTrend().range());
        assertEquals(29, response.priceTrend().points().size());
        assertEquals(5, response.news().size());
        assertFalse(response.timeOfUsePrices().isEmpty());
    }

    private MarketSignalResponse buildLatestSignalResponse() {
        return new MarketSignalResponse(
                "market-signal-dashboard-168",
                "DAY_AHEAD",
                LATEST_START,
                LATEST_START.plusHours(1),
                150.0,
                new BigDecimal("0.920000"),
                new BigDecimal("0.960000"),
                new BigDecimal("1.020000"),
                360.0,
                210.0,
                46.0,
                30.0,
                LATEST_START.minusMinutes(5),
                LATEST_START,
                OffsetDateTime.parse("2030-01-01T00:00:00+08:00"),
                "MARKET_DASHBOARD_SEED",
                QualityCode.GOOD,
                "market-dashboard-v1",
                LATEST_START.minusMinutes(4)
        );
    }

    private ForecastSnapshotResponse buildForecastSnapshot() {
        List<ForecastPointResponse> points = new ArrayList<>();
        OffsetDateTime start = LATEST_START.minusHours(167);
        for (int index = 0; index < 192; index++) {
            OffsetDateTime pointStart = start.plusHours(index);
            OffsetDateTime pointEnd = pointStart.plusHours(1);
            int hour = pointStart.getHour();
            BigDecimal priceP50 = BigDecimal.valueOf(0.30 + hour * 0.035 + (index / 24) * 0.01).setScale(6, RoundingMode.HALF_UP);
            points.add(new ForecastPointResponse(
                    pointStart,
                    pointEnd,
                    260.0,
                    282.0,
                    304.0,
                    45.0,
                    68.0,
                    88.0,
                    62.0,
                    78.0,
                    96.0,
                    priceP50.subtract(new BigDecimal("0.050000")),
                    priceP50,
                    priceP50.add(new BigDecimal("0.060000"))
            ));
        }
        return new ForecastSnapshotResponse(
                "forecast-dashboard-001",
                60,
                "forecast-dashboard-v1",
                points,
                LATEST_START.minusMinutes(20),
                start,
                OffsetDateTime.parse("2030-01-01T00:00:00+08:00"),
                "MARKET_DASHBOARD_FORECAST",
                QualityCode.GOOD,
                "forecast-dashboard-v1",
                LATEST_START.minusMinutes(19)
        );
    }

    private DispatchPlanResponse buildDispatchPlan() {
        List<DispatchPlanItemResponse> items = new ArrayList<>();
        OffsetDateTime dayStart = OffsetDateTime.parse("2026-07-10T00:00:00+08:00");
        for (int hour = 0; hour < 24; hour++) {
            OffsetDateTime start = dayStart.plusHours(hour);
            OffsetDateTime end = start.plusHours(1);
            double charge = hour < 6 ? 40 + hour * 5 : 0;
            double discharge = hour >= 18 && hour <= 21 ? 55 + (hour - 18) * 8 : 0;
            double flexibleLoad = hour >= 19 && hour <= 20 ? 28 + (hour - 19) * 10 : 0;
            items.add(new DispatchPlanItemResponse(
                    "item-" + hour,
                    "dispatch-context-market-dashboard-001",
                    start,
                    end,
                    110 + hour,
                    12,
                    8,
                    charge,
                    discharge,
                    flexibleLoad,
                    10,
                    8,
                    0,
                    new BigDecimal("18.000000"),
                    new BigDecimal("25.000000"),
                    new BigDecimal("1.500000"),
                    hour < 6 ? "VALLEY_CHARGE" : hour >= 18 ? "PEAK_DISCHARGE" : "BALANCE_TRACKING",
                    hour
            ));
        }
        return new DispatchPlanResponse(
                "market-dashboard-plan-001",
                "dispatch-context-market-dashboard-001",
                60,
                false,
                DispatchPlanStatus.ACTIVE,
                dayStart.plusMinutes(5),
                dayStart,
                dayStart.plusDays(1),
                "MARKET_DASHBOARD_PLAN",
                QualityCode.GOOD,
                "dispatch-plan-v1",
                dayStart.plusMinutes(5),
                items
        );
    }

    private SettlementResultResponse buildSettlement() {
        return new SettlementResultResponse(
                "market-dashboard-settlement-001",
                "market-dashboard-plan-001",
                "market-dashboard-clearing-024",
                new BigDecimal("6520.000000"),
                new BigDecimal("1180.000000"),
                new BigDecimal("860.000000"),
                new BigDecimal("210.000000"),
                new BigDecimal("95.000000"),
                new BigDecimal("480.000000"),
                new BigDecimal("62.000000"),
                new BigDecimal("330.000000"),
                new BigDecimal("7345.000000"),
                java.util.Map.of("storage-hub-03", new BigDecimal("4350.00")),
                true,
                LATEST_START.plusMinutes(20),
                List.of(
                        new MarketMeteringRecordResponse("metering-1", "storage-hub-03", LATEST_START, LATEST_START.plusHours(1), 25, new BigDecimal("25000.000000"), 12),
                        new MarketMeteringRecordResponse("metering-2", "load-oasis-04", LATEST_START, LATEST_START.plusHours(1), 23.15, new BigDecimal("23150.000000"), 12),
                        new MarketMeteringRecordResponse("metering-3", "charge-corridor-05", LATEST_START, LATEST_START.plusHours(1), 20.20, new BigDecimal("20200.000000"), 12)
                )
        );
    }

    private List<ResourceCapabilityResponse> buildCapabilities() {
        return List.of(
                new ResourceCapabilityResponse(
                        "cap-storage",
                        "group-1",
                        "storage-hub-03",
                        ResourceType.STORAGE,
                        -18,
                        126,
                        -120,
                        120,
                        120,
                        120,
                        240,
                        new StorageConstraintResponse(
                                64,
                                10,
                                90,
                                240,
                                120,
                                120,
                                0.95,
                                0.95,
                                new BigDecimal("0.080000"),
                                0.96,
                                12,
                                12
                        ),
                        LinkState.S0,
                        2,
                        0.98,
                        true,
                        "",
                        LATEST_START,
                        LATEST_START,
                        LATEST_START.plusHours(1),
                        "REALTIME_TELEMETRY",
                        QualityCode.GOOD,
                        "capability-v1",
                        LATEST_START
                ),
                new ResourceCapabilityResponse(
                        "cap-load",
                        "group-1",
                        "load-oasis-04",
                        ResourceType.LOAD,
                        188,
                        96,
                        0,
                        96,
                        36,
                        36,
                        96,
                        null,
                        LinkState.S0,
                        3,
                        0.96,
                        true,
                        "",
                        LATEST_START,
                        LATEST_START,
                        LATEST_START.plusHours(1),
                        "REALTIME_TELEMETRY",
                        QualityCode.GOOD,
                        "capability-v1",
                        LATEST_START
                )
        );
    }

    private List<MarketSignalSnapshotEntity> buildSignals(int count) {
        List<MarketSignalSnapshotEntity> signals = new ArrayList<>();
        OffsetDateTime start = LATEST_START.minusHours(count - 1L);
        for (int index = 0; index < count; index++) {
            OffsetDateTime pointStart = start.plusHours(index);
            int hour = pointStart.getHour();
            BigDecimal realTime = BigDecimal.valueOf(0.28 + hour * 0.04 + (index % 5) * 0.005).setScale(6, RoundingMode.HALF_UP);
            BigDecimal dayAhead = realTime.subtract(new BigDecimal("0.040000"));
            MarketSignalSnapshotEntity entity = new MarketSignalSnapshotEntity();
            entity.setSnapshotId("signal-" + index);
            entity.setMarketType("DAY_AHEAD");
            entity.setIntervalStart(pointStart);
            entity.setIntervalEnd(pointStart.plusHours(1));
            entity.setContractPower(120 + index * 0.5);
            entity.setContractPrice(dayAhead.subtract(new BigDecimal("0.020000")));
            entity.setDayAheadPrice(dayAhead);
            entity.setRealTimePrice(realTime);
            entity.setGridImportLimit(320);
            entity.setGridExportLimit(180);
            entity.setReserveUpRequirement(40);
            entity.setReserveDownRequirement(28);
            entity.setSource("MARKET_DASHBOARD_SEED");
            entity.setQualityCode(QualityCode.GOOD.name());
            entity.setVersion("market-dashboard-v1");
            entity.setGeneratedAt(pointStart.minusMinutes(5));
            entity.setValidFrom(pointStart);
            entity.setValidUntil(OffsetDateTime.parse("2030-01-01T00:00:00+08:00"));
            entity.setReceivedAt(pointStart.minusMinutes(4));
            entity.setPayloadJson("{}");
            signals.add(entity);
        }
        return signals;
    }

    /**
     * 将市场信号时间转换为 UTC 偏移，模拟 PostgreSQL TIMESTAMPTZ 被 JPA 读出后的偏移差异。
     *
     * @param source 原始市场信号
     * @return UTC 偏移市场信号
     */
    private List<MarketSignalSnapshotEntity> withUtcOffset(List<MarketSignalSnapshotEntity> source) {
        return source.stream()
                .map(entity -> {
                    MarketSignalSnapshotEntity copied = new MarketSignalSnapshotEntity();
                    copied.setSnapshotId(entity.getSnapshotId());
                    copied.setMarketType(entity.getMarketType());
                    copied.setIntervalStart(entity.getIntervalStart().withOffsetSameInstant(ZoneOffset.UTC));
                    copied.setIntervalEnd(entity.getIntervalEnd().withOffsetSameInstant(ZoneOffset.UTC));
                    copied.setContractPower(entity.getContractPower());
                    copied.setContractPrice(entity.getContractPrice());
                    copied.setDayAheadPrice(entity.getDayAheadPrice());
                    copied.setRealTimePrice(entity.getRealTimePrice());
                    copied.setGridImportLimit(entity.getGridImportLimit());
                    copied.setGridExportLimit(entity.getGridExportLimit());
                    copied.setReserveUpRequirement(entity.getReserveUpRequirement());
                    copied.setReserveDownRequirement(entity.getReserveDownRequirement());
                    copied.setSource(entity.getSource());
                    copied.setQualityCode(entity.getQualityCode());
                    copied.setVersion(entity.getVersion());
                    copied.setGeneratedAt(entity.getGeneratedAt().withOffsetSameInstant(ZoneOffset.UTC));
                    copied.setValidFrom(entity.getValidFrom().withOffsetSameInstant(ZoneOffset.UTC));
                    copied.setValidUntil(entity.getValidUntil().withOffsetSameInstant(ZoneOffset.UTC));
                    copied.setReceivedAt(entity.getReceivedAt().withOffsetSameInstant(ZoneOffset.UTC));
                    copied.setPayloadJson(entity.getPayloadJson());
                    return copied;
                })
                .toList();
    }

    private List<MarketClearingResultEntity> buildClearings(int count) {
        List<MarketClearingResultEntity> clearings = new ArrayList<>();
        OffsetDateTime start = LATEST_START.minusHours(count - 1L);
        for (int index = 0; index < count; index++) {
            OffsetDateTime pointStart = start.plusHours(index);
            MarketClearingResultEntity entity = new MarketClearingResultEntity();
            entity.setClearingId("clearing-" + index);
            entity.setBidId("bid-" + index);
            entity.setMarketType("DAY_AHEAD");
            entity.setIntervalStart(pointStart);
            entity.setIntervalEnd(pointStart.plusHours(1));
            entity.setClearedPower(86 + index * 1.2);
            entity.setClearedPrice(new BigDecimal("0.650000"));
            entity.setAncillaryServicePower(12);
            entity.setExternalTradeId("trade-" + index);
            entity.setTestData(true);
            entity.setCreatedAt(pointStart.plusMinutes(15));
            clearings.add(entity);
        }
        return clearings;
    }

    private MarketBidEntity buildLatestBid() {
        MarketBidEntity entity = new MarketBidEntity();
        entity.setBidId("market-dashboard-bid-024");
        entity.setMarketType("DAY_AHEAD");
        entity.setAggregateObject("virtual-power-plant");
        entity.setIntervalStart(LATEST_START);
        entity.setIntervalEnd(LATEST_START.plusHours(1));
        entity.setAvailableCapacity(130);
        entity.setTotalQuantity(120);
        entity.setInputSnapshotId("dispatch-context-market-dashboard-001");
        entity.setBidVersion("bid-dashboard-v1");
        entity.setStatus("CLEARED");
        entity.setValidUntil(LATEST_START.plusHours(3));
        entity.setTestData(true);
        entity.setCreatedAt(LATEST_START.plusMinutes(10));
        return entity;
    }

    private List<MarketNewsEntity> buildNews() {
        List<MarketNewsEntity> news = new ArrayList<>();
        for (int index = 0; index < 5; index++) {
            MarketNewsEntity entity = new MarketNewsEntity();
            entity.setNewsId("news-" + index);
            entity.setTitle("市场新闻-" + index);
            entity.setPublishedAt(LATEST_START.minusHours(index));
            entity.setSource("资讯源");
            entity.setCreatedAt(LATEST_START.minusHours(index).plusMinutes(1));
            news.add(entity);
        }
        return news;
    }

    private <T> List<T> reversed(List<T> source) {
        List<T> reversed = new ArrayList<>(source);
        java.util.Collections.reverse(reversed);
        return reversed;
    }
}

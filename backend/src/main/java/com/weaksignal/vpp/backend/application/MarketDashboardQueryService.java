package com.weaksignal.vpp.backend.application;

import com.weaksignal.vpp.backend.api.dto.DispatchPlanItemResponse;
import com.weaksignal.vpp.backend.api.dto.DispatchPlanResponse;
import com.weaksignal.vpp.backend.api.dto.ForecastPointResponse;
import com.weaksignal.vpp.backend.api.dto.ForecastSnapshotResponse;
import com.weaksignal.vpp.backend.api.dto.MarketDashboardOverviewResponse;
import com.weaksignal.vpp.backend.api.dto.MarketMeteringRecordResponse;
import com.weaksignal.vpp.backend.api.dto.MarketSignalResponse;
import com.weaksignal.vpp.backend.api.dto.ResourceCapabilityResponse;
import com.weaksignal.vpp.backend.api.dto.SettlementResultResponse;
import com.weaksignal.vpp.backend.domain.DispatchPlanStatus;
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
import com.weaksignal.vpp.backend.capability.ResourceCapabilityService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 市场与电价页面查询服务。
 */
@Service
public class MarketDashboardQueryService {

    private static final String SIMULATOR_TELEMETRY_SOURCE = "SIMULATOR_LIVE_TELEMETRY";
    private static final String SIMULATOR_TELEMETRY_VERSION = "simulator-live-telemetry-v1";
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final DateTimeFormatter HOUR_MINUTE_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter MONTH_DAY_HOUR_FORMATTER = DateTimeFormatter.ofPattern("MM-dd HH:mm");
    private static final DateTimeFormatter TABLE_PERIOD_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    /**
     * 市场页面统一使用的业务时区偏移。
     */
    private static final ZoneOffset MARKET_ZONE_OFFSET = ZoneOffset.ofHours(8);

    private final MarketSignalService marketSignalService;
    private final ForecastService forecastService;
    private final ResourceCapabilityService resourceCapabilityService;
    private final MarketDispatchPlanningService marketDispatchPlanningService;
    private final MarketSettlementService marketSettlementService;
    private final MarketSignalSnapshotRepository marketSignalSnapshotRepository;
    private final MarketBidRepository marketBidRepository;
    private final MarketClearingResultRepository marketClearingResultRepository;
    private final MarketNewsRepository marketNewsRepository;
    private final Clock clock;

    /**
     * 注入市场与电价页面查询依赖。
     *
     * @param marketSignalService 市场信号服务
     * @param forecastService 预测服务
     * @param resourceCapabilityService 资源能力服务
     * @param marketDispatchPlanningService 市场调度计划服务
     * @param marketSettlementService 市场结算服务
     * @param marketSignalSnapshotRepository 市场信号仓储
     * @param marketBidRepository 市场报价仓储
     * @param marketClearingResultRepository 市场出清仓储
     * @param marketNewsRepository 市场新闻仓储
     * @param clock 系统时钟
     */
    public MarketDashboardQueryService(
            MarketSignalService marketSignalService,
            ForecastService forecastService,
            ResourceCapabilityService resourceCapabilityService,
            MarketDispatchPlanningService marketDispatchPlanningService,
            MarketSettlementService marketSettlementService,
            MarketSignalSnapshotRepository marketSignalSnapshotRepository,
            MarketBidRepository marketBidRepository,
            MarketClearingResultRepository marketClearingResultRepository,
            MarketNewsRepository marketNewsRepository,
            Clock clock
    ) {
        this.marketSignalService = marketSignalService;
        this.forecastService = forecastService;
        this.resourceCapabilityService = resourceCapabilityService;
        this.marketDispatchPlanningService = marketDispatchPlanningService;
        this.marketSettlementService = marketSettlementService;
        this.marketSignalSnapshotRepository = marketSignalSnapshotRepository;
        this.marketBidRepository = marketBidRepository;
        this.marketClearingResultRepository = marketClearingResultRepository;
        this.marketNewsRepository = marketNewsRepository;
        this.clock = clock;
    }

    /**
     * 查询市场与电价页面聚合数据。
     *
     * @param rangeCode 时间范围编码
     * @param newsLimit 新闻条数
     * @return 聚合响应
     */
    @Transactional
    public MarketDashboardOverviewResponse getOverview(String rangeCode, int newsLimit) {
        MarketDashboardRange range = MarketDashboardRange.fromCode(rangeCode);
        int effectiveNewsLimit = Math.max(1, Math.min(newsLimit, 10));

        MarketSignalResponse latestSignal = marketSignalService.getLatestSnapshot();
        ForecastSnapshotResponse latestForecast = forecastService.getLatestSnapshot();
        DispatchPlanResponse latestPlan = marketDispatchPlanningService.getLatestPlan();
        SettlementResultResponse latestSettlement = marketSettlementService.getLatestSettlement();
        List<ResourceCapabilityResponse> latestCapabilities = resourceCapabilityService.getLatestCapabilities();
        MarketBidEntity latestBid = marketBidRepository.findFirstByOrderByCreatedAtDesc().orElse(null);
        MarketClearingResultEntity latestClearing = marketClearingResultRepository.findFirstByOrderByCreatedAtDesc().orElse(null);

        OffsetDateTime latestRangeEnd = latestSignal.intervalEnd();
        OffsetDateTime priceTrendStart = latestRangeEnd.minusHours(range.hours());
        List<MarketSignalSnapshotEntity> priceTrendSignals = marketSignalSnapshotRepository
                .findBySourceAndVersionAndIntervalStartGreaterThanEqualAndIntervalStartLessThanOrderByIntervalStartAsc(
                        SIMULATOR_TELEMETRY_SOURCE,
                        SIMULATOR_TELEMETRY_VERSION,
                        priceTrendStart,
                        latestRangeEnd
                );
        List<MarketSignalSnapshotEntity> latestTwentyFourSignals = loadLatestSimulatorSignals(24);
        List<MarketClearingResultEntity> latestTwentyFourClearings = loadLatestClearings(24);
        Map<OffsetDateTime, BigDecimal> forecastPriceByStart = buildForecastPriceMap(latestForecast.points());

        return new MarketDashboardOverviewResponse(
                buildSummary(latestSignal, latestPlan, latestSettlement, latestBid, latestClearing, latestTwentyFourClearings, forecastPriceByStart),
                buildSparklines(latestTwentyFourSignals, latestTwentyFourClearings, forecastPriceByStart),
                buildPriceTrend(range, priceTrendSignals, forecastPriceByStart),
                buildDistribution(latestTwentyFourSignals),
                buildTimeOfUsePrices(latestTwentyFourSignals, forecastPriceByStart),
                buildPredictionAnalysis(latestTwentyFourSignals, forecastPriceByStart),
                buildRecommendations(latestPlan, latestCapabilities, latestTwentyFourSignals, forecastPriceByStart),
                buildNews(effectiveNewsLimit),
                buildDataSourceStatuses(latestSignal, latestForecast, latestClearing, latestCapabilities)
        );
    }

    private MarketDashboardOverviewResponse.Summary buildSummary(
            MarketSignalResponse latestSignal,
            DispatchPlanResponse latestPlan,
            SettlementResultResponse latestSettlement,
            MarketBidEntity latestBid,
            MarketClearingResultEntity latestClearing,
            List<MarketClearingResultEntity> latestTwentyFourClearings,
            Map<OffsetDateTime, BigDecimal> forecastPriceByStart
    ) {
        BigDecimal contractEnergyMwh = latestBid == null
                ? scale(BigDecimal.valueOf(latestSignal.contractPower()))
                : scale(BigDecimal.valueOf(latestBid.getTotalQuantity()));
        BigDecimal executedEnergyMwh = scale(sumMeteringEnergyKwh(latestSettlement.meteringRecords()).divide(BigDecimal.valueOf(1000), 6, RoundingMode.HALF_UP));
        BigDecimal contractProgressPercent = contractEnergyMwh.compareTo(BigDecimal.ZERO) == 0
                ? ZERO
                : scale(executedEnergyMwh.multiply(BigDecimal.valueOf(100)).divide(contractEnergyMwh, 6, RoundingMode.HALF_UP));
        BigDecimal peakForecastPrice = calculatePeakForecastPrice(forecastPriceByStart);
        BigDecimal averageForecastPrice = calculateAverageForecastPrice(forecastPriceByStart, latestSignal.intervalEnd(), 24);
        BigDecimal todayTradeVolumeMwh = scale(latestTwentyFourClearings.stream()
                .map(item -> BigDecimal.valueOf(item.getClearedPower()))
                .reduce(BigDecimal.ZERO, BigDecimal::add));

        return new MarketDashboardOverviewResponse.Summary(
                scale(latestSignal.realTimePrice()),
                peakForecastPrice,
                "18:00-21:00",
                contractEnergyMwh,
                executedEnergyMwh,
                contractProgressPercent,
                averageForecastPrice,
                todayTradeVolumeMwh,
                resolveMarketName(latestSignal.marketType()),
                latestPlan.intervalMinutes() + "分钟",
                formatPeriod(latestSignal.intervalStart(), latestSignal.intervalEnd()),
                resolveCurrentPhase(latestPlan.status()),
                latestClearing == null ? "待出清" : "已出清"
        );
    }

    private MarketDashboardOverviewResponse.SummarySparklines buildSparklines(
            List<MarketSignalSnapshotEntity> latestTwentyFourSignals,
            List<MarketClearingResultEntity> latestTwentyFourClearings,
            Map<OffsetDateTime, BigDecimal> forecastPriceByStart
    ) {
        List<MarketSignalSnapshotEntity> latestSeventeenSignals = tail(latestTwentyFourSignals, 17);
        List<MarketClearingResultEntity> latestSeventeenClearings = tail(latestTwentyFourClearings, 17);
        return new MarketDashboardOverviewResponse.SummarySparklines(
                latestSeventeenSignals.stream().map(signal -> scale(signal.getRealTimePrice())).toList(),
                latestSeventeenSignals.stream()
                        .map(signal -> scale(defaultIfNull(forecastPriceAt(forecastPriceByStart, normalizeMarketTime(signal.getIntervalStart()).withHour(18).withMinute(0).withSecond(0).withNano(0)), signal.getDayAheadPrice())))
                        .toList(),
                latestSeventeenSignals.stream()
                        .map(signal -> scale(defaultIfNull(forecastPriceAt(forecastPriceByStart, signal.getIntervalStart()), signal.getDayAheadPrice())))
                        .toList(),
                latestSeventeenClearings.stream()
                        .map(item -> scale(BigDecimal.valueOf(item.getClearedPower())))
                        .toList()
        );
    }

    private MarketDashboardOverviewResponse.PriceTrend buildPriceTrend(
            MarketDashboardRange range,
            List<MarketSignalSnapshotEntity> priceTrendSignals,
            Map<OffsetDateTime, BigDecimal> forecastPriceByStart
    ) {
        List<MarketSignalSnapshotEntity> sampledSignals = switch (range) {
            case DAY_7 -> sampleByStep(priceTrendSignals, 6);
            case HOUR_48 -> sampleByStep(priceTrendSignals, 2);
            default -> priceTrendSignals;
        };
        DateTimeFormatter formatter = range == MarketDashboardRange.DAY_7 ? MONTH_DAY_HOUR_FORMATTER : HOUR_MINUTE_FORMATTER;
        List<MarketDashboardOverviewResponse.PriceTrendPoint> points = sampledSignals.stream()
                .map(signal -> new MarketDashboardOverviewResponse.PriceTrendPoint(
                        normalizeMarketTime(signal.getIntervalStart()).format(formatter),
                        scale(signal.getRealTimePrice()),
                        scale(signal.getDayAheadPrice()),
                        scale(defaultIfNull(forecastPriceAt(forecastPriceByStart, signal.getIntervalStart()), signal.getDayAheadPrice())),
                        scale(defaultIfNull(
                                forecastPriceAt(forecastPriceByStart, signal.getIntervalStart().plusDays(1)),
                                defaultIfNull(forecastPriceAt(forecastPriceByStart, signal.getIntervalStart()), signal.getDayAheadPrice())
                        ))
                ))
                .toList();
        return new MarketDashboardOverviewResponse.PriceTrend(range.code(), points);
    }

    private List<MarketDashboardOverviewResponse.DistributionItem> buildDistribution(List<MarketSignalSnapshotEntity> latestTwentyFourSignals) {
        List<BigDecimal> prices = latestTwentyFourSignals.stream().map(MarketSignalSnapshotEntity::getRealTimePrice).filter(Objects::nonNull).toList();
        int total = prices.size();
        if (total == 0) {
            return List.of(
                    distributionItem("< 0.5 元/kWh", 0, 1),
                    distributionItem("0.5 - 0.8 元/kWh", 0, 1),
                    distributionItem("0.8 - 1.2 元/kWh", 0, 1),
                    distributionItem("1.2 - 1.5 元/kWh", 0, 1),
                    distributionItem("> 1.5 元/kWh", 0, 1)
            );
        }
        long lowerThanFive = prices.stream().filter(price -> price.compareTo(BigDecimal.valueOf(0.5)) < 0).count();
        long betweenFiveAndEight = prices.stream().filter(price -> price.compareTo(BigDecimal.valueOf(0.5)) >= 0
                && price.compareTo(BigDecimal.valueOf(0.8)) < 0).count();
        long betweenEightAndTwelve = prices.stream().filter(price -> price.compareTo(BigDecimal.valueOf(0.8)) >= 0
                && price.compareTo(BigDecimal.valueOf(1.2)) < 0).count();
        long betweenTwelveAndFifteen = prices.stream().filter(price -> price.compareTo(BigDecimal.valueOf(1.2)) >= 0
                && price.compareTo(BigDecimal.valueOf(1.5)) <= 0).count();
        long greaterThanFifteen = prices.stream().filter(price -> price.compareTo(BigDecimal.valueOf(1.5)) > 0).count();
        return List.of(
                distributionItem("< 0.5 元/kWh", lowerThanFive, total),
                distributionItem("0.5 - 0.8 元/kWh", betweenFiveAndEight, total),
                distributionItem("0.8 - 1.2 元/kWh", betweenEightAndTwelve, total),
                distributionItem("1.2 - 1.5 元/kWh", betweenTwelveAndFifteen, total),
                distributionItem("> 1.5 元/kWh", greaterThanFifteen, total)
        );
    }

    private List<MarketDashboardOverviewResponse.TimeOfUsePriceItem> buildTimeOfUsePrices(
            List<MarketSignalSnapshotEntity> latestTwentyFourSignals,
            Map<OffsetDateTime, BigDecimal> forecastPriceByStart
    ) {
        return latestTwentyFourSignals.stream()
                .map(signal -> new MarketDashboardOverviewResponse.TimeOfUsePriceItem(
                        formatPeriod(signal.getIntervalStart(), signal.getIntervalEnd()),
                        scale(signal.getRealTimePrice()),
                        scale(signal.getDayAheadPrice()),
                        scale(defaultIfNull(forecastPriceAt(forecastPriceByStart, signal.getIntervalStart()), signal.getDayAheadPrice())),
                        scale(defaultIfNull(
                                forecastPriceAt(forecastPriceByStart, signal.getIntervalStart().plusDays(1)),
                                defaultIfNull(forecastPriceAt(forecastPriceByStart, signal.getIntervalStart()), signal.getDayAheadPrice())
                        ))
                ))
                .toList();
    }

    private MarketDashboardOverviewResponse.PredictionAnalysis buildPredictionAnalysis(
            List<MarketSignalSnapshotEntity> latestTwentyFourSignals,
            Map<OffsetDateTime, BigDecimal> forecastPriceByStart
    ) {
        List<MarketDashboardOverviewResponse.PredictionPoint> todayTrend = latestTwentyFourSignals.stream()
                .map(signal -> new MarketDashboardOverviewResponse.PredictionPoint(
                        normalizeMarketTime(signal.getIntervalStart()).format(HOUR_MINUTE_FORMATTER),
                        scale(defaultIfNull(forecastPriceAt(forecastPriceByStart, signal.getIntervalStart()), signal.getDayAheadPrice()))
                ))
                .toList();
        List<MarketDashboardOverviewResponse.PredictionPoint> tomorrowTrend = latestTwentyFourSignals.stream()
                .map(signal -> new MarketDashboardOverviewResponse.PredictionPoint(
                        normalizeMarketTime(signal.getIntervalStart()).format(HOUR_MINUTE_FORMATTER),
                        scale(defaultIfNull(
                                forecastPriceAt(forecastPriceByStart, signal.getIntervalStart().plusDays(1)),
                                defaultIfNull(forecastPriceAt(forecastPriceByStart, signal.getIntervalStart()), signal.getDayAheadPrice())
                        ))
                ))
                .toList();
        return new MarketDashboardOverviewResponse.PredictionAnalysis(
                todayTrend,
                tomorrowTrend,
                buildPredictionConclusion(latestTwentyFourSignals, forecastPriceByStart)
        );
    }

    private List<MarketDashboardOverviewResponse.RecommendationItem> buildRecommendations(
            DispatchPlanResponse latestPlan,
            List<ResourceCapabilityResponse> latestCapabilities,
            List<MarketSignalSnapshotEntity> latestTwentyFourSignals,
            Map<OffsetDateTime, BigDecimal> forecastPriceByStart
    ) {
        ResourceCapabilityResponse storageCapability = latestCapabilities.stream()
                .filter(item -> item.resourceType() == ResourceType.STORAGE && item.storageConstraint() != null)
                .findFirst()
                .orElse(null);
        BigDecimal chargeWindowAverage = averageForecastForHours(forecastPriceByStart, latestTwentyFourSignals, 0, 6);
        BigDecimal dischargeWindowAverage = averageForecastForHours(forecastPriceByStart, latestTwentyFourSignals, 18, 21);
        BigDecimal currentAverage = averageRealtimePrice(latestTwentyFourSignals);

        DispatchPlanItemResponse chargeItem = latestPlan.items().stream()
                .max(Comparator.comparingDouble(DispatchPlanItemResponse::storageChargePower))
                .orElse(null);
        DispatchPlanItemResponse dischargeItem = latestPlan.items().stream()
                .max(Comparator.comparingDouble(DispatchPlanItemResponse::storageDischargePower))
                .orElse(null);
        DispatchPlanItemResponse demandResponseItem = latestPlan.items().stream()
                .max(Comparator.comparingDouble(DispatchPlanItemResponse::flexibleLoadAdjustment))
                .orElse(null);

        BigDecimal storageChargeValue = calculateStorageChargeValue(storageCapability, chargeWindowAverage, dischargeWindowAverage);
        BigDecimal storageDischargeValue = calculateStorageDischargeValue(storageCapability, dischargeWindowAverage, chargeWindowAverage);
        BigDecimal demandResponseValue = calculateDemandResponseValue(demandResponseItem, currentAverage, dischargeWindowAverage);

        return List.of(
                new MarketDashboardOverviewResponse.RecommendationItem(
                        "storage-charge",
                        "储能充电建议",
                        chargeItem == null
                                ? "当前无可执行的充电时间片。"
                                : "建议在 " + formatPeriod(chargeItem.intervalStart(), chargeItem.intervalEnd()) + " 低价时段充电，利用峰谷价差补能。",
                        "预计可节省成本",
                        storageChargeValue,
                        "green"
                ),
                new MarketDashboardOverviewResponse.RecommendationItem(
                        "storage-discharge",
                        "储能放电建议",
                        dischargeItem == null
                                ? "当前无可执行的放电时间片。"
                                : "建议在 " + formatPeriod(dischargeItem.intervalStart(), dischargeItem.intervalEnd()) + " 高价时段放电，释放储能收益。",
                        "预计可增加收益",
                        storageDischargeValue,
                        "orange"
                ),
                new MarketDashboardOverviewResponse.RecommendationItem(
                        "demand-response",
                        "需求响应建议",
                        demandResponseItem == null
                                ? "当前无需求响应时间片。"
                                : "建议在 " + formatPeriod(demandResponseItem.intervalStart(), demandResponseItem.intervalEnd()) + " 下调柔性负荷，降低高价购电敞口。",
                        "预计可获得收益",
                        demandResponseValue,
                        "blue"
                )
        );
    }

    private List<MarketDashboardOverviewResponse.NewsItem> buildNews(int newsLimit) {
        return marketNewsRepository.findAllByOrderByPublishedAtDesc(PageRequest.of(0, newsLimit)).stream()
                .map(item -> new MarketDashboardOverviewResponse.NewsItem(item.getPublishedAt(), item.getTitle()))
                .toList();
    }

    private List<MarketDashboardOverviewResponse.DataSourceStatusItem> buildDataSourceStatuses(
            MarketSignalResponse latestSignal,
            ForecastSnapshotResponse latestForecast,
            MarketClearingResultEntity latestClearing,
            List<ResourceCapabilityResponse> latestCapabilities
    ) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        boolean marketOnline = latestSignal.validUntil().isAfter(now);
        boolean forecastOnline = latestForecast.validUntil().isAfter(now);
        boolean clearingOnline = latestClearing != null;
        boolean capabilityOnline = latestCapabilities.stream().anyMatch(ResourceCapabilityResponse::dataTrusted);
        return List.of(
                new MarketDashboardOverviewResponse.DataSourceStatusItem("市场数据", marketOnline),
                new MarketDashboardOverviewResponse.DataSourceStatusItem("电价预测", forecastOnline),
                new MarketDashboardOverviewResponse.DataSourceStatusItem("行情接口", clearingOnline),
                new MarketDashboardOverviewResponse.DataSourceStatusItem("更新服务", capabilityOnline)
        );
    }

    private List<MarketSignalSnapshotEntity> loadLatestSimulatorSignals(int limit) {
        List<MarketSignalSnapshotEntity> reversed = new ArrayList<>(marketSignalSnapshotRepository
                .findBySourceAndVersionOrderByIntervalStartDesc(
                        SIMULATOR_TELEMETRY_SOURCE,
                        SIMULATOR_TELEMETRY_VERSION,
                        PageRequest.of(0, limit)
                ));
        reversed.sort(Comparator.comparing(MarketSignalSnapshotEntity::getIntervalStart));
        return reversed;
    }

    private List<MarketClearingResultEntity> loadLatestClearings(int limit) {
        List<MarketClearingResultEntity> reversed = new ArrayList<>(marketClearingResultRepository.findAllByOrderByIntervalStartDesc(PageRequest.of(0, limit)));
        reversed.sort(Comparator.comparing(MarketClearingResultEntity::getIntervalStart));
        return reversed;
    }

    private Map<OffsetDateTime, BigDecimal> buildForecastPriceMap(List<ForecastPointResponse> points) {
        Map<OffsetDateTime, BigDecimal> priceByStart = new LinkedHashMap<>();
        for (ForecastPointResponse point : points) {
            priceByStart.put(normalizeMarketTime(point.intervalStart()), point.priceP50());
        }
        return priceByStart;
    }

    private BigDecimal calculatePeakForecastPrice(Map<OffsetDateTime, BigDecimal> forecastPriceByStart) {
        return forecastPriceByStart.entrySet().stream()
                .filter(entry -> {
                    int hour = entry.getKey().getHour();
                    return hour >= 18 && hour < 21;
                })
                .map(Map.Entry::getValue)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .map(this::scale)
                .orElse(ZERO);
    }

    private BigDecimal calculateAverageForecastPrice(
            Map<OffsetDateTime, BigDecimal> forecastPriceByStart,
            OffsetDateTime endExclusive,
            int hours
    ) {
        List<BigDecimal> values = forecastPriceByStart.entrySet().stream()
                .filter(entry -> !entry.getKey().isBefore(normalizeMarketTime(endExclusive.minusHours(hours)))
                        && entry.getKey().isBefore(normalizeMarketTime(endExclusive)))
                .map(Map.Entry::getValue)
                .filter(Objects::nonNull)
                .toList();
        return average(values);
    }

    private BigDecimal averageForecastForHours(
            Map<OffsetDateTime, BigDecimal> forecastPriceByStart,
            List<MarketSignalSnapshotEntity> latestTwentyFourSignals,
            int startHourInclusive,
            int endHourExclusive
    ) {
        List<BigDecimal> values = latestTwentyFourSignals.stream()
                .filter(signal -> marketHour(signal.getIntervalStart()) >= startHourInclusive
                        && marketHour(signal.getIntervalStart()) < endHourExclusive)
                .map(signal -> defaultIfNull(forecastPriceAt(forecastPriceByStart, signal.getIntervalStart()), signal.getDayAheadPrice()))
                .toList();
        return average(values);
    }

    /**
     * 按市场业务时区查询预测价格。
     *
     * @param forecastPriceByStart 预测价格映射
     * @param intervalStart 时间片开始时间
     * @return 预测价格
     */
    private BigDecimal forecastPriceAt(Map<OffsetDateTime, BigDecimal> forecastPriceByStart, OffsetDateTime intervalStart) {
        return forecastPriceByStart.get(normalizeMarketTime(intervalStart));
    }

    /**
     * 将时间归一到市场业务时区，避免同一时刻因 Offset 不同导致预测点无法匹配。
     *
     * @param dateTime 原始时间
     * @return 市场业务时区时间
     */
    private OffsetDateTime normalizeMarketTime(OffsetDateTime dateTime) {
        return dateTime.withOffsetSameInstant(MARKET_ZONE_OFFSET);
    }

    /**
     * 获取市场业务时区下的小时数。
     *
     * @param dateTime 原始时间
     * @return 市场业务时区小时
     */
    private int marketHour(OffsetDateTime dateTime) {
        return normalizeMarketTime(dateTime).getHour();
    }

    private BigDecimal averageRealtimePrice(List<MarketSignalSnapshotEntity> latestTwentyFourSignals) {
        return average(latestTwentyFourSignals.stream()
                .map(MarketSignalSnapshotEntity::getRealTimePrice)
                .filter(Objects::nonNull)
                .toList());
    }

    private BigDecimal calculateStorageChargeValue(
            ResourceCapabilityResponse storageCapability,
            BigDecimal chargeWindowAverage,
            BigDecimal dischargeWindowAverage
    ) {
        if (storageCapability == null || storageCapability.storageConstraint() == null) {
            return ZERO;
        }
        BigDecimal availableChargePower = BigDecimal.valueOf(storageCapability.storageConstraint().maximumChargePowerKW());
        BigDecimal arbitrageSpread = dischargeWindowAverage.subtract(chargeWindowAverage).subtract(storageCapability.storageConstraint().cycleCost());
        if (arbitrageSpread.compareTo(BigDecimal.ZERO) < 0) {
            return ZERO;
        }
        return scale(arbitrageSpread.multiply(availableChargePower).multiply(BigDecimal.valueOf(3)));
    }

    private BigDecimal calculateStorageDischargeValue(
            ResourceCapabilityResponse storageCapability,
            BigDecimal dischargeWindowAverage,
            BigDecimal chargeWindowAverage
    ) {
        if (storageCapability == null || storageCapability.storageConstraint() == null) {
            return ZERO;
        }
        BigDecimal availableDischargePower = BigDecimal.valueOf(storageCapability.storageConstraint().maximumDischargePowerKW());
        BigDecimal arbitrageSpread = dischargeWindowAverage.subtract(chargeWindowAverage);
        if (arbitrageSpread.compareTo(BigDecimal.ZERO) < 0) {
            return ZERO;
        }
        return scale(arbitrageSpread.multiply(availableDischargePower).multiply(BigDecimal.valueOf(3.5)));
    }

    private BigDecimal calculateDemandResponseValue(
            DispatchPlanItemResponse demandResponseItem,
            BigDecimal currentAverage,
            BigDecimal dischargeWindowAverage
    ) {
        if (demandResponseItem == null) {
            return ZERO;
        }
        BigDecimal flexibleLoad = BigDecimal.valueOf(demandResponseItem.flexibleLoadAdjustment());
        BigDecimal incentive = dischargeWindowAverage.max(currentAverage).multiply(BigDecimal.valueOf(0.65));
        return scale(flexibleLoad.multiply(incentive));
    }

    private String buildPredictionConclusion(
            List<MarketSignalSnapshotEntity> latestTwentyFourSignals,
            Map<OffsetDateTime, BigDecimal> forecastPriceByStart
    ) {
        BigDecimal lowWindowAverage = averageForecastForHours(forecastPriceByStart, latestTwentyFourSignals, 0, 6);
        BigDecimal peakWindowAverage = averageForecastForHours(forecastPriceByStart, latestTwentyFourSignals, 18, 21);
        BigDecimal spread = peakWindowAverage.subtract(lowWindowAverage);
        if (spread.compareTo(BigDecimal.valueOf(0.25)) >= 0) {
            return String.format(
                    Locale.ROOT,
                    "预计18:00-21:00为高价窗口，较00:00-06:00低谷均价高%s元/kWh，建议优先在低价时段充电并在晚高峰放电。",
                    scale(spread).toPlainString()
            );
        }
        return String.format(
                Locale.ROOT,
                "预计未来24小时电价波动较平缓，峰谷价差约为%s元/kWh，建议以稳态跟踪和柔性负荷微调为主。",
                scale(spread.max(BigDecimal.ZERO)).toPlainString()
        );
    }

    private List<MarketSignalSnapshotEntity> sampleByStep(List<MarketSignalSnapshotEntity> signals, int step) {
        List<MarketSignalSnapshotEntity> sampled = new ArrayList<>();
        for (int index = 0; index < signals.size(); index += step) {
            sampled.add(signals.get(index));
        }
        if (!signals.isEmpty() && !sampled.contains(signals.get(signals.size() - 1))) {
            sampled.add(signals.get(signals.size() - 1));
        }
        return sampled;
    }

    private MarketDashboardOverviewResponse.DistributionItem distributionItem(String bucket, long count, int total) {
        BigDecimal percentage = total <= 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(count * 100.0 / total).setScale(1, RoundingMode.HALF_UP);
        return new MarketDashboardOverviewResponse.DistributionItem(bucket, percentage);
    }

    private <T> List<T> tail(List<T> items, int size) {
        if (items.size() <= size) {
            return items;
        }
        return items.subList(items.size() - size, items.size());
    }

    private BigDecimal sumMeteringEnergyKwh(List<MarketMeteringRecordResponse> meteringRecords) {
        return meteringRecords.stream()
                .map(MarketMeteringRecordResponse::energyKwh)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal average(List<BigDecimal> values) {
        if (values.isEmpty()) {
            return ZERO;
        }
        BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return scale(sum.divide(BigDecimal.valueOf(values.size()), 6, RoundingMode.HALF_UP));
    }

    private BigDecimal scale(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal defaultIfNull(BigDecimal value, BigDecimal defaultValue) {
        return value == null ? defaultValue : value;
    }

    private String formatPeriod(OffsetDateTime start, OffsetDateTime end) {
        return normalizeMarketTime(start).format(TABLE_PERIOD_FORMATTER) + " - " + normalizeMarketTime(end).format(TABLE_PERIOD_FORMATTER);
    }

    private String resolveMarketName(String marketType) {
        if ("TEST_MARKET".equalsIgnoreCase(marketType) || "DAY_AHEAD".equalsIgnoreCase(marketType)) {
            return "西北电力市场";
        }
        return "区域电力市场";
    }

    private String resolveCurrentPhase(DispatchPlanStatus status) {
        return switch (status) {
            case ACTIVE -> "实时运行";
            case APPROVED, VALIDATED -> "执行准备";
            case COMPLETED -> "日终归档";
            default -> "计划编制";
        };
    }

    /**
     * 市场看板时间范围。
     */
    private enum MarketDashboardRange {
        HOUR_24("24H", 24),
        HOUR_48("48H", 48),
        DAY_7("7D", 168);

        private final String code;
        private final int hours;

        MarketDashboardRange(String code, int hours) {
            this.code = code;
            this.hours = hours;
        }

        public String code() {
            return code;
        }

        public int hours() {
            return hours;
        }

        /**
         * 按编码解析时间范围。
         *
         * @param code 时间范围编码
         * @return 时间范围
         */
        public static MarketDashboardRange fromCode(String code) {
            if (code == null || code.isBlank()) {
                return HOUR_24;
            }
            for (MarketDashboardRange value : values()) {
                if (value.code.equalsIgnoreCase(code.trim())) {
                    return value;
                }
            }
            return HOUR_24;
        }
    }
}

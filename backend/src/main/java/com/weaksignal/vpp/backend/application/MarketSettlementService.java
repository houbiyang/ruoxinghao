package com.weaksignal.vpp.backend.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weaksignal.vpp.backend.api.ApiException;
import com.weaksignal.vpp.backend.api.dto.MarketBidCreateRequest;
import com.weaksignal.vpp.backend.api.dto.MarketBidResponse;
import com.weaksignal.vpp.backend.api.dto.MarketBidSegmentResponse;
import com.weaksignal.vpp.backend.api.dto.MarketClearingResponse;
import com.weaksignal.vpp.backend.api.dto.MarketClearingTestRequest;
import com.weaksignal.vpp.backend.api.dto.MarketMeteringRecordResponse;
import com.weaksignal.vpp.backend.api.dto.SettlementCalculationRequest;
import com.weaksignal.vpp.backend.api.dto.SettlementResultResponse;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.DispatchPlanEntity;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.DispatchPlanItemEntity;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.MarketBidEntity;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.MarketBidSegmentEntity;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.MarketClearingResultEntity;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.MarketMeteringRecordEntity;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.SettlementResultEntity;
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
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 测试市场报价、出清、计量与结算服务。
 */
@Service
public class MarketSettlementService {

    private static final int SETTLEMENT_TELEMETRY_LIMIT = 20_000;

    private static final BigDecimal DEVIATION_PENALTY_FACTOR = new BigDecimal("0.150000");
    private static final BigDecimal GRID_IMPORT_COST_FACTOR = new BigDecimal("1.020000");
    private static final BigDecimal GRID_EXPORT_REVENUE_FACTOR = new BigDecimal("0.980000");
    private static final BigDecimal STORAGE_CYCLE_COST_FACTOR = new BigDecimal("0.020000");
    private static final BigDecimal DEMAND_RESPONSE_FACTOR = new BigDecimal("0.060000");
    private static final BigDecimal CURTAILMENT_COST_FACTOR = new BigDecimal("0.030000");
    private static final BigDecimal ANCILLARY_REVENUE_FACTOR = new BigDecimal("0.200000");
    private static final TypeReference<Map<String, BigDecimal>> REVENUE_ALLOCATION_TYPE = new TypeReference<>() {
    };

    private final MarketBidRepository marketBidRepository;
    private final MarketBidSegmentRepository marketBidSegmentRepository;
    private final MarketClearingResultRepository marketClearingResultRepository;
    private final MarketMeteringRecordRepository marketMeteringRecordRepository;
    private final SettlementResultRepository settlementResultRepository;
    private final DispatchPlanRepository dispatchPlanRepository;
    private final DispatchPlanItemRepository dispatchPlanItemRepository;
    private final TelemetryHistoryRepository telemetryHistoryRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    /**
     * 注入测试市场结算服务依赖。
     *
     * @param marketBidRepository 市场报价仓储
     * @param marketBidSegmentRepository 市场报价分段仓储
     * @param marketClearingResultRepository 测试出清仓储
     * @param marketMeteringRecordRepository 计量记录仓储
     * @param settlementResultRepository 结算结果仓储
     * @param dispatchPlanRepository 调度计划仓储
     * @param dispatchPlanItemRepository 调度时间片仓储
     * @param telemetryHistoryRepository 遥测历史仓储
     * @param objectMapper JSON 处理器
     * @param clock 系统时钟
     */
    public MarketSettlementService(
            MarketBidRepository marketBidRepository,
            MarketBidSegmentRepository marketBidSegmentRepository,
            MarketClearingResultRepository marketClearingResultRepository,
            MarketMeteringRecordRepository marketMeteringRecordRepository,
            SettlementResultRepository settlementResultRepository,
            DispatchPlanRepository dispatchPlanRepository,
            DispatchPlanItemRepository dispatchPlanItemRepository,
            TelemetryHistoryRepository telemetryHistoryRepository,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.marketBidRepository = marketBidRepository;
        this.marketBidSegmentRepository = marketBidSegmentRepository;
        this.marketClearingResultRepository = marketClearingResultRepository;
        this.marketMeteringRecordRepository = marketMeteringRecordRepository;
        this.settlementResultRepository = settlementResultRepository;
        this.dispatchPlanRepository = dispatchPlanRepository;
        this.dispatchPlanItemRepository = dispatchPlanItemRepository;
        this.telemetryHistoryRepository = telemetryHistoryRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * 创建测试市场报价。
     *
     * @param request 报价请求
     * @return 报价响应
     */
    @Transactional
    public MarketBidResponse createBid(MarketBidCreateRequest request) {
        validateBidRequest(request);
        OffsetDateTime now = OffsetDateTime.now(clock);
        double totalQuantity = round(request.segments().stream()
                .mapToDouble(segment -> segment.quantity() == null ? 0.0 : segment.quantity())
                .sum());
        MarketBidEntity entity = new MarketBidEntity();
        entity.setBidId("bid-" + UUID.randomUUID());
        entity.setMarketType(request.marketType());
        entity.setAggregateObject(request.aggregateObject());
        entity.setIntervalStart(request.intervalStart());
        entity.setIntervalEnd(request.intervalEnd());
        entity.setAvailableCapacity(round(request.availableCapacity()));
        entity.setTotalQuantity(totalQuantity);
        entity.setInputSnapshotId(request.inputSnapshotId());
        entity.setBidVersion(request.bidVersion());
        entity.setStatus("CREATED");
        entity.setValidUntil(request.validUntil());
        entity.setTestData(request.testData() == null || request.testData());
        entity.setCreatedAt(now);
        marketBidRepository.save(entity);

        List<MarketBidSegmentEntity> segments = new ArrayList<>();
        for (int index = 0; index < request.segments().size(); index++) {
            MarketBidSegmentEntity segmentEntity = new MarketBidSegmentEntity();
            segmentEntity.setBidId(entity.getBidId());
            segmentEntity.setSegmentOrder(index + 1);
            segmentEntity.setQuantity(round(request.segments().get(index).quantity()));
            segmentEntity.setPrice(scale(request.segments().get(index).price()));
            segmentEntity.setCreatedAt(now);
            segments.add(segmentEntity);
        }
        marketBidSegmentRepository.saveAll(segments);
        return toBidResponse(entity, segments);
    }

    /**
     * 查询报价详情。
     *
     * @param bidId 报价标识
     * @return 报价响应
     */
    @Transactional(readOnly = true)
    public MarketBidResponse getBid(String bidId) {
        MarketBidEntity entity = requireBid(bidId);
        return toBidResponse(entity, marketBidSegmentRepository.findByBidIdOrderBySegmentOrderAsc(bidId));
    }

    /**
     * 执行测试出清。
     *
     * @param request 测试出清请求
     * @return 出清响应
     */
    @Transactional
    public MarketClearingResponse createTestClearing(MarketClearingTestRequest request) {
        MarketBidEntity bidEntity = requireBid(request.bidId());
        if (!bidEntity.getValidUntil().isAfter(OffsetDateTime.now(clock))) {
            throw badRequest("MARKET_BID_EXPIRED", "报价已过期，不能测试出清", Map.of("bidId", request.bidId()));
        }
        List<MarketBidSegmentEntity> segments = marketBidSegmentRepository.findByBidIdOrderBySegmentOrderAsc(request.bidId());
        if (segments.isEmpty()) {
            throw badRequest("MARKET_BID_SEGMENT_EMPTY", "报价缺少分段数据", Map.of("bidId", request.bidId()));
        }

        double clearedPower = round(Math.min(bidEntity.getAvailableCapacity(), bidEntity.getTotalQuantity()));
        BigDecimal weightedPrice = weightedAveragePrice(segments, clearedPower);
        double ancillaryServicePower = round(Math.min(
                request.ancillaryServicePower() == null ? 0.0 : request.ancillaryServicePower(),
                Math.max(0.0, bidEntity.getAvailableCapacity() - clearedPower)
        ));
        MarketClearingResultEntity entity = new MarketClearingResultEntity();
        entity.setClearingId("clearing-" + UUID.randomUUID());
        entity.setBidId(bidEntity.getBidId());
        entity.setMarketType(bidEntity.getMarketType());
        entity.setIntervalStart(bidEntity.getIntervalStart());
        entity.setIntervalEnd(bidEntity.getIntervalEnd());
        entity.setClearedPower(clearedPower);
        entity.setClearedPrice(weightedPrice);
        entity.setAncillaryServicePower(ancillaryServicePower);
        entity.setExternalTradeId(resolveExternalTradeId(request.externalTradeId()));
        entity.setTestData(true);
        entity.setCreatedAt(OffsetDateTime.now(clock));
        marketClearingResultRepository.save(entity);

        bidEntity.setStatus("CLEARED");
        marketBidRepository.save(bidEntity);
        return toClearingResponse(entity);
    }

    /**
     * 查询测试出清详情。
     *
     * @param clearingId 出清标识
     * @return 出清响应
     */
    @Transactional(readOnly = true)
    public MarketClearingResponse getClearing(String clearingId) {
        return toClearingResponse(requireClearing(clearingId));
    }

    /**
     * 计算测试市场结算结果。
     *
     * @param request 结算请求
     * @return 结算响应
     */
    @Transactional
    public SettlementResultResponse calculateSettlement(SettlementCalculationRequest request) {
        if (settlementResultRepository.findByPlanIdAndClearingId(request.planId(), request.clearingId()).isPresent()) {
            throw conflict("SETTLEMENT_ALREADY_EXISTS", "同一计划与出清结果已完成结算");
        }

        DispatchPlanEntity planEntity = requirePlan(request.planId());
        MarketClearingResultEntity clearingEntity = requireClearing(request.clearingId());
        List<DispatchPlanItemEntity> overlappedItems = dispatchPlanItemRepository.findByPlanIdOrderByItemOrderAsc(request.planId()).stream()
                .filter(item -> overlap(item.getIntervalStart(), item.getIntervalEnd(), clearingEntity.getIntervalStart(), clearingEntity.getIntervalEnd()))
                .toList();
        if (overlappedItems.isEmpty()) {
            throw badRequest(
                    "SETTLEMENT_PLAN_INTERVAL_MISMATCH",
                    "计划时间片与出清时间片不重叠，无法结算",
                    Map.of("planId", request.planId(), "clearingId", request.clearingId())
            );
        }

        List<TelemetryHistoryEntity> telemetryHistory = telemetryHistoryRepository.findByReceivedTimestampBetweenOrderByReceivedTimestampAsc(
                clearingEntity.getIntervalStart(),
                clearingEntity.getIntervalEnd(),
                org.springframework.data.domain.PageRequest.of(0, SETTLEMENT_TELEMETRY_LIMIT)
        );
        Map<String, List<TelemetryHistoryEntity>> stationTelemetryMap = telemetryHistory.stream()
                .collect(Collectors.groupingBy(TelemetryHistoryEntity::getStationId, LinkedHashMap::new, Collectors.toList()));
        OffsetDateTime now = OffsetDateTime.now(clock);
        String settlementId = "settlement-" + UUID.randomUUID();

        List<MarketMeteringRecordEntity> meteringEntities = new ArrayList<>();
        for (Map.Entry<String, List<TelemetryHistoryEntity>> entry : stationTelemetryMap.entrySet()) {
            StationMetering stationMetering = buildStationMetering(
                    settlementId,
                    request.planId(),
                    entry.getKey(),
                    clearingEntity.getIntervalStart(),
                    clearingEntity.getIntervalEnd(),
                    entry.getValue(),
                    now
            );
            if (stationMetering.sampleCount() > 0) {
                meteringEntities.add(stationMetering.entity());
            }
        }
        marketMeteringRecordRepository.saveAll(meteringEntities);

        BigDecimal intervalHours = durationHours(clearingEntity.getIntervalStart(), clearingEntity.getIntervalEnd());
        BigDecimal clearedEnergy = scale(BigDecimal.valueOf(clearingEntity.getClearedPower()).multiply(intervalHours));
        BigDecimal totalEnergyRevenue = scale(clearedEnergy.multiply(clearingEntity.getClearedPrice()));

        BigDecimal plannedNetEnergy = BigDecimal.ZERO;
        BigDecimal gridImportCost = BigDecimal.ZERO;
        BigDecimal gridExportRevenue = BigDecimal.ZERO;
        BigDecimal storageCycleCost = BigDecimal.ZERO;
        BigDecimal demandResponseCompensation = BigDecimal.ZERO;
        BigDecimal renewableCurtailmentCost = BigDecimal.ZERO;
        for (DispatchPlanItemEntity itemEntity : overlappedItems) {
            BigDecimal itemHours = durationHours(itemEntity.getIntervalStart(), itemEntity.getIntervalEnd());
            plannedNetEnergy = plannedNetEnergy.add(scale(BigDecimal.valueOf(itemEntity.getNetPowerTarget()).multiply(itemHours)));
            gridImportCost = gridImportCost.add(scale(
                    BigDecimal.valueOf(itemEntity.getGridImportPower())
                            .multiply(itemHours)
                            .multiply(clearingEntity.getClearedPrice())
                            .multiply(GRID_IMPORT_COST_FACTOR)
            ));
            gridExportRevenue = gridExportRevenue.add(scale(
                    BigDecimal.valueOf(itemEntity.getGridExportPower())
                            .multiply(itemHours)
                            .multiply(clearingEntity.getClearedPrice())
                            .multiply(GRID_EXPORT_REVENUE_FACTOR)
            ));
            storageCycleCost = storageCycleCost.add(scale(
                    BigDecimal.valueOf(itemEntity.getStorageChargePower() + itemEntity.getStorageDischargePower())
                            .multiply(itemHours)
                            .multiply(STORAGE_CYCLE_COST_FACTOR)
            ));
            demandResponseCompensation = demandResponseCompensation.add(scale(
                    BigDecimal.valueOf(Math.abs(itemEntity.getFlexibleLoadAdjustment()))
                            .multiply(itemHours)
                            .multiply(DEMAND_RESPONSE_FACTOR)
            ));
            renewableCurtailmentCost = renewableCurtailmentCost.add(scale(
                    BigDecimal.valueOf(itemEntity.getRenewableCurtailment())
                            .multiply(itemHours)
                            .multiply(CURTAILMENT_COST_FACTOR)
            ));
        }

        BigDecimal actualEnergy = meteringEntities.stream()
                .map(MarketMeteringRecordEntity::getEnergyKwh)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal deviationEnergy = actualEnergy.subtract(plannedNetEnergy).abs();
        BigDecimal deviationCost = scale(deviationEnergy.multiply(clearingEntity.getClearedPrice()).multiply(DEVIATION_PENALTY_FACTOR));
        BigDecimal ancillaryServiceRevenue = scale(
                BigDecimal.valueOf(clearingEntity.getAncillaryServicePower())
                        .multiply(intervalHours)
                        .multiply(clearingEntity.getClearedPrice())
                        .multiply(ANCILLARY_REVENUE_FACTOR)
        );
        BigDecimal netRevenue = totalEnergyRevenue
                .add(gridExportRevenue)
                .add(ancillaryServiceRevenue)
                .subtract(gridImportCost)
                .subtract(deviationCost)
                .subtract(storageCycleCost)
                .subtract(demandResponseCompensation)
                .subtract(renewableCurtailmentCost);

        Map<String, BigDecimal> allocation = buildRevenueAllocation(meteringEntities, netRevenue);
        SettlementResultEntity settlementEntity = new SettlementResultEntity();
        settlementEntity.setSettlementId(settlementId);
        settlementEntity.setPlanId(request.planId());
        settlementEntity.setClearingId(request.clearingId());
        settlementEntity.setTotalEnergyRevenue(scale(totalEnergyRevenue));
        settlementEntity.setGridImportCost(scale(gridImportCost));
        settlementEntity.setGridExportRevenue(scale(gridExportRevenue));
        settlementEntity.setDeviationCost(scale(deviationCost));
        settlementEntity.setStorageCycleCost(scale(storageCycleCost));
        settlementEntity.setDemandResponseCompensation(scale(demandResponseCompensation));
        settlementEntity.setRenewableCurtailmentCost(scale(renewableCurtailmentCost));
        settlementEntity.setAncillaryServiceRevenue(scale(ancillaryServiceRevenue));
        settlementEntity.setResourceRevenueAllocationJson(writeRevenueAllocation(allocation));
        settlementEntity.setNetRevenue(scale(netRevenue));
        settlementEntity.setTestData(true);
        settlementEntity.setCreatedAt(now);
        settlementResultRepository.save(settlementEntity);
        return toSettlementResponse(settlementEntity, meteringEntities);
    }

    /**
     * 查询结算详情。
     *
     * @param settlementId 结算标识
     * @return 结算响应
     */
    @Transactional(readOnly = true)
    public SettlementResultResponse getSettlement(String settlementId) {
        SettlementResultEntity entity = settlementResultRepository.findBySettlementId(settlementId)
                .orElseThrow(() -> notFound("SETTLEMENT_NOT_FOUND", "结算结果不存在", settlementId));
        return toSettlementResponse(entity, marketMeteringRecordRepository.findBySettlementIdOrderByStationIdAscIntervalStartAsc(settlementId));
    }

    /**
     * 查询最新结算结果。
     *
     * @return 结算响应
     */
    @Transactional(readOnly = true)
    public SettlementResultResponse getLatestSettlement() {
        SettlementResultEntity entity = settlementResultRepository.findFirstByOrderByCreatedAtDesc()
                .orElseThrow(() -> notFound("SETTLEMENT_NOT_FOUND", "尚无结算结果", "latest"));
        return toSettlementResponse(
                entity,
                marketMeteringRecordRepository.findBySettlementIdOrderByStationIdAscIntervalStartAsc(entity.getSettlementId())
        );
    }

    private void validateBidRequest(MarketBidCreateRequest request) {
        if (!request.intervalEnd().isAfter(request.intervalStart())) {
            throw badRequest("MARKET_BID_INTERVAL_INVALID", "报价时间片结束时间必须晚于开始时间", Map.of());
        }
        if (!request.validUntil().isAfter(OffsetDateTime.now(clock))) {
            throw badRequest("MARKET_BID_VALID_UNTIL_INVALID", "报价有效时间必须晚于当前时间", Map.of());
        }
        double totalQuantity = request.segments().stream()
                .mapToDouble(segment -> segment.quantity() == null ? 0.0 : segment.quantity())
                .sum();
        if (totalQuantity - request.availableCapacity() > 0.0001) {
            throw badRequest(
                    "MARKET_BID_CAPACITY_EXCEEDED",
                    "分段申报总功率超过可申报容量",
                    Map.of("totalQuantity", round(totalQuantity), "availableCapacity", round(request.availableCapacity()))
            );
        }
    }

    private MarketBidEntity requireBid(String bidId) {
        return marketBidRepository.findByBidId(bidId)
                .orElseThrow(() -> notFound("MARKET_BID_NOT_FOUND", "市场报价不存在", bidId));
    }

    private DispatchPlanEntity requirePlan(String planId) {
        return dispatchPlanRepository.findByPlanId(planId)
                .orElseThrow(() -> notFound("DISPATCH_PLAN_NOT_FOUND", "调度计划不存在", planId));
    }

    private MarketClearingResultEntity requireClearing(String clearingId) {
        return marketClearingResultRepository.findByClearingId(clearingId)
                .orElseThrow(() -> notFound("MARKET_CLEARING_NOT_FOUND", "测试出清结果不存在", clearingId));
    }

    private MarketBidResponse toBidResponse(MarketBidEntity entity, List<MarketBidSegmentEntity> segmentEntities) {
        return new MarketBidResponse(
                entity.getBidId(),
                entity.getMarketType(),
                entity.getAggregateObject(),
                entity.getIntervalStart(),
                entity.getIntervalEnd(),
                entity.getAvailableCapacity(),
                entity.getTotalQuantity(),
                entity.getInputSnapshotId(),
                entity.getBidVersion(),
                entity.getStatus(),
                entity.getValidUntil(),
                entity.isTestData(),
                entity.getCreatedAt(),
                segmentEntities.stream()
                        .map(segment -> new MarketBidSegmentResponse(
                                segment.getSegmentOrder(),
                                segment.getQuantity(),
                                segment.getPrice()
                        ))
                        .toList()
        );
    }

    private MarketClearingResponse toClearingResponse(MarketClearingResultEntity entity) {
        return new MarketClearingResponse(
                entity.getClearingId(),
                entity.getBidId(),
                entity.getMarketType(),
                entity.getIntervalStart(),
                entity.getIntervalEnd(),
                entity.getClearedPower(),
                entity.getClearedPrice(),
                entity.getAncillaryServicePower(),
                entity.getExternalTradeId(),
                entity.isTestData(),
                entity.getCreatedAt()
        );
    }

    private SettlementResultResponse toSettlementResponse(
            SettlementResultEntity entity,
            List<MarketMeteringRecordEntity> meteringEntities
    ) {
        return new SettlementResultResponse(
                entity.getSettlementId(),
                entity.getPlanId(),
                entity.getClearingId(),
                entity.getTotalEnergyRevenue(),
                entity.getGridImportCost(),
                entity.getGridExportRevenue(),
                entity.getDeviationCost(),
                entity.getStorageCycleCost(),
                entity.getDemandResponseCompensation(),
                entity.getRenewableCurtailmentCost(),
                entity.getAncillaryServiceRevenue(),
                entity.getNetRevenue(),
                readRevenueAllocation(entity.getResourceRevenueAllocationJson()),
                entity.isTestData(),
                entity.getCreatedAt(),
                meteringEntities.stream()
                        .sorted(Comparator.comparing(MarketMeteringRecordEntity::getStationId)
                                .thenComparing(MarketMeteringRecordEntity::getIntervalStart))
                        .map(record -> new MarketMeteringRecordResponse(
                                record.getMeteringId(),
                                record.getStationId(),
                                record.getIntervalStart(),
                                record.getIntervalEnd(),
                                record.getAveragePower(),
                                record.getEnergyKwh(),
                                record.getSampleCount()
                        ))
                        .toList()
        );
    }

    private String resolveExternalTradeId(String externalTradeId) {
        if (externalTradeId != null && !externalTradeId.isBlank()) {
            return externalTradeId;
        }
        return "trade-" + UUID.randomUUID();
    }

    private BigDecimal weightedAveragePrice(List<MarketBidSegmentEntity> segments, double clearedPower) {
        BigDecimal remaining = BigDecimal.valueOf(clearedPower);
        BigDecimal weightedTotal = BigDecimal.ZERO;
        for (MarketBidSegmentEntity segment : segments) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            BigDecimal segmentQuantity = BigDecimal.valueOf(segment.getQuantity());
            BigDecimal acceptedQuantity = remaining.min(segmentQuantity);
            weightedTotal = weightedTotal.add(acceptedQuantity.multiply(segment.getPrice()));
            remaining = remaining.subtract(acceptedQuantity);
        }
        if (clearedPower <= 0.0) {
            return BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP);
        }
        return weightedTotal.divide(BigDecimal.valueOf(clearedPower), 6, RoundingMode.HALF_UP);
    }

    private StationMetering buildStationMetering(
            String settlementId,
            String planId,
            String stationId,
            OffsetDateTime intervalStart,
            OffsetDateTime intervalEnd,
            List<TelemetryHistoryEntity> samples,
            OffsetDateTime createdAt
    ) {
        double averagePower = round(samples.stream().mapToDouble(TelemetryHistoryEntity::getActivePower).average().orElse(0.0));
        BigDecimal energyKwh = scale(BigDecimal.valueOf(averagePower).multiply(durationHours(intervalStart, intervalEnd)));
        MarketMeteringRecordEntity entity = new MarketMeteringRecordEntity();
        entity.setMeteringId("metering-" + UUID.randomUUID());
        entity.setSettlementId(settlementId);
        entity.setPlanId(planId);
        entity.setStationId(stationId);
        entity.setIntervalStart(intervalStart);
        entity.setIntervalEnd(intervalEnd);
        entity.setAveragePower(averagePower);
        entity.setEnergyKwh(energyKwh);
        entity.setSampleCount(samples.size());
        entity.setCreatedAt(createdAt);
        return new StationMetering(entity, samples.size());
    }

    private Map<String, BigDecimal> buildRevenueAllocation(List<MarketMeteringRecordEntity> meteringEntities, BigDecimal netRevenue) {
        BigDecimal totalAbsoluteEnergy = meteringEntities.stream()
                .map(record -> record.getEnergyKwh().abs())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, BigDecimal> allocation = new LinkedHashMap<>();
        if (totalAbsoluteEnergy.compareTo(BigDecimal.ZERO) == 0) {
            return allocation;
        }
        for (MarketMeteringRecordEntity record : meteringEntities) {
            BigDecimal share = record.getEnergyKwh().abs().divide(totalAbsoluteEnergy, 6, RoundingMode.HALF_UP);
            allocation.put(record.getStationId(), scale(netRevenue.multiply(share)));
        }
        return allocation;
    }

    private String writeRevenueAllocation(Map<String, BigDecimal> allocation) {
        try {
            return objectMapper.writeValueAsString(allocation);
        } catch (JsonProcessingException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "SETTLEMENT_SERIALIZATION_FAILED", "结算收益分配序列化失败", Map.of());
        }
    }

    private Map<String, BigDecimal> readRevenueAllocation(String value) {
        try {
            return objectMapper.readValue(value, REVENUE_ALLOCATION_TYPE);
        } catch (JsonProcessingException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "SETTLEMENT_DESERIALIZATION_FAILED", "结算收益分配反序列化失败", Map.of());
        }
    }

    private BigDecimal durationHours(OffsetDateTime start, OffsetDateTime end) {
        long seconds = Duration.between(start, end).getSeconds();
        return BigDecimal.valueOf(seconds)
                .divide(BigDecimal.valueOf(3600L), 6, RoundingMode.HALF_UP);
    }

    private boolean overlap(OffsetDateTime startA, OffsetDateTime endA, OffsetDateTime startB, OffsetDateTime endB) {
        return startA.isBefore(endB) && endA.isAfter(startB);
    }

    private BigDecimal scale(BigDecimal value) {
        return value.setScale(6, RoundingMode.HALF_UP);
    }

    private double round(double value) {
        return BigDecimal.valueOf(value)
                .setScale(1, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private ApiException notFound(String code, String message, String identifier) {
        return new ApiException(HttpStatus.NOT_FOUND, code, message, Map.of("identifier", identifier));
    }

    private ApiException badRequest(String code, String message, Map<String, Object> data) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message, data);
    }

    private ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message, Map.of());
    }

    /**
     * 场站计量构建结果。
     *
     * @param entity 计量实体
     * @param sampleCount 样本数量
     */
    private record StationMetering(MarketMeteringRecordEntity entity, int sampleCount) {
    }
}

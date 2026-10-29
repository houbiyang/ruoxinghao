package com.weaksignal.vpp.backend.market;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weaksignal.vpp.backend.api.ApiException;
import com.weaksignal.vpp.backend.api.dto.MarketSignalCreateRequest;
import com.weaksignal.vpp.backend.api.dto.MarketSignalResponse;
import com.weaksignal.vpp.backend.domain.QualityCode;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.MarketSignalSnapshotEntity;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.MarketSignalSnapshotRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 市场信号快照服务。
 */
@Service
public class MarketSignalService {

    private final MarketSignalSnapshotRepository marketSignalSnapshotRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    /**
     * 注入市场信号服务依赖。
     *
     * @param marketSignalSnapshotRepository 市场信号仓储
     * @param objectMapper JSON 序列化器
     * @param clock 系统时钟
     */
    public MarketSignalService(
            MarketSignalSnapshotRepository marketSignalSnapshotRepository,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.marketSignalSnapshotRepository = marketSignalSnapshotRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * 创建市场信号快照。
     *
     * @param request 创建请求
     * @return 响应结果
     */
    @Transactional
    public MarketSignalResponse createSnapshot(MarketSignalCreateRequest request) {
        return toResponse(createEntity(request));
    }

    private MarketSignalSnapshotEntity createEntity(MarketSignalCreateRequest request) {
        validateRequest(request);

        MarketSignalSnapshotEntity entity = new MarketSignalSnapshotEntity();
        entity.setSnapshotId("market-" + UUID.randomUUID());
        entity.setMarketType(request.marketType());
        entity.setIntervalStart(request.intervalStart());
        entity.setIntervalEnd(request.intervalEnd());
        entity.setContractPower(request.contractPower());
        entity.setContractPrice(request.contractPrice());
        entity.setDayAheadPrice(request.dayAheadPrice());
        entity.setRealTimePrice(request.realTimePrice());
        entity.setGridImportLimit(request.gridImportLimit());
        entity.setGridExportLimit(request.gridExportLimit());
        entity.setReserveUpRequirement(request.reserveUpRequirement());
        entity.setReserveDownRequirement(request.reserveDownRequirement());
        entity.setSource(request.source());
        entity.setQualityCode(request.qualityCode().name());
        entity.setVersion(request.version());
        entity.setGeneratedAt(request.generatedAt());
        entity.setValidFrom(request.validFrom());
        entity.setValidUntil(request.validUntil());
        entity.setReceivedAt(request.receivedAt());
        entity.setPayloadJson(writePayload(request));
        entity.setCreatedAt(OffsetDateTime.now(clock));
        marketSignalSnapshotRepository.save(entity);
        return entity;
    }

    /**
     * 获取最新市场信号快照。
     *
     * @return 最新快照
     */
    @Transactional(readOnly = true)
    public MarketSignalResponse getLatestSnapshot() {
        MarketSignalSnapshotEntity entity = requireLatestEntity();
        return toResponse(entity);
    }

    /**
     * 获取最新有效市场信号领域快照。
     *
     * @return 领域快照
     */
    @Transactional(readOnly = true, noRollbackFor = ApiException.class)
    public MarketSignalSnapshot getLatestActiveSnapshot() {
        MarketSignalSnapshotEntity entity = requireLatestEntity();
        ensureActive(entity.getSnapshotId(), entity.getValidUntil());
        return toDomain(entity);
    }

    /**
     * 确保实验闭环拥有有效的市场信号快照。
     * 当最新市场信号已失效时，复用其价格与边界值创建一份新的实验快照，避免测试实验因历史信号过期而无法建立调度上下文。
     *
     * @return 可用于实验调度上下文的市场信号快照
     */
    @Transactional
    public MarketSignalSnapshot ensureActiveForExperiment() {
        MarketSignalSnapshotEntity latestEntity = requireLatestEntity();
        OffsetDateTime now = OffsetDateTime.now(clock);
        if (latestEntity.getValidUntil().isAfter(now)) {
            return toDomain(latestEntity);
        }
        Duration intervalDuration = Duration.between(latestEntity.getIntervalStart(), latestEntity.getIntervalEnd());
        if (intervalDuration.isZero() || intervalDuration.isNegative()) {
            intervalDuration = Duration.ofMinutes(30);
        }
        MarketSignalCreateRequest request = new MarketSignalCreateRequest(
                latestEntity.getMarketType(),
                now,
                now.plus(intervalDuration),
                latestEntity.getContractPower(),
                latestEntity.getContractPrice(),
                latestEntity.getDayAheadPrice(),
                latestEntity.getRealTimePrice(),
                latestEntity.getGridImportLimit(),
                latestEntity.getGridExportLimit(),
                latestEntity.getReserveUpRequirement(),
                latestEntity.getReserveDownRequirement(),
                now,
                now,
                now.plus(intervalDuration),
                "EXPERIMENT_SIGNAL_REFRESH",
                QualityCode.valueOf(latestEntity.getQualityCode()),
                "experiment-refresh-" + now.toEpochSecond(),
                now
        );
        return toDomain(createEntity(request));
    }

    /**
     * 按标识获取领域快照。
     *
     * @param snapshotId 快照标识
     * @return 领域快照
     */
    @Transactional(readOnly = true)
    public MarketSignalSnapshot getSnapshotById(String snapshotId) {
        MarketSignalSnapshotEntity entity = marketSignalSnapshotRepository.findBySnapshotId(snapshotId)
                .orElseThrow(() -> notFound("MARKET_SIGNAL_NOT_FOUND", "市场信号快照不存在", snapshotId));
        return toDomain(entity);
    }

    private MarketSignalSnapshotEntity requireLatestEntity() {
        return marketSignalSnapshotRepository.findFirstByOrderByGeneratedAtDesc()
                .orElseThrow(() -> notFound("MARKET_SIGNAL_NOT_FOUND", "尚无市场信号快照", "latest"));
    }

    private void validateRequest(MarketSignalCreateRequest request) {
        if (!request.intervalEnd().isAfter(request.intervalStart())) {
            throw badRequest("MARKET_SIGNAL_INTERVAL_INVALID", "市场时间片结束时间必须晚于开始时间");
        }
        if (!request.validUntil().isAfter(request.validFrom())) {
            throw badRequest("MARKET_SIGNAL_VALIDITY_INVALID", "市场信号有效期结束时间必须晚于开始时间");
        }
        ensureActive("request", request.validUntil());
    }

    private void ensureActive(String snapshotId, OffsetDateTime validUntil) {
        if (!validUntil.isAfter(OffsetDateTime.now(clock))) {
            throw badRequest("MARKET_SIGNAL_EXPIRED", "市场信号已过期，不能参与调度上下文", snapshotId);
        }
    }

    private String writePayload(MarketSignalCreateRequest request) {
        Map<String, Object> payload = Map.ofEntries(
                Map.entry("marketType", request.marketType()),
                Map.entry("intervalStart", request.intervalStart()),
                Map.entry("intervalEnd", request.intervalEnd()),
                Map.entry("contractPower", request.contractPower()),
                Map.entry("contractPrice", request.contractPrice()),
                Map.entry("dayAheadPrice", request.dayAheadPrice()),
                Map.entry("realTimePrice", request.realTimePrice()),
                Map.entry("gridImportLimit", request.gridImportLimit()),
                Map.entry("gridExportLimit", request.gridExportLimit()),
                Map.entry("reserveUpRequirement", request.reserveUpRequirement()),
                Map.entry("reserveDownRequirement", request.reserveDownRequirement())
        );
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "MARKET_SIGNAL_SERIALIZATION_FAILED",
                    "市场信号快照序列化失败",
                    Map.of()
            );
        }
    }

    private MarketSignalSnapshot toDomain(MarketSignalSnapshotEntity entity) {
        return new MarketSignalSnapshot(
                entity.getSnapshotId(),
                entity.getMarketType(),
                entity.getIntervalStart(),
                entity.getIntervalEnd(),
                entity.getContractPower(),
                entity.getContractPrice(),
                entity.getDayAheadPrice(),
                entity.getRealTimePrice(),
                entity.getGridImportLimit(),
                entity.getGridExportLimit(),
                entity.getReserveUpRequirement(),
                entity.getReserveDownRequirement(),
                entity.getGeneratedAt(),
                entity.getValidFrom(),
                entity.getValidUntil(),
                entity.getSource(),
                QualityCode.valueOf(entity.getQualityCode()),
                entity.getVersion(),
                entity.getReceivedAt()
        );
    }

    private MarketSignalResponse toResponse(MarketSignalSnapshotEntity entity) {
        return new MarketSignalResponse(
                entity.getSnapshotId(),
                entity.getMarketType(),
                entity.getIntervalStart(),
                entity.getIntervalEnd(),
                entity.getContractPower(),
                entity.getContractPrice(),
                entity.getDayAheadPrice(),
                entity.getRealTimePrice(),
                entity.getGridImportLimit(),
                entity.getGridExportLimit(),
                entity.getReserveUpRequirement(),
                entity.getReserveDownRequirement(),
                entity.getGeneratedAt(),
                entity.getValidFrom(),
                entity.getValidUntil(),
                entity.getSource(),
                QualityCode.valueOf(entity.getQualityCode()),
                entity.getVersion(),
                entity.getReceivedAt()
        );
    }

    private ApiException notFound(String code, String message, String identifier) {
        return new ApiException(HttpStatus.NOT_FOUND, code, message, Map.of("identifier", identifier));
    }

    private ApiException badRequest(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message, Map.of());
    }

    private ApiException badRequest(String code, String message, String identifier) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message, Map.of("identifier", identifier));
    }
}

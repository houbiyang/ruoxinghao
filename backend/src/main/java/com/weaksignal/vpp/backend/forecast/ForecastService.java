package com.weaksignal.vpp.backend.forecast;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weaksignal.vpp.backend.api.ApiException;
import com.weaksignal.vpp.backend.api.dto.ForecastPointRequest;
import com.weaksignal.vpp.backend.api.dto.ForecastPointResponse;
import com.weaksignal.vpp.backend.api.dto.ForecastSnapshotCreateRequest;
import com.weaksignal.vpp.backend.api.dto.ForecastSnapshotResponse;
import com.weaksignal.vpp.backend.domain.QualityCode;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.ForecastSnapshotEntity;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.ForecastSnapshotRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 预测快照服务。
 */
@Service
public class ForecastService {

    private static final TypeReference<ForecastPayload> FORECAST_PAYLOAD_TYPE = new TypeReference<>() {
    };

    private final ForecastSnapshotRepository forecastSnapshotRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    /**
     * 注入预测服务依赖。
     *
     * @param forecastSnapshotRepository 预测仓储
     * @param objectMapper JSON 处理器
     * @param clock 系统时钟
     */
    public ForecastService(
            ForecastSnapshotRepository forecastSnapshotRepository,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.forecastSnapshotRepository = forecastSnapshotRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * 创建预测快照。
     *
     * @param request 创建请求
     * @return 响应结果
     */
    @Transactional
    public ForecastSnapshotResponse createSnapshot(ForecastSnapshotCreateRequest request) {
        validateRequest(request);

        ForecastSnapshotEntity entity = new ForecastSnapshotEntity();
        entity.setSnapshotId("forecast-" + UUID.randomUUID());
        entity.setIntervalMinutes(request.intervalMinutes());
        entity.setModelVersion(request.modelVersion());
        entity.setSource(request.source());
        entity.setQualityCode(request.qualityCode().name());
        entity.setVersion(request.version());
        entity.setGeneratedAt(request.generatedAt());
        entity.setValidFrom(request.validFrom());
        entity.setValidUntil(request.validUntil());
        entity.setReceivedAt(request.receivedAt());
        entity.setPayloadJson(writePayload(request.points()));
        entity.setCreatedAt(OffsetDateTime.now(clock));
        forecastSnapshotRepository.save(entity);
        return toResponse(entity);
    }

    /**
     * 获取最新预测快照。
     *
     * @return 最新快照
     */
    @Transactional(readOnly = true)
    public ForecastSnapshotResponse getLatestSnapshot() {
        return toResponse(requireLatestEntity());
    }

    /**
     * 获取最新有效预测领域快照。
     *
     * @return 领域快照
     */
    @Transactional(readOnly = true, noRollbackFor = ApiException.class)
    public ForecastSnapshot getLatestActiveSnapshot() {
        ForecastSnapshotEntity entity = requireLatestEntity();
        ensureActive(entity.getSnapshotId(), entity.getValidUntil());
        return toDomain(entity);
    }

    /**
     * 按标识获取预测领域快照。
     *
     * @param snapshotId 快照标识
     * @return 领域快照
     */
    @Transactional(readOnly = true)
    public ForecastSnapshot getSnapshotById(String snapshotId) {
        ForecastSnapshotEntity entity = forecastSnapshotRepository.findBySnapshotId(snapshotId)
                .orElseThrow(() -> notFound("FORECAST_SNAPSHOT_NOT_FOUND", "预测快照不存在", snapshotId));
        return toDomain(entity);
    }

    private ForecastSnapshotEntity requireLatestEntity() {
        return forecastSnapshotRepository.findFirstByOrderByGeneratedAtDesc()
                .orElseThrow(() -> notFound("FORECAST_SNAPSHOT_NOT_FOUND", "尚无预测快照", "latest"));
    }

    private void validateRequest(ForecastSnapshotCreateRequest request) {
        if (!request.validUntil().isAfter(request.validFrom())) {
            throw badRequest("FORECAST_VALIDITY_INVALID", "预测有效期结束时间必须晚于开始时间");
        }
        for (ForecastPointRequest point : request.points()) {
            if (!point.intervalEnd().isAfter(point.intervalStart())) {
                throw badRequest("FORECAST_INTERVAL_INVALID", "预测时间片结束时间必须晚于开始时间");
            }
        }
        ensureActive("request", request.validUntil());
    }

    private void ensureActive(String snapshotId, OffsetDateTime validUntil) {
        if (!validUntil.isAfter(OffsetDateTime.now(clock))) {
            throw badRequest("FORECAST_SNAPSHOT_EXPIRED", "预测快照已过期，不能参与调度上下文", snapshotId);
        }
    }

    private String writePayload(List<ForecastPointRequest> points) {
        try {
            return objectMapper.writeValueAsString(Map.of("points", points));
        } catch (JsonProcessingException exception) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "FORECAST_SNAPSHOT_SERIALIZATION_FAILED",
                    "预测快照序列化失败",
                    Map.of()
            );
        }
    }

    private ForecastSnapshot toDomain(ForecastSnapshotEntity entity) {
        return new ForecastSnapshot(
                entity.getSnapshotId(),
                entity.getIntervalMinutes(),
                entity.getModelVersion(),
                readPoints(entity.getPayloadJson()),
                entity.getGeneratedAt(),
                entity.getValidFrom(),
                entity.getValidUntil(),
                entity.getSource(),
                QualityCode.valueOf(entity.getQualityCode()),
                entity.getVersion(),
                entity.getReceivedAt()
        );
    }

    private ForecastSnapshotResponse toResponse(ForecastSnapshotEntity entity) {
        List<ForecastPoint> points = readPoints(entity.getPayloadJson());
        return new ForecastSnapshotResponse(
                entity.getSnapshotId(),
                entity.getIntervalMinutes(),
                entity.getModelVersion(),
                points.stream()
                        .map(point -> new ForecastPointResponse(
                                point.intervalStart(),
                                point.intervalEnd(),
                                point.loadP10(),
                                point.loadP50(),
                                point.loadP90(),
                                point.photovoltaicP10(),
                                point.photovoltaicP50(),
                                point.photovoltaicP90(),
                                point.windP10(),
                                point.windP50(),
                                point.windP90(),
                                point.priceP10(),
                                point.priceP50(),
                                point.priceP90()
                        ))
                        .toList(),
                entity.getGeneratedAt(),
                entity.getValidFrom(),
                entity.getValidUntil(),
                entity.getSource(),
                QualityCode.valueOf(entity.getQualityCode()),
                entity.getVersion(),
                entity.getReceivedAt()
        );
    }

    private List<ForecastPoint> readPoints(String payloadJson) {
        try {
            ForecastPayload payload = objectMapper.readValue(payloadJson, FORECAST_PAYLOAD_TYPE);
            return payload.points().stream()
                    .map(point -> new ForecastPoint(
                            point.intervalStart(),
                            point.intervalEnd(),
                            point.loadP10(),
                            point.loadP50(),
                            point.loadP90(),
                            point.photovoltaicP10(),
                            point.photovoltaicP50(),
                            point.photovoltaicP90(),
                            point.windP10(),
                            point.windP50(),
                            point.windP90(),
                            point.priceP10(),
                            point.priceP50(),
                            point.priceP90()
                    ))
                    .toList();
        } catch (JsonProcessingException exception) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "FORECAST_SNAPSHOT_PAYLOAD_INVALID",
                    "预测快照载荷解析失败",
                    Map.of()
            );
        }
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

    /**
     * 预测载荷包装对象。
     *
     * @param points 预测点集合
     */
    private record ForecastPayload(List<ForecastPointPayload> points) {
    }

    /**
     * 预测点载荷对象。
     *
     * @param intervalStart 时间片起始
     * @param intervalEnd 时间片结束
     * @param loadP10 负荷 P10
     * @param loadP50 负荷 P50
     * @param loadP90 负荷 P90
     * @param photovoltaicP10 光伏 P10
     * @param photovoltaicP50 光伏 P50
     * @param photovoltaicP90 光伏 P90
     * @param windP10 风电 P10
     * @param windP50 风电 P50
     * @param windP90 风电 P90
     * @param priceP10 价格 P10
     * @param priceP50 价格 P50
     * @param priceP90 价格 P90
     */
    private record ForecastPointPayload(
            OffsetDateTime intervalStart,
            OffsetDateTime intervalEnd,
            double loadP10,
            double loadP50,
            double loadP90,
            double photovoltaicP10,
            double photovoltaicP50,
            double photovoltaicP90,
            double windP10,
            double windP50,
            double windP90,
            java.math.BigDecimal priceP10,
            java.math.BigDecimal priceP50,
            java.math.BigDecimal priceP90
    ) {
    }
}

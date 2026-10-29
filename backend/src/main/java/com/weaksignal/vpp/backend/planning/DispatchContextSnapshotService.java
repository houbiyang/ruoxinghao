package com.weaksignal.vpp.backend.planning;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weaksignal.vpp.backend.api.ApiException;
import com.weaksignal.vpp.backend.api.dto.DispatchContextSnapshotResponse;
import com.weaksignal.vpp.backend.api.dto.EnvironmentAssessmentResponse;
import com.weaksignal.vpp.backend.api.dto.ForecastPointResponse;
import com.weaksignal.vpp.backend.api.dto.ForecastSnapshotResponse;
import com.weaksignal.vpp.backend.api.dto.MarketSignalResponse;
import com.weaksignal.vpp.backend.api.dto.ResourceCapabilityResponse;
import com.weaksignal.vpp.backend.api.dto.StorageConstraintResponse;
import com.weaksignal.vpp.backend.capability.ResourceCapabilityService;
import com.weaksignal.vpp.backend.capability.ResourceCapabilitySnapshot;
import com.weaksignal.vpp.backend.capability.StorageConstraint;
import com.weaksignal.vpp.backend.config.PlanningProperties;
import com.weaksignal.vpp.backend.domain.QualityCode;
import com.weaksignal.vpp.backend.forecast.ForecastPoint;
import com.weaksignal.vpp.backend.forecast.ForecastService;
import com.weaksignal.vpp.backend.forecast.ForecastSnapshot;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.DispatchContextSnapshotEntity;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.DispatchContextSnapshotRepository;
import com.weaksignal.vpp.backend.market.MarketSignalService;
import com.weaksignal.vpp.backend.market.MarketSignalSnapshot;
import com.weaksignal.vpp.backend.market.SimulatorMarketDataService;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 调度上下文快照服务。
 */
@Service
public class DispatchContextSnapshotService {

    private final DispatchContextSnapshotRepository dispatchContextSnapshotRepository;
    private final MarketSignalService marketSignalService;
    private final ForecastService forecastService;
    private final SimulatorMarketDataService simulatorMarketDataService;
    private final ResourceCapabilityService resourceCapabilityService;
    private final PlanningProperties planningProperties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    /**
     * 注入调度上下文快照服务依赖。
     *
     * @param dispatchContextSnapshotRepository 调度上下文仓储
     * @param marketSignalService 市场信号服务
     * @param forecastService 预测服务
     * @param simulatorMarketDataService 模拟器市场数据刷新服务
     * @param resourceCapabilityService 资源能力服务
     * @param planningProperties 计划参数
     * @param objectMapper JSON 处理器
     * @param clock 系统时钟
     */
    public DispatchContextSnapshotService(
            DispatchContextSnapshotRepository dispatchContextSnapshotRepository,
            MarketSignalService marketSignalService,
            ForecastService forecastService,
            SimulatorMarketDataService simulatorMarketDataService,
            ResourceCapabilityService resourceCapabilityService,
            PlanningProperties planningProperties,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.dispatchContextSnapshotRepository = dispatchContextSnapshotRepository;
        this.marketSignalService = marketSignalService;
        this.forecastService = forecastService;
        this.simulatorMarketDataService = simulatorMarketDataService;
        this.resourceCapabilityService = resourceCapabilityService;
        this.planningProperties = planningProperties;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * 创建新的调度上下文快照。
     *
     * @return 调度上下文响应
     */
    @Transactional(noRollbackFor = ApiException.class)
    public DispatchContextSnapshotResponse createSnapshot() {
        MarketSignalSnapshot marketSignalSnapshot = marketSignalService.getLatestActiveSnapshot();
        ForecastSnapshot forecastSnapshot = forecastService.getLatestActiveSnapshot();
        List<ResourceCapabilitySnapshot> capabilitySnapshots = resourceCapabilityService.createCapabilitySnapshots();

        OffsetDateTime generatedAt = OffsetDateTime.now(clock);
        OffsetDateTime validFrom = max(marketSignalSnapshot.validFrom(), forecastSnapshot.validFrom(), generatedAt);
        OffsetDateTime validUntil = min(
                marketSignalSnapshot.validUntil(),
                forecastSnapshot.validUntil(),
                capabilitySnapshots.stream().map(ResourceCapabilitySnapshot::validUntil).min(OffsetDateTime::compareTo)
                        .orElseThrow(() -> badRequest("RESOURCE_CAPABILITY_EMPTY", "资源能力快照不能为空"))
        );
        if (!validUntil.isAfter(validFrom)) {
            throw badRequest("DISPATCH_CONTEXT_EXPIRED", "调度上下文快照有效期不足，无法生成");
        }

        DispatchContextSnapshot snapshot = new DispatchContextSnapshot(
                "dispatch-context-" + UUID.randomUUID(),
                generatedAt,
                validFrom,
                validUntil,
                "DISPATCH_CONTEXT_BUILDER",
                QualityCode.GOOD,
                "dispatch-context-v1",
                generatedAt,
                planningProperties.intervalMinutes(),
                marketSignalSnapshot,
                forecastSnapshot,
                capabilitySnapshots
        );

        DispatchContextSnapshotEntity entity = new DispatchContextSnapshotEntity();
        entity.setSnapshotId(snapshot.snapshotId());
        entity.setMarketSignalSnapshotId(marketSignalSnapshot.snapshotId());
        entity.setForecastSnapshotId(forecastSnapshot.snapshotId());
        entity.setCapabilitySnapshotGroupId(capabilitySnapshots.get(0).snapshotGroupId());
        entity.setIntervalMinutes(snapshot.intervalMinutes());
        entity.setSource(snapshot.source());
        entity.setQualityCode(snapshot.qualityCode().name());
        entity.setVersion(snapshot.version());
        entity.setGeneratedAt(snapshot.generatedAt());
        entity.setValidFrom(snapshot.validFrom());
        entity.setValidUntil(snapshot.validUntil());
        entity.setReceivedAt(snapshot.receivedAt());
        entity.setPayloadJson(writePayload(snapshot));
        entity.setCreatedAt(generatedAt);
        dispatchContextSnapshotRepository.saveAndFlush(entity);
        return toResponse(snapshot);
    }

    /**
     * 创建实验闭环专用调度上下文快照。
     * 实验前会根据最新模拟器遥测刷新市场信号与短期预测，普通调度入口仍保持原有的严格有效期校验。
     *
     * @return 调度上下文响应
     */
    @Transactional(noRollbackFor = ApiException.class)
    public DispatchContextSnapshotResponse createExperimentSnapshot() {
        simulatorMarketDataService.refreshForExperiment();
        return createSnapshot();
    }

    /**
     * 查询调度上下文快照。
     *
     * @param snapshotId 快照标识
     * @return 调度上下文响应
     */
    @Transactional(readOnly = true)
    public DispatchContextSnapshotResponse getSnapshot(String snapshotId) {
        DispatchContextSnapshotEntity entity = dispatchContextSnapshotRepository.findBySnapshotId(snapshotId)
                .orElseThrow(() -> notFound("DISPATCH_CONTEXT_NOT_FOUND", "调度上下文快照不存在", snapshotId));
        MarketSignalSnapshot marketSignalSnapshot = marketSignalService.getSnapshotById(entity.getMarketSignalSnapshotId());
        ForecastSnapshot forecastSnapshot = forecastService.getSnapshotById(entity.getForecastSnapshotId());
        List<ResourceCapabilitySnapshot> capabilitySnapshots =
                resourceCapabilityService.getSnapshotsByGroupId(entity.getCapabilitySnapshotGroupId());
        DispatchContextSnapshot snapshot = new DispatchContextSnapshot(
                entity.getSnapshotId(),
                entity.getGeneratedAt(),
                entity.getValidFrom(),
                entity.getValidUntil(),
                entity.getSource(),
                QualityCode.valueOf(entity.getQualityCode()),
                entity.getVersion(),
                entity.getReceivedAt(),
                entity.getIntervalMinutes(),
                marketSignalSnapshot,
                forecastSnapshot,
                capabilitySnapshots
        );
        return toResponse(snapshot);
    }

    private String writePayload(DispatchContextSnapshot snapshot) {
        Map<String, Object> payload = Map.of(
                "snapshotId", snapshot.snapshotId(),
                "marketSignalSnapshotId", snapshot.marketSignalSnapshot().snapshotId(),
                "forecastSnapshotId", snapshot.forecastSnapshot().snapshotId(),
                "capabilitySnapshotGroupId", snapshot.resourceCapabilitySnapshots().get(0).snapshotGroupId(),
                "intervalMinutes", snapshot.intervalMinutes()
        );
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "DISPATCH_CONTEXT_SERIALIZATION_FAILED",
                    "调度上下文快照序列化失败",
                    Map.of()
            );
        }
    }

    private DispatchContextSnapshotResponse toResponse(DispatchContextSnapshot snapshot) {
        return new DispatchContextSnapshotResponse(
                snapshot.snapshotId(),
                snapshot.generatedAt(),
                snapshot.validFrom(),
                snapshot.validUntil(),
                snapshot.source(),
                snapshot.qualityCode(),
                snapshot.version(),
                snapshot.receivedAt(),
                snapshot.intervalMinutes(),
                new MarketSignalResponse(
                        snapshot.marketSignalSnapshot().snapshotId(),
                        snapshot.marketSignalSnapshot().marketType(),
                        snapshot.marketSignalSnapshot().intervalStart(),
                        snapshot.marketSignalSnapshot().intervalEnd(),
                        snapshot.marketSignalSnapshot().contractPower(),
                        snapshot.marketSignalSnapshot().contractPrice(),
                        snapshot.marketSignalSnapshot().dayAheadPrice(),
                        snapshot.marketSignalSnapshot().realTimePrice(),
                        snapshot.marketSignalSnapshot().gridImportLimit(),
                        snapshot.marketSignalSnapshot().gridExportLimit(),
                        snapshot.marketSignalSnapshot().reserveUpRequirement(),
                        snapshot.marketSignalSnapshot().reserveDownRequirement(),
                        snapshot.marketSignalSnapshot().generatedAt(),
                        snapshot.marketSignalSnapshot().validFrom(),
                        snapshot.marketSignalSnapshot().validUntil(),
                        snapshot.marketSignalSnapshot().source(),
                        snapshot.marketSignalSnapshot().qualityCode(),
                        snapshot.marketSignalSnapshot().version(),
                        snapshot.marketSignalSnapshot().receivedAt()
                ),
                new ForecastSnapshotResponse(
                        snapshot.forecastSnapshot().snapshotId(),
                        snapshot.forecastSnapshot().intervalMinutes(),
                        snapshot.forecastSnapshot().modelVersion(),
                        snapshot.forecastSnapshot().points().stream()
                                .map(this::toForecastPointResponse)
                                .toList(),
                        snapshot.forecastSnapshot().generatedAt(),
                        snapshot.forecastSnapshot().validFrom(),
                        snapshot.forecastSnapshot().validUntil(),
                        snapshot.forecastSnapshot().source(),
                        snapshot.forecastSnapshot().qualityCode(),
                        snapshot.forecastSnapshot().version(),
                        snapshot.forecastSnapshot().receivedAt()
                ),
                snapshot.resourceCapabilitySnapshots().stream().map(this::toCapabilityResponse).toList()
        );
    }

    private ForecastPointResponse toForecastPointResponse(ForecastPoint point) {
        return new ForecastPointResponse(
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
        );
    }

    private ResourceCapabilityResponse toCapabilityResponse(ResourceCapabilitySnapshot snapshot) {
        StorageConstraint storageConstraint = snapshot.storageConstraint();
        StorageConstraintResponse storageConstraintResponse = storageConstraint == null ? null : new StorageConstraintResponse(
                storageConstraint.soc(),
                storageConstraint.minimumSoc(),
                storageConstraint.maximumSoc(),
                storageConstraint.energyCapacityKWh(),
                storageConstraint.maximumChargePowerKW(),
                storageConstraint.maximumDischargePowerKW(),
                storageConstraint.chargeEfficiency(),
                storageConstraint.dischargeEfficiency(),
                storageConstraint.cycleCost(),
                storageConstraint.stateOfHealth(),
                storageConstraint.reservedUpPower(),
                storageConstraint.reservedDownPower()
        );
        return new ResourceCapabilityResponse(
                snapshot.capabilityId(),
                snapshot.snapshotGroupId(),
                snapshot.stationId(),
                snapshot.resourceType(),
                snapshot.currentPower(),
                snapshot.availableCapacity(),
                snapshot.minimumPower(),
                snapshot.maximumPower(),
                snapshot.maximumRampUp(),
                snapshot.maximumRampDown(),
                snapshot.availableEnergyKwh(),
                storageConstraintResponse,
                toEnvironmentResponse(snapshot),
                snapshot.linkState(),
                snapshot.dataAge(),
                snapshot.capabilityConfidence(),
                snapshot.dataTrusted(),
                snapshot.unavailableReason(),
                snapshot.generatedAt(),
                snapshot.validFrom(),
                snapshot.validUntil(),
                snapshot.source(),
                snapshot.qualityCode(),
                snapshot.version(),
                snapshot.receivedAt()
        );
    }

    /**
     * 转换资源能力快照中的环境感知响应。
     *
     * @param snapshot 资源能力快照
     * @return 环境感知响应
     */
    private EnvironmentAssessmentResponse toEnvironmentResponse(ResourceCapabilitySnapshot snapshot) {
        if (snapshot.environmentAssessment() == null) {
            return null;
        }
        return new EnvironmentAssessmentResponse(
                snapshot.environmentAssessment().stationId(),
                snapshot.environmentAssessment().resourceType(),
                snapshot.environmentAssessment().environmentFactor(),
                snapshot.environmentAssessment().riskLevel(),
                snapshot.environmentAssessment().correctionReasons(),
                snapshot.environmentAssessment().affectedDeviceMetrics(),
                snapshot.environmentAssessment().deviceDataInterpretation(),
                snapshot.environmentAssessment().safetyConstraints(),
                snapshot.environmentAssessment().dataAvailable(),
                snapshot.environmentAssessment().missingFields()
        );
    }

    private OffsetDateTime min(OffsetDateTime first, OffsetDateTime second, OffsetDateTime third) {
        return List.of(first, second, third).stream().min(OffsetDateTime::compareTo).orElse(first);
    }

    private OffsetDateTime max(OffsetDateTime first, OffsetDateTime second, OffsetDateTime third) {
        return List.of(first, second, third).stream().max(OffsetDateTime::compareTo).orElse(first);
    }

    private ApiException notFound(String code, String message, String identifier) {
        return new ApiException(HttpStatus.NOT_FOUND, code, message, Map.of("identifier", identifier));
    }

    private ApiException badRequest(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message, Map.of());
    }
}

package com.weaksignal.vpp.backend.capability;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weaksignal.vpp.backend.api.ApiException;
import com.weaksignal.vpp.backend.api.dto.EnvironmentAssessmentResponse;
import com.weaksignal.vpp.backend.api.dto.ResourceCapabilityResponse;
import com.weaksignal.vpp.backend.api.dto.StorageConstraintResponse;
import com.weaksignal.vpp.backend.config.PlanningProperties;
import com.weaksignal.vpp.backend.domain.LinkState;
import com.weaksignal.vpp.backend.domain.QualityCode;
import com.weaksignal.vpp.backend.domain.ResourceType;
import com.weaksignal.vpp.backend.environment.EnvironmentAssessmentResult;
import com.weaksignal.vpp.backend.environment.EnvironmentAssessmentService;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.ResourceCapabilitySnapshotEntity;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.StationEntity;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.TelemetrySnapshotEntity;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.ResourceCapabilitySnapshotRepository;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.StationRepository;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.TelemetrySnapshotRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 资源能力快照服务。
 */
@Service
public class ResourceCapabilityService {

    private static final double STORAGE_MIN_SOC = 10.0;
    private static final double STORAGE_MAX_SOC = 90.0;
    private static final double STORAGE_ENERGY_CAPACITY_KWH = 240.0;
    private static final double STORAGE_MAX_CHARGE_POWER_KW = 120.0;
    private static final double STORAGE_MAX_DISCHARGE_POWER_KW = 120.0;
    private static final double STORAGE_CHARGE_EFFICIENCY = 0.95;
    private static final double STORAGE_DISCHARGE_EFFICIENCY = 0.95;
    private static final BigDecimal STORAGE_CYCLE_COST = new BigDecimal("0.080000");
    private static final double STORAGE_STATE_OF_HEALTH = 0.96;
    private static final double DEFAULT_RESERVED_UP_POWER = 12.0;
    private static final double DEFAULT_RESERVED_DOWN_POWER = 12.0;

    private final StationRepository stationRepository;
    private final TelemetrySnapshotRepository telemetrySnapshotRepository;
    private final ResourceCapabilitySnapshotRepository resourceCapabilitySnapshotRepository;
    private final ObjectMapper objectMapper;
    private final EnvironmentAssessmentService environmentAssessmentService;
    private final PlanningProperties planningProperties;
    private final Clock clock;

    /**
     * 注入资源能力服务依赖。
     *
     * @param stationRepository 场站仓储
     * @param telemetrySnapshotRepository 遥测快照仓储
     * @param resourceCapabilitySnapshotRepository 能力快照仓储
     * @param objectMapper JSON 处理器
     * @param environmentAssessmentService 环境感知智能体服务
     * @param planningProperties 计划参数
     * @param clock 系统时钟
     */
    public ResourceCapabilityService(
            StationRepository stationRepository,
            TelemetrySnapshotRepository telemetrySnapshotRepository,
            ResourceCapabilitySnapshotRepository resourceCapabilitySnapshotRepository,
            ObjectMapper objectMapper,
            EnvironmentAssessmentService environmentAssessmentService,
            PlanningProperties planningProperties,
            Clock clock
    ) {
        this.stationRepository = stationRepository;
        this.telemetrySnapshotRepository = telemetrySnapshotRepository;
        this.resourceCapabilitySnapshotRepository = resourceCapabilitySnapshotRepository;
        this.objectMapper = objectMapper;
        this.environmentAssessmentService = environmentAssessmentService;
        this.planningProperties = planningProperties;
        this.clock = clock;
    }

    /**
     * 获取最新资源能力响应，并创建一组新的能力快照。
     *
     * @return 能力响应集合
     */
    @Transactional
    public List<ResourceCapabilityResponse> getLatestCapabilities() {
        List<ResourceCapabilitySnapshot> snapshots = createCapabilitySnapshots();
        return snapshots.stream().map(this::toResponse).toList();
    }

    /**
     * 创建资源能力领域快照。
     *
     * @return 领域快照集合
     */
    @Transactional
    public List<ResourceCapabilitySnapshot> createCapabilitySnapshots() {
        OffsetDateTime now = OffsetDateTime.now(clock);
        String snapshotGroupId = "capability-group-" + UUID.randomUUID();
        List<ResourceCapabilitySnapshot> snapshots = new ArrayList<>();
        List<StationEntity> enabledStations = stationRepository.findAll().stream()
                .filter(StationEntity::isEnabled)
                .sorted(Comparator.comparing(StationEntity::getStationId))
                .toList();

        for (StationEntity station : enabledStations) {
            TelemetrySnapshotEntity telemetry = telemetrySnapshotRepository.findByStationId(station.getStationId())
                    .orElseThrow(() -> notFound("TELEMETRY_NOT_FOUND", "场站尚无实时遥测", station.getStationId()));
            ResourceCapabilitySnapshot snapshot = buildSnapshot(snapshotGroupId, station, telemetry, now);
            persistSnapshot(snapshot);
            snapshots.add(snapshot);
        }
        return snapshots;
    }

    /**
     * 按快照分组查询资源能力。
     *
     * @param snapshotGroupId 快照分组标识
     * @return 资源能力快照集合
     */
    @Transactional(readOnly = true)
    public List<ResourceCapabilitySnapshot> getSnapshotsByGroupId(String snapshotGroupId) {
        List<ResourceCapabilitySnapshotEntity> entities =
                resourceCapabilitySnapshotRepository.findBySnapshotGroupIdOrderByStationIdAsc(snapshotGroupId);
        if (entities.isEmpty()) {
            throw notFound("RESOURCE_CAPABILITY_GROUP_NOT_FOUND", "资源能力快照分组不存在", snapshotGroupId);
        }
        return entities.stream().map(this::toDomain).toList();
    }

    private ResourceCapabilitySnapshot buildSnapshot(
            String snapshotGroupId,
            StationEntity station,
            TelemetrySnapshotEntity telemetry,
            OffsetDateTime now
    ) {
        double intervalHours = planningProperties.intervalMinutes() / 60.0;
        double linkFactor = resolveLinkFactor(telemetry.getLinkState());
        boolean dataFresh = telemetry.getDataAge() <= planningProperties.maximumDataAgeSeconds();
        boolean qualityTrusted = QualityCode.GOOD.name().equals(telemetry.getQualityCode())
                || QualityCode.DEGRADED.name().equals(telemetry.getQualityCode());
        boolean dataTrusted = telemetry.isOnline() && dataFresh && qualityTrusted && telemetry.getLinkState() != LinkState.S3;
        String unavailableReason = resolveUnavailableReason(telemetry, dataFresh, qualityTrusted);

        EnvironmentAssessmentResult environmentAssessment = environmentAssessmentService.assess(
                station.getStationId(),
                station.getResourceType(),
                telemetry.getActivePower(),
                telemetry.getAvailableCapacity(),
                telemetry.getLinkState(),
                telemetry.getPayloadJson()
        );
        // 模拟器上送的可用容量已经包含环境和设备故障造成的物理降额，后端仅叠加链路可信度约束。
        double constrainedCapacity = telemetry.getAvailableCapacity() * linkFactor;
        double capabilityConfidence = resolveCapabilityConfidence(telemetry.getLinkState(), dataTrusted, qualityTrusted);
        double maximumRamp = resolveRampRate(station.getResourceType(), telemetry.getAvailableCapacity());
        OffsetDateTime validUntil = dataTrusted
                ? telemetry.getReceivedTimestamp().plusSeconds(planningProperties.maximumDataAgeSeconds())
                : now.plusSeconds(planningProperties.maximumDataAgeSeconds());

        StorageConstraint storageConstraint = null;
        double minimumPower = 0.0;
        double maximumPower = dataTrusted ? constrainedCapacity : 0.0;
        double availableEnergyKwh = dataTrusted ? constrainedCapacity * intervalHours : 0.0;
        double reservedUpPower = 0.0;
        double reservedDownPower = 0.0;

        if (station.getResourceType() == ResourceType.STORAGE) {
            storageConstraint = buildStorageConstraint(telemetry, constrainedCapacity, intervalHours);
            minimumPower = dataTrusted ? -storageConstraint.maximumChargePowerKW() : 0.0;
            maximumPower = dataTrusted ? storageConstraint.maximumDischargePowerKW() : 0.0;
            availableEnergyKwh = dataTrusted
                    ? Math.max(0.0, (storageConstraint.soc() - storageConstraint.minimumSoc()) / 100.0
                    * storageConstraint.energyCapacityKWh())
                    : 0.0;
            reservedUpPower = DEFAULT_RESERVED_UP_POWER;
            reservedDownPower = DEFAULT_RESERVED_DOWN_POWER;
        }

        if (!dataTrusted) {
            minimumPower = 0.0;
            maximumPower = 0.0;
            constrainedCapacity = 0.0;
            availableEnergyKwh = 0.0;
            capabilityConfidence = 0.0;
        }

        return new ResourceCapabilitySnapshot(
                "capability-" + UUID.randomUUID(),
                snapshotGroupId,
                station.getStationId(),
                station.getResourceType(),
                telemetry.getActivePower(),
                constrainedCapacity,
                minimumPower,
                maximumPower,
                maximumRamp,
                maximumRamp,
                availableEnergyKwh,
                storageConstraint,
                environmentAssessment,
                telemetry.getLinkState(),
                telemetry.getDataAge(),
                capabilityConfidence,
                dataTrusted,
                unavailableReason,
                now,
                now,
                validUntil,
                "REALTIME_TELEMETRY",
                QualityCode.valueOf(telemetry.getQualityCode()),
                "capability-v1",
                telemetry.getReceivedTimestamp()
        );
    }

    private StorageConstraint buildStorageConstraint(
            TelemetrySnapshotEntity telemetry,
            double constrainedCapacity,
            double intervalHours
    ) {
        double soc = readSoc(telemetry.getPayloadJson());
        double availableDischargeEnergy = Math.max(0.0, (soc - STORAGE_MIN_SOC) / 100.0 * STORAGE_ENERGY_CAPACITY_KWH);
        double availableChargeEnergy = Math.max(0.0, (STORAGE_MAX_SOC - soc) / 100.0 * STORAGE_ENERGY_CAPACITY_KWH);
        double maximumDischargePower = intervalHours > 0
                ? Math.min(STORAGE_MAX_DISCHARGE_POWER_KW, availableDischargeEnergy / intervalHours)
                : STORAGE_MAX_DISCHARGE_POWER_KW;
        double maximumChargePower = intervalHours > 0
                ? Math.min(STORAGE_MAX_CHARGE_POWER_KW, availableChargeEnergy / intervalHours)
                : STORAGE_MAX_CHARGE_POWER_KW;
        maximumDischargePower = Math.min(maximumDischargePower, constrainedCapacity);
        maximumChargePower = Math.min(maximumChargePower, constrainedCapacity);
        return new StorageConstraint(
                soc,
                STORAGE_MIN_SOC,
                STORAGE_MAX_SOC,
                STORAGE_ENERGY_CAPACITY_KWH,
                maximumChargePower,
                maximumDischargePower,
                STORAGE_CHARGE_EFFICIENCY,
                STORAGE_DISCHARGE_EFFICIENCY,
                STORAGE_CYCLE_COST,
                STORAGE_STATE_OF_HEALTH,
                DEFAULT_RESERVED_UP_POWER,
                DEFAULT_RESERVED_DOWN_POWER
        );
    }

    private double readSoc(String payloadJson) {
        try {
            JsonNode rootNode = objectMapper.readTree(payloadJson);
            JsonNode socNode = rootNode.path("metrics").path("soc");
            if (socNode.isNumber()) {
                return clamp(socNode.asDouble(), STORAGE_MIN_SOC, STORAGE_MAX_SOC);
            }
            return 55.0;
        } catch (JsonProcessingException exception) {
            return 55.0;
        }
    }

    private void persistSnapshot(ResourceCapabilitySnapshot snapshot) {
        ResourceCapabilitySnapshotEntity entity = new ResourceCapabilitySnapshotEntity();
        entity.setCapabilityId(snapshot.capabilityId());
        entity.setSnapshotGroupId(snapshot.snapshotGroupId());
        entity.setStationId(snapshot.stationId());
        entity.setResourceType(snapshot.resourceType().name());
        entity.setCurrentPower(snapshot.currentPower());
        entity.setAvailableCapacity(snapshot.availableCapacity());
        entity.setMinimumPower(snapshot.minimumPower());
        entity.setMaximumPower(snapshot.maximumPower());
        entity.setMaximumRampUp(snapshot.maximumRampUp());
        entity.setMaximumRampDown(snapshot.maximumRampDown());
        entity.setAvailableEnergyKwh(snapshot.availableEnergyKwh());
        if (snapshot.storageConstraint() != null) {
            entity.setSoc(snapshot.storageConstraint().soc());
            entity.setMinimumSoc(snapshot.storageConstraint().minimumSoc());
            entity.setMaximumSoc(snapshot.storageConstraint().maximumSoc());
            entity.setEnergyCapacityKwh(snapshot.storageConstraint().energyCapacityKWh());
            entity.setMaximumChargePowerKw(snapshot.storageConstraint().maximumChargePowerKW());
            entity.setMaximumDischargePowerKw(snapshot.storageConstraint().maximumDischargePowerKW());
            entity.setChargeEfficiency(snapshot.storageConstraint().chargeEfficiency());
            entity.setDischargeEfficiency(snapshot.storageConstraint().dischargeEfficiency());
            entity.setCycleCost(snapshot.storageConstraint().cycleCost());
            entity.setStateOfHealth(snapshot.storageConstraint().stateOfHealth());
            entity.setReservedUpPower(snapshot.storageConstraint().reservedUpPower());
            entity.setReservedDownPower(snapshot.storageConstraint().reservedDownPower());
        } else {
            entity.setReservedUpPower(0.0);
            entity.setReservedDownPower(0.0);
        }
        entity.setLinkState(snapshot.linkState().name());
        entity.setDataAge(snapshot.dataAge());
        entity.setCapabilityConfidence(snapshot.capabilityConfidence());
        entity.setDataTrusted(snapshot.dataTrusted());
        entity.setUnavailableReason(snapshot.unavailableReason());
        entity.setSource(snapshot.source());
        entity.setQualityCode(snapshot.qualityCode().name());
        entity.setVersion(snapshot.version());
        entity.setGeneratedAt(snapshot.generatedAt());
        entity.setValidFrom(snapshot.validFrom());
        entity.setValidUntil(snapshot.validUntil());
        entity.setReceivedAt(snapshot.receivedAt());
        entity.setPayloadJson(writePayload(snapshot));
        entity.setCreatedAt(OffsetDateTime.now(clock));
        resourceCapabilitySnapshotRepository.save(entity);
    }

    private String writePayload(ResourceCapabilitySnapshot snapshot) {
        Map<String, Object> payload = Map.of(
                "stationId", snapshot.stationId(),
                "resourceType", snapshot.resourceType().getValue(),
                "availableCapacity", snapshot.availableCapacity(),
                "environmentAssessment", snapshot.environmentAssessment(),
                "linkState", snapshot.linkState().name(),
                "dataTrusted", snapshot.dataTrusted()
        );
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "RESOURCE_CAPABILITY_SERIALIZATION_FAILED",
                    "资源能力快照序列化失败",
                    Map.of()
            );
        }
    }

    /**
     * 从能力快照载荷读取环境感知结果。
     *
     * @param entity 能力快照实体
     * @return 环境感知评估结果
     */
    private EnvironmentAssessmentResult readEnvironmentAssessment(ResourceCapabilitySnapshotEntity entity) {
        try {
            JsonNode node = objectMapper.readTree(entity.getPayloadJson()).path("environmentAssessment");
            if (!node.isMissingNode() && !node.isNull()) {
                return objectMapper.treeToValue(node, EnvironmentAssessmentResult.class);
            }
        } catch (JsonProcessingException exception) {
            return defaultEnvironmentAssessment(entity);
        }
        return defaultEnvironmentAssessment(entity);
    }

    /**
     * 构建兼容旧版快照的默认环境评估结果。
     *
     * @param entity 能力快照实体
     * @return 默认环境评估结果
     */
    private EnvironmentAssessmentResult defaultEnvironmentAssessment(ResourceCapabilitySnapshotEntity entity) {
        return new EnvironmentAssessmentResult(
                entity.getStationId(),
                entity.getResourceType(),
                1.0,
                "NORMAL",
                List.of("旧版能力快照未记录环境修正，按中性因子处理"),
                List.of(),
                "旧版能力快照缺少环境上下文，当前仅按已保存能力边界展示。",
                List.of(),
                false,
                List.of("环境测量")
        );
    }

    private ResourceCapabilitySnapshot toDomain(ResourceCapabilitySnapshotEntity entity) {
        StorageConstraint storageConstraint = entity.getSoc() == null ? null : new StorageConstraint(
                entity.getSoc(),
                entity.getMinimumSoc(),
                entity.getMaximumSoc(),
                entity.getEnergyCapacityKwh(),
                entity.getMaximumChargePowerKw(),
                entity.getMaximumDischargePowerKw(),
                entity.getChargeEfficiency(),
                entity.getDischargeEfficiency(),
                entity.getCycleCost(),
                entity.getStateOfHealth(),
                entity.getReservedUpPower(),
                entity.getReservedDownPower()
        );
        return new ResourceCapabilitySnapshot(
                entity.getCapabilityId(),
                entity.getSnapshotGroupId(),
                entity.getStationId(),
                ResourceType.valueOf(entity.getResourceType()),
                entity.getCurrentPower(),
                entity.getAvailableCapacity(),
                entity.getMinimumPower(),
                entity.getMaximumPower(),
                entity.getMaximumRampUp(),
                entity.getMaximumRampDown(),
                entity.getAvailableEnergyKwh(),
                storageConstraint,
                readEnvironmentAssessment(entity),
                LinkState.valueOf(entity.getLinkState()),
                entity.getDataAge(),
                entity.getCapabilityConfidence(),
                entity.isDataTrusted(),
                entity.getUnavailableReason(),
                entity.getGeneratedAt(),
                entity.getValidFrom(),
                entity.getValidUntil(),
                entity.getSource(),
                QualityCode.valueOf(entity.getQualityCode()),
                entity.getVersion(),
                entity.getReceivedAt()
        );
    }

    private ResourceCapabilityResponse toResponse(ResourceCapabilitySnapshot snapshot) {
        StorageConstraintResponse storageConstraint = snapshot.storageConstraint() == null ? null : new StorageConstraintResponse(
                snapshot.storageConstraint().soc(),
                snapshot.storageConstraint().minimumSoc(),
                snapshot.storageConstraint().maximumSoc(),
                snapshot.storageConstraint().energyCapacityKWh(),
                snapshot.storageConstraint().maximumChargePowerKW(),
                snapshot.storageConstraint().maximumDischargePowerKW(),
                snapshot.storageConstraint().chargeEfficiency(),
                snapshot.storageConstraint().dischargeEfficiency(),
                snapshot.storageConstraint().cycleCost(),
                snapshot.storageConstraint().stateOfHealth(),
                snapshot.storageConstraint().reservedUpPower(),
                snapshot.storageConstraint().reservedDownPower()
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
                storageConstraint,
                toEnvironmentResponse(snapshot.environmentAssessment()),
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
     * 转换环境感知响应。
     *
     * @param result 环境感知评估结果
     * @return 环境感知响应
     */
    private EnvironmentAssessmentResponse toEnvironmentResponse(EnvironmentAssessmentResult result) {
        if (result == null) {
            return null;
        }
        return new EnvironmentAssessmentResponse(
                result.stationId(),
                result.resourceType(),
                result.environmentFactor(),
                result.riskLevel(),
                result.correctionReasons(),
                result.affectedDeviceMetrics(),
                result.deviceDataInterpretation(),
                result.safetyConstraints(),
                result.dataAvailable(),
                result.missingFields()
        );
    }

    private double resolveLinkFactor(LinkState linkState) {
        return switch (linkState) {
            case S0, S4 -> 1.0;
            case S1 -> 0.85;
            case S2 -> 0.60;
            case S3 -> 0.0;
        };
    }

    private double resolveRampRate(ResourceType resourceType, double availableCapacity) {
        return switch (resourceType) {
            case PHOTOVOLTAIC -> availableCapacity * 0.35;
            case WIND -> availableCapacity * 0.28;
            case THERMAL -> availableCapacity * 0.18;
            case GRID -> availableCapacity * 0.40;
            case STORAGE -> availableCapacity * 0.60;
            case LOAD -> availableCapacity * 0.22;
            case CHARGING -> availableCapacity * 0.25;
        };
    }

    private double resolveCapabilityConfidence(LinkState linkState, boolean dataTrusted, boolean qualityTrusted) {
        if (!dataTrusted) {
            return 0.0;
        }
        if (!qualityTrusted) {
            return 0.35;
        }
        return switch (linkState) {
            case S0 -> 0.98;
            case S1 -> 0.82;
            case S2 -> 0.65;
            case S3 -> 0.0;
            case S4 -> 0.76;
        };
    }

    private String resolveUnavailableReason(TelemetrySnapshotEntity telemetry, boolean dataFresh, boolean qualityTrusted) {
        if (!telemetry.isOnline()) {
            return "场站离线";
        }
        if (telemetry.getLinkState() == LinkState.S3) {
            return "场站处于失联自治状态";
        }
        if (!dataFresh) {
            return "遥测数据已过期";
        }
        if (!qualityTrusted) {
            return "遥测质量不可信";
        }
        return "";
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private ApiException notFound(String code, String message, String identifier) {
        return new ApiException(HttpStatus.NOT_FOUND, code, message, Map.of("identifier", identifier));
    }
}

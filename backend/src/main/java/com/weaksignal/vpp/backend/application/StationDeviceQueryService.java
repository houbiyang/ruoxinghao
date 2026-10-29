package com.weaksignal.vpp.backend.application;

import com.weaksignal.vpp.backend.api.ApiException;
import com.weaksignal.vpp.backend.api.dto.ResourceMetricResponse;
import com.weaksignal.vpp.backend.api.dto.StationDeviceDetailResponse;
import com.weaksignal.vpp.backend.api.dto.StationDevicePowerTrendResponse;
import com.weaksignal.vpp.backend.api.dto.StationDeviceQualityTrendPointResponse;
import com.weaksignal.vpp.backend.api.dto.StationDeviceSnapshotResponse;
import com.weaksignal.vpp.backend.api.dto.StationDeviceTrendPointResponse;
import com.weaksignal.vpp.backend.domain.LinkState;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.ResourceDeviceEntity;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.StationEntity;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.TelemetryHistoryEntity;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.TelemetrySnapshotEntity;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.ResourceDeviceRepository;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.StationRepository;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.TelemetryHistoryRepository;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.TelemetrySnapshotRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 场站子设备查询服务。
 */
@Service
public class StationDeviceQueryService {

    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    private static final List<DeviceCatalog> PHOTOVOLTAIC_DEVICES = List.of(
            new DeviceCatalog("pv-array-a", "组件方阵 A", "pv-array", false, 1),
            new DeviceCatalog("pv-array-b", "组件方阵 B", "pv-array", false, 2),
            new DeviceCatalog("inverter-group", "逆变器组", "inverter-group", true, 3),
            new DeviceCatalog("combiner-box-01", "汇流箱", "combiner-box", false, 4),
            new DeviceCatalog("weather-station-01", "气象站", "weather-station", false, 5)
    );
    private static final List<DeviceCatalog> WIND_DEVICES = List.of(
            new DeviceCatalog("wind-turbine-a", "风机 A", "wind-turbine", true, 1),
            new DeviceCatalog("wind-turbine-b", "风机 B", "wind-turbine", true, 2),
            new DeviceCatalog("yaw-controller-01", "偏航控制器", "yaw-controller", false, 3),
            new DeviceCatalog("anemometer-01", "测风仪", "anemometer", false, 4),
            new DeviceCatalog("wind-edge-01", "风电边缘控制器", "edge-controller", false, 5)
    );
    private static final List<DeviceCatalog> THERMAL_DEVICES = List.of(
            new DeviceCatalog("thermal-generator-01", "发电机组 01", "thermal-generator", true, 1),
            new DeviceCatalog("thermal-turbine-01", "汽轮机", "steam-turbine", false, 2),
            new DeviceCatalog("thermal-boiler-01", "锅炉系统", "boiler-system", false, 3),
            new DeviceCatalog("thermal-meter-01", "厂用电表计", "thermal-meter", false, 4),
            new DeviceCatalog("thermal-edge-01", "火电边缘控制器", "edge-controller", false, 5)
    );
    private static final List<DeviceCatalog> GRID_DEVICES = List.of(
            new DeviceCatalog("grid-breaker-01", "并网开关", "grid-breaker", true, 1),
            new DeviceCatalog("grid-transformer-01", "10kV 变压器", "grid-transformer", false, 2),
            new DeviceCatalog("grid-meter-01", "双向表计", "grid-meter", false, 3),
            new DeviceCatalog("grid-protection-01", "保护测控单元", "protection-relay", false, 4),
            new DeviceCatalog("grid-edge-01", "并网边缘网关", "edge-controller", false, 5)
    );
    private static final List<DeviceCatalog> STORAGE_DEVICES = List.of(
            new DeviceCatalog("storage-pcs-01", "PCS 01", "storage-pcs", true, 1),
            new DeviceCatalog("storage-bms-01", "电池管理系统", "battery-management", false, 2),
            new DeviceCatalog("battery-cluster-01", "电池簇 01", "battery-cluster", false, 3),
            new DeviceCatalog("thermal-unit-01", "温控单元", "thermal-management", false, 4),
            new DeviceCatalog("storage-edge-01", "储能边缘策略器", "edge-controller", false, 5)
    );
    private static final List<DeviceCatalog> LOAD_DEVICES = List.of(
            new DeviceCatalog("load-feeder-01", "冷站负荷簇", "load-feeder", true, 1),
            new DeviceCatalog("load-feeder-02", "生产支路", "load-feeder", true, 2),
            new DeviceCatalog("hvac-controller-01", "空调群控器", "hvac-controller", false, 3),
            new DeviceCatalog("load-meter-01", "负荷表计", "load-meter", false, 4),
            new DeviceCatalog("load-edge-01", "需求响应终端", "edge-controller", false, 5)
    );
    private static final List<DeviceCatalog> CHARGING_DEVICES = List.of(
            new DeviceCatalog("charge-pile-01", "直流桩 01", "charging-pile", true, 1),
            new DeviceCatalog("charge-pile-02", "交流桩 02", "charging-pile", true, 2),
            new DeviceCatalog("charge-distributor-01", "功率分配单元", "charge-distributor", false, 3),
            new DeviceCatalog("queue-sensor-01", "排队检测器", "queue-sensor", false, 4),
            new DeviceCatalog("charge-edge-01", "有序充电控制器", "edge-controller", false, 5)
    );

    private final StationRepository stationRepository;
    private final ResourceDeviceRepository resourceDeviceRepository;
    private final TelemetrySnapshotRepository telemetrySnapshotRepository;
    private final TelemetryHistoryRepository telemetryHistoryRepository;
    private final Clock clock;

    /**
     * 注入子设备查询依赖。
     *
     * @param stationRepository 场站仓储
     * @param resourceDeviceRepository 设备仓储
     * @param telemetrySnapshotRepository 遥测快照仓储
     * @param telemetryHistoryRepository 遥测历史仓储
     * @param clock 系统时钟
     */
    public StationDeviceQueryService(
            StationRepository stationRepository,
            ResourceDeviceRepository resourceDeviceRepository,
            TelemetrySnapshotRepository telemetrySnapshotRepository,
            TelemetryHistoryRepository telemetryHistoryRepository,
            Clock clock
    ) {
        this.stationRepository = stationRepository;
        this.resourceDeviceRepository = resourceDeviceRepository;
        this.telemetrySnapshotRepository = telemetrySnapshotRepository;
        this.telemetryHistoryRepository = telemetryHistoryRepository;
        this.clock = clock;
    }

    /**
     * 查询场站子设备快照列表。
     *
     * @param stationId 场站标识
     * @return 子设备快照
     */
    public List<StationDeviceSnapshotResponse> getDevices(String stationId) {
        requireStation(stationId);
        TelemetrySnapshotEntity snapshot = telemetrySnapshotRepository.findByStationId(stationId).orElse(null);
        Map<String, ResourceDeviceEntity> entityMap = loadEntityMap(stationId);
        return catalogsFor(stationId).stream()
                .map(catalog -> toSnapshotResponse(stationId, catalog, entityMap.get(catalog.deviceId()), sampleOf(snapshot)))
                .toList();
    }

    /**
     * 查询单个子设备详情。
     *
     * @param stationId 场站标识
     * @param deviceId 设备标识
     * @return 子设备详情
     */
    public StationDeviceDetailResponse getDeviceDetail(String stationId, String deviceId) {
        requireStation(stationId);
        DeviceCatalog catalog = requireCatalog(stationId, deviceId);
        ResourceDeviceEntity entity = resourceDeviceRepository.findByStationIdAndDeviceId(stationId, deviceId)
                .orElse(null);
        TelemetrySnapshotEntity snapshot = telemetrySnapshotRepository.findByStationId(stationId).orElse(null);
        DeviceReading reading = deriveReading(stationId, catalog, entity, sampleOf(snapshot));
        return new StationDeviceDetailResponse(
                catalog.deviceId(),
                stationId,
                catalog.deviceName(),
                catalog.deviceType(),
                reading.status(),
                reading.activePower(),
                reading.communicationStatus(),
                reading.linkState(),
                reading.latency(),
                reading.packetLossRate(),
                reading.dataAge(),
                TIMESTAMP_FORMATTER.format(reading.updatedAt()),
                catalog.controllable(),
                reading.minimumPower(),
                reading.maximumPower(),
                reading.metrics()
        );
    }

    /**
     * 查询子设备执行趋势。
     *
     * @param stationId 场站标识
     * @param deviceId 设备标识
     * @param from 起始时间
     * @param to 截止时间
     * @param limit 返回数量
     * @return 趋势数据
     */
    public StationDevicePowerTrendResponse getPowerTrend(
            String stationId,
            String deviceId,
            OffsetDateTime from,
            OffsetDateTime to,
            int limit
    ) {
        requireStation(stationId);
        DeviceCatalog catalog = requireCatalog(stationId, deviceId);
        List<TelemetryHistoryEntity> history = telemetryHistoryRepository.findByStationIdAndReceivedTimestampBetweenOrderByReceivedTimestampAsc(
                stationId,
                from,
                to,
                PageRequest.of(0, limit)
        );
        DeviceTrendMeta trendMeta = trendMeta(catalog);
        List<StationDeviceTrendPointResponse> points = history.stream()
                .map(item -> toTrendPoint(catalog, item))
                .toList();
        return new StationDevicePowerTrendResponse(
                catalog.deviceId(),
                trendMeta.mode(),
                trendMeta.primaryLabel(),
                trendMeta.primaryUnit(),
                trendMeta.secondaryLabel(),
                trendMeta.secondaryUnit(),
                points
        );
    }

    /**
     * 查询子设备链路质量趋势。
     *
     * @param stationId 场站标识
     * @param deviceId 设备标识
     * @param from 起始时间
     * @param to 截止时间
     * @param limit 返回数量
     * @return 链路质量趋势
     */
    public List<StationDeviceQualityTrendPointResponse> getQualityTrend(
            String stationId,
            String deviceId,
            OffsetDateTime from,
            OffsetDateTime to,
            int limit
    ) {
        requireStation(stationId);
        DeviceCatalog catalog = requireCatalog(stationId, deviceId);
        List<TelemetryHistoryEntity> history = telemetryHistoryRepository.findByStationIdAndReceivedTimestampBetweenOrderByReceivedTimestampAsc(
                stationId,
                from,
                to,
                PageRequest.of(0, limit)
        );
        return history.stream()
                .map(item -> {
                    StationSample sample = sampleOf(item);
                    DeviceReading reading = deriveReading(stationId, catalog, null, sample);
                    return new StationDeviceQualityTrendPointResponse(
                            TIMESTAMP_FORMATTER.format(item.getReceivedTimestamp()),
                            reading.latency(),
                            reading.packetLossRate(),
                            reading.dataAge(),
                            reading.linkState()
                    );
                })
                .toList();
    }

    private StationEntity requireStation(String stationId) {
        return stationRepository.findByStationId(stationId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "STATION_NOT_FOUND",
                        "场站不存在",
                        Map.of("stationId", stationId)
                ));
    }

    private DeviceCatalog requireCatalog(String stationId, String deviceId) {
        return catalogsFor(stationId).stream()
                .filter(catalog -> catalog.deviceId().equals(deviceId))
                .findFirst()
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "DEVICE_NOT_FOUND",
                        "设备不存在",
                        Map.of("stationId", stationId, "deviceId", deviceId)
                ));
    }

    private List<DeviceCatalog> catalogsFor(String stationId) {
        return switch (stationId) {
            case "pv-gobi-01" -> PHOTOVOLTAIC_DEVICES;
            case "wind-ridge-02" -> WIND_DEVICES;
            case "thermal-plant-06" -> THERMAL_DEVICES;
            case "grid-link-00" -> GRID_DEVICES;
            case "storage-hub-03" -> STORAGE_DEVICES;
            case "load-oasis-04" -> LOAD_DEVICES;
            case "charge-corridor-05" -> CHARGING_DEVICES;
            default -> List.of();
        };
    }

    private Map<String, ResourceDeviceEntity> loadEntityMap(String stationId) {
        Map<String, ResourceDeviceEntity> entityMap = new HashMap<>();
        for (ResourceDeviceEntity entity : resourceDeviceRepository.findByStationId(stationId)) {
            entityMap.put(entity.getDeviceId(), entity);
        }
        return entityMap;
    }

    private StationDeviceSnapshotResponse toSnapshotResponse(
            String stationId,
            DeviceCatalog catalog,
            ResourceDeviceEntity entity,
            StationSample sample
    ) {
        DeviceReading reading = deriveReading(stationId, catalog, entity, sample);
        return new StationDeviceSnapshotResponse(
                catalog.deviceId(),
                stationId,
                catalog.deviceName(),
                catalog.deviceType(),
                reading.status(),
                reading.activePower(),
                reading.communicationStatus(),
                reading.linkState(),
                reading.latency(),
                reading.packetLossRate(),
                reading.dataAge(),
                TIMESTAMP_FORMATTER.format(reading.updatedAt()),
                catalog.controllable(),
                reading.metrics()
        );
    }

    private DeviceReading deriveReading(
            String stationId,
            DeviceCatalog catalog,
            ResourceDeviceEntity entity,
            StationSample sample
    ) {
        OffsetDateTime updatedAt = entity != null && entity.getUpdatedAt() != null
                ? entity.getUpdatedAt()
                : sample.updatedAt();
        if (sample.snapshotMissing()) {
            return new DeviceReading(
                    mapDeviceStatus(entity == null ? "OFFLINE" : entity.getStatus()),
                    "失联",
                    null,
                    LinkState.S3,
                    999,
                    100,
                    0,
                    updatedAt,
                    null,
                    null,
                    offlineMetrics(catalog)
            );
        }

        double capacityRatio = sample.availableCapacity() <= 0 ? 0 : sample.activePower() / sample.availableCapacity();
        double dcBridgePower = round(sample.activePower() / 0.978);
        double irradiance = round(320 + clamp(capacityRatio, 0, 1) * 620);
        double moduleTemperature = round(20 + clamp(capacityRatio, 0, 1) * 19 + sample.packetLossRate() * 0.1);
        double ambientTemperature = round(14 + clamp(capacityRatio, 0, 1) * 15);
        double windSpeed = round(2.6 + clamp(capacityRatio, 0, 1) * 5.4);
        double dcVoltage = round(845 + clamp(capacityRatio, 0, 1) * 42);
        double acVoltage = round(378 + Math.min(sample.latency(), 150) * 0.04);
        double efficiency = round(sample.activePower() <= 0 ? 0 : sample.activePower() / Math.max(dcBridgePower, 1) * 100);
        double arrayAPower = round(dcBridgePower * 0.56);
        double arrayBPower = round(dcBridgePower * 0.44);
        Double activePower = switch (catalog.deviceId()) {
            case "pv-array-a" -> arrayAPower;
            case "pv-array-b" -> arrayBPower;
            case "inverter-group" -> round(sample.activePower());
            case "combiner-box-01" -> dcBridgePower;
            case "weather-station-01", "yaw-controller-01", "anemometer-01", "wind-edge-01",
                    "storage-bms-01", "battery-cluster-01", "thermal-unit-01", "storage-edge-01",
                    "hvac-controller-01", "load-meter-01", "load-edge-01",
                    "charge-distributor-01", "queue-sensor-01", "charge-edge-01" -> null;
            case "wind-turbine-a" -> round(sample.activePower() * 0.52);
            case "wind-turbine-b" -> round(sample.activePower() * 0.48);
            case "grid-breaker-01", "grid-transformer-01", "grid-meter-01", "storage-pcs-01" -> round(sample.activePower());
            case "load-feeder-01" -> round(sample.activePower() * 0.56);
            case "load-feeder-02" -> round(sample.activePower() * 0.44);
            case "charge-pile-01" -> round(sample.activePower() * 0.76);
            case "charge-pile-02" -> round(sample.activePower() * 0.24);
            default -> round(sample.activePower());
        };

        int latency = sample.latency() + latencyOffset(catalog.deviceId());
        double packetLossRate = round(Math.max(0, sample.packetLossRate() + packetLossOffset(catalog.deviceId())));
        long dataAge = sample.dataAge() + dataAgeOffset(catalog.deviceId());
        LinkState linkState = deriveLinkState(sample.linkState(), catalog.deviceId(), sample.online());
        String communicationStatus = communicationStatus(linkState, sample.online());
        String status = sample.online() ? mapOperationalStatus(entity, linkState, activePower) : "离线";

        Double minimumPower = catalog.controllable() && activePower != null ? 0.0 : null;
        Double maximumPower = catalog.controllable() && activePower != null
                ? round(Math.max(activePower, sample.availableCapacity() * controllableUpperRatio(catalog.deviceId())))
                : null;

        return new DeviceReading(
                status,
                communicationStatus,
                activePower,
                linkState,
                latency,
                packetLossRate,
                dataAge,
                updatedAt,
                minimumPower,
                maximumPower,
                metricsFor(stationId, catalog.deviceId(), sample, activePower, arrayAPower, arrayBPower, irradiance,
                        moduleTemperature, ambientTemperature, windSpeed, dcBridgePower, dcVoltage, acVoltage, efficiency)
        );
    }

    private List<ResourceMetricResponse> metricsFor(
            String stationId,
            String deviceId,
            StationSample sample,
            Double activePower,
            double arrayAPower,
            double arrayBPower,
            double irradiance,
            double moduleTemperature,
            double ambientTemperature,
            double windSpeed,
            double dcBridgePower,
            double dcVoltage,
            double acVoltage,
            double efficiency
    ) {
        List<ResourceMetricResponse> metrics = new ArrayList<>();
        switch (deviceId) {
            case "pv-array-a", "pv-array-b" -> {
                metrics.add(new ResourceMetricResponse("辐照度", irradiance, "W/m2"));
                metrics.add(new ResourceMetricResponse("组件温度", moduleTemperature, "°C"));
                metrics.add(new ResourceMetricResponse("直流功率", "pv-array-a".equals(deviceId) ? arrayAPower : arrayBPower, "kW"));
            }
            case "inverter-group" -> {
                metrics.add(new ResourceMetricResponse("交流功率", activePower, "kW"));
                metrics.add(new ResourceMetricResponse("转换效率", efficiency, "%"));
                metrics.add(new ResourceMetricResponse("直流电压", dcVoltage, "V"));
                metrics.add(new ResourceMetricResponse("交流电压", acVoltage, "V"));
            }
            case "combiner-box-01" -> {
                metrics.add(new ResourceMetricResponse("输入支路数", 24, "路"));
                metrics.add(new ResourceMetricResponse("母线电压", dcVoltage, "V"));
                metrics.add(new ResourceMetricResponse("输出电流", round(dcBridgePower * 1000 / Math.max(dcVoltage, 1)), "A"));
            }
            case "weather-station-01" -> {
                metrics.add(new ResourceMetricResponse("辐照度", irradiance, "W/m2"));
                metrics.add(new ResourceMetricResponse("环境温度", ambientTemperature, "°C"));
                metrics.add(new ResourceMetricResponse("风速", windSpeed, "m/s"));
            }
            case "wind-turbine-a", "wind-turbine-b" -> {
                metrics.add(new ResourceMetricResponse("风速", windSpeed + 1.6, "m/s"));
                metrics.add(new ResourceMetricResponse("叶轮转速", round(12 + windSpeed * 0.8), "rpm"));
                metrics.add(new ResourceMetricResponse("机舱温度", round(24 + sample.packetLossRate() * 0.4), "°C"));
            }
            case "yaw-controller-01" -> {
                metrics.add(new ResourceMetricResponse("偏航角", round(12 + windSpeed * 2.1), "°"));
                metrics.add(new ResourceMetricResponse("角度偏差", round(sample.packetLossRate() * 0.7 + 1.2), "°"));
                metrics.add(new ResourceMetricResponse("执行状态", sample.online() ? "闭环跟踪" : "失联", ""));
            }
            case "anemometer-01" -> {
                metrics.add(new ResourceMetricResponse("风速", windSpeed + 1.8, "m/s"));
                metrics.add(new ResourceMetricResponse("风向", round(185 + sample.latency() * 0.2), "°"));
                metrics.add(new ResourceMetricResponse("环境温度", ambientTemperature, "°C"));
            }
            case "wind-edge-01" -> {
                metrics.add(new ResourceMetricResponse("协同状态", communicationStatus(sample.linkState(), sample.online()), ""));
                metrics.add(new ResourceMetricResponse("控制步长", 8, "kW"));
                metrics.add(new ResourceMetricResponse("自治窗口", 90, "s"));
            }
            case "thermal-generator-01" -> {
                metrics.add(new ResourceMetricResponse("有功出力", activePower, "kW"));
                metrics.add(new ResourceMetricResponse("频率", round(49.96 + sample.packetLossRate() * 0.01), "Hz"));
                metrics.add(new ResourceMetricResponse("功率因数", round(0.95 + sample.packetLossRate() * 0.002), ""));
            }
            case "thermal-turbine-01" -> {
                metrics.add(new ResourceMetricResponse("转速", 3000, "rpm"));
                metrics.add(new ResourceMetricResponse("缸温", round(515 + sample.packetLossRate() * 0.8), "°C"));
                metrics.add(new ResourceMetricResponse("调门开度", round(62 + loadRatio(sample) * 18), "%"));
            }
            case "thermal-boiler-01" -> {
                metrics.add(new ResourceMetricResponse("蒸汽压力", round(13.2 + loadRatio(sample) * 1.6), "MPa"));
                metrics.add(new ResourceMetricResponse("炉膛温度", round(850 + loadRatio(sample) * 60), "°C"));
                metrics.add(new ResourceMetricResponse("负荷率", round(loadRatio(sample) * 100), "%"));
            }
            case "thermal-meter-01" -> {
                metrics.add(new ResourceMetricResponse("厂用电率", 6.2, "%"));
                metrics.add(new ResourceMetricResponse("上网功率", activePower, "kW"));
                metrics.add(new ResourceMetricResponse("计量状态", sample.online() ? "在线" : "离线", ""));
            }
            case "thermal-edge-01" -> {
                metrics.add(new ResourceMetricResponse("边缘链路", communicationStatus(sample.linkState(), sample.online()), ""));
                metrics.add(new ResourceMetricResponse("爬坡约束", 18, "kW/min"));
                metrics.add(new ResourceMetricResponse("备用策略", "旋转备用", ""));
            }
            case "grid-breaker-01" -> {
                metrics.add(new ResourceMetricResponse("开关状态", sample.online() ? "合位" : "分位", ""));
                metrics.add(new ResourceMetricResponse("交换功率", activePower, "kW"));
                metrics.add(new ResourceMetricResponse("保护状态", sample.linkState() == LinkState.S3 ? "待孤网" : "稳定", ""));
            }
            case "grid-transformer-01" -> {
                metrics.add(new ResourceMetricResponse("负载率", round(sample.availableCapacity() <= 0 ? 0 : sample.activePower() / sample.availableCapacity() * 100), "%"));
                metrics.add(new ResourceMetricResponse("母线电压", acVoltage, "V"));
                metrics.add(new ResourceMetricResponse("油温", round(38 + sample.packetLossRate() * 0.5), "°C"));
            }
            case "grid-meter-01" -> {
                metrics.add(new ResourceMetricResponse("双向功率", activePower, "kW"));
                metrics.add(new ResourceMetricResponse("频率", round(49.95 + sample.packetLossRate() * 0.01), "Hz"));
                metrics.add(new ResourceMetricResponse("计量状态", sample.online() ? "在线" : "离线", ""));
            }
            case "grid-protection-01" -> {
                metrics.add(new ResourceMetricResponse("保护状态", sample.linkState() == LinkState.S3 ? "孤网保护" : "投入", ""));
                metrics.add(new ResourceMetricResponse("动作门槛", 110, "kV"));
                metrics.add(new ResourceMetricResponse("最近自检", "通过", ""));
            }
            case "grid-edge-01" -> {
                metrics.add(new ResourceMetricResponse("边缘链路", communicationStatus(sample.linkState(), sample.online()), ""));
                metrics.add(new ResourceMetricResponse("策略缓存", 12, "条"));
                metrics.add(new ResourceMetricResponse("自治窗口", 120, "s"));
            }
            case "storage-pcs-01" -> {
                metrics.add(new ResourceMetricResponse("交流功率", activePower, "kW"));
                metrics.add(new ResourceMetricResponse("直流母线", dcVoltage, "V"));
                metrics.add(new ResourceMetricResponse("转换效率", round(95.8 + Math.max(0, 1 - sample.packetLossRate() * 0.02)), "%"));
            }
            case "storage-bms-01" -> {
                metrics.add(new ResourceMetricResponse("SOC", round(58 + sample.activePower() / 12), "%"));
                metrics.add(new ResourceMetricResponse("簇温差", round(2.1 + sample.packetLossRate() * 0.1), "°C"));
                metrics.add(new ResourceMetricResponse("单簇电压", round(715 + sample.activePower() * 0.2), "V"));
            }
            case "battery-cluster-01" -> {
                metrics.add(new ResourceMetricResponse("簇电流", round(Math.max(sample.activePower(), 1) * 2.8), "A"));
                metrics.add(new ResourceMetricResponse("簇电压", round(702 + sample.activePower() * 0.18), "V"));
                metrics.add(new ResourceMetricResponse("健康度", round(96.4 - sample.packetLossRate() * 0.2), "%"));
            }
            case "thermal-unit-01" -> {
                metrics.add(new ResourceMetricResponse("冷却液温度", round(23 + sample.packetLossRate() * 0.5), "°C"));
                metrics.add(new ResourceMetricResponse("风机转速", round(820 + sample.activePower() * 4.2), "rpm"));
                metrics.add(new ResourceMetricResponse("温控状态", sample.online() ? "运行" : "失联", ""));
            }
            case "storage-edge-01" -> {
                metrics.add(new ResourceMetricResponse("自治策略", sample.linkState() == LinkState.S3 ? "已接管" : "待命", ""));
                metrics.add(new ResourceMetricResponse("爬坡约束", 12, "kW/min"));
                metrics.add(new ResourceMetricResponse("SOC 安全边界", "10% - 90%", ""));
            }
            case "load-feeder-01", "load-feeder-02" -> {
                metrics.add(new ResourceMetricResponse("当前用电", activePower, "kW"));
                metrics.add(new ResourceMetricResponse("可调比例", "load-feeder-01".equals(deviceId) ? 12 : 8, "%"));
                metrics.add(new ResourceMetricResponse("响应状态", sample.linkState() == LinkState.S3 ? "本地保底" : "可协同", ""));
            }
            case "load-edge-01" -> {
                metrics.add(new ResourceMetricResponse("协调窗口", 60, "s"));
                metrics.add(new ResourceMetricResponse("响应终端", sample.online() ? "在线" : "离线", ""));
                metrics.add(new ResourceMetricResponse("本地策略", sample.linkState() == LinkState.S3 ? "已启用" : "待命", ""));
            }
            case "hvac-controller-01" -> {
                metrics.add(new ResourceMetricResponse("冷机台数", 4, "台"));
                metrics.add(new ResourceMetricResponse("设定温度", round(22 + sample.packetLossRate() * 0.2), "°C"));
                metrics.add(new ResourceMetricResponse("控制状态", sample.online() ? "联动" : "失联", ""));
            }
            case "load-meter-01" -> {
                metrics.add(new ResourceMetricResponse("表计功率", round(sample.activePower()), "kW"));
                metrics.add(new ResourceMetricResponse("功率因数", round(0.94 + sample.packetLossRate() * 0.002), ""));
                metrics.add(new ResourceMetricResponse("计量状态", sample.online() ? "在线" : "离线", ""));
            }
            case "charge-pile-01", "charge-pile-02" -> {
                metrics.add(new ResourceMetricResponse("充电功率", activePower, "kW"));
                metrics.add(new ResourceMetricResponse("枪口占用", "charge-pile-01".equals(deviceId) ? 1 : 0, "把"));
                metrics.add(new ResourceMetricResponse("排队车辆", "charge-pile-01".equals(deviceId) ? 2 : 0, "辆"));
            }
            case "charge-distributor-01" -> {
                metrics.add(new ResourceMetricResponse("分配通道", 6, "路"));
                metrics.add(new ResourceMetricResponse("当前充电功率", round(sample.activePower()), "kW"));
                metrics.add(new ResourceMetricResponse("调度状态", sample.online() ? "启用" : "失联", ""));
            }
            case "queue-sensor-01" -> {
                metrics.add(new ResourceMetricResponse("排队车辆", 3, "辆"));
                metrics.add(new ResourceMetricResponse("平均等待", 8, "min"));
                metrics.add(new ResourceMetricResponse("检测状态", sample.online() ? "在线" : "离线", ""));
            }
            case "charge-edge-01" -> {
                metrics.add(new ResourceMetricResponse("有序策略", sample.linkState() == LinkState.S3 ? "本地保守" : "云边协同", ""));
                metrics.add(new ResourceMetricResponse("限流步长", 6, "kW"));
                metrics.add(new ResourceMetricResponse("优先队列", 3, "级"));
            }
            default -> {
                metrics.add(new ResourceMetricResponse("当前功率", activePower, "kW"));
                metrics.add(new ResourceMetricResponse("通信状态", communicationStatus(sample.linkState(), sample.online()), ""));
            }
        }
        if ("pv-gobi-01".equals(stationId) && "weather-station-01".equals(deviceId)) {
            metrics.add(new ResourceMetricResponse("环境湿度", 24, "%"));
        }
        return metrics;
    }

    private List<ResourceMetricResponse> offlineMetrics(DeviceCatalog catalog) {
        return List.of(
                new ResourceMetricResponse("设备类型", catalog.deviceType(), ""),
                new ResourceMetricResponse("状态说明", "当前未收到有效子设备快照", "")
        );
    }

    private String mapOperationalStatus(ResourceDeviceEntity entity, LinkState linkState, Double activePower) {
        if (linkState == LinkState.S3) {
            return "离线";
        }
        if (linkState == LinkState.S2) {
            return "告警";
        }
        if (entity != null && "STANDBY".equals(entity.getStatus())) {
            return "待机";
        }
        if (activePower != null && Math.abs(activePower) < 0.1) {
            return "待机";
        }
        return mapDeviceStatus(entity == null ? "RUNNING" : entity.getStatus());
    }

    private LinkState deriveLinkState(LinkState stationLinkState, String deviceId, boolean online) {
        if (!online) {
            return LinkState.S3;
        }
        if ("weather-station-01".equals(deviceId) && stationLinkState == LinkState.S1) {
            return LinkState.S2;
        }
        if (List.of("wind-edge-01", "thermal-edge-01", "grid-edge-01", "storage-edge-01", "load-edge-01", "charge-edge-01").contains(deviceId)
                && stationLinkState == LinkState.S4) {
            return LinkState.S4;
        }
        return stationLinkState;
    }

    private String communicationStatus(LinkState linkState, boolean online) {
        if (!online) {
            return "失联";
        }
        return switch (linkState) {
            case S0 -> "良好";
            case S1 -> "退化";
            case S2 -> "严重退化";
            case S3 -> "失联";
            case S4 -> "恢复同步";
        };
    }

    private String mapDeviceStatus(String status) {
        return switch (status) {
            case "RUNNING" -> "运行";
            case "STANDBY" -> "待机";
            case "ALARM" -> "告警";
            default -> "离线";
        };
    }

    private int latencyOffset(String deviceId) {
        return switch (deviceId) {
            case "weather-station-01" -> 7;
            case "anemometer-01", "queue-sensor-01" -> 6;
            case "wind-edge-01", "thermal-edge-01", "grid-edge-01", "storage-edge-01", "load-edge-01", "charge-edge-01" -> 5;
            case "grid-meter-01" -> 3;
            default -> 0;
        };
    }

    private double packetLossOffset(String deviceId) {
        return switch (deviceId) {
            case "weather-station-01" -> 0.2;
            case "anemometer-01", "queue-sensor-01" -> 0.2;
            case "wind-edge-01", "thermal-edge-01", "grid-edge-01", "storage-edge-01", "load-edge-01", "charge-edge-01" -> 0.1;
            default -> 0;
        };
    }

    private long dataAgeOffset(String deviceId) {
        return switch (deviceId) {
            case "weather-station-01" -> 1;
            case "anemometer-01", "queue-sensor-01" -> 1;
            case "wind-edge-01", "thermal-edge-01", "grid-edge-01", "storage-edge-01", "load-edge-01", "charge-edge-01" -> 1;
            default -> 0;
        };
    }

    private double controllableUpperRatio(String deviceId) {
        return switch (deviceId) {
            case "inverter-group", "wind-turbine-a", "wind-turbine-b", "thermal-generator-01", "storage-pcs-01" -> 0.92;
            case "load-feeder-01", "load-feeder-02" -> 0.65;
            case "charge-pile-01", "charge-pile-02" -> 0.55;
            case "grid-breaker-01" -> 1.0;
            default -> 0.0;
        };
    }

    private DeviceTrendMeta trendMeta(DeviceCatalog catalog) {
        return switch (catalog.deviceId()) {
            case "weather-station-01" -> new DeviceTrendMeta("environment", "辐照度", "W/m2", "环境温度", "°C");
            case "anemometer-01" -> new DeviceTrendMeta("environment", "风速", "m/s", "环境温度", "°C");
            case "wind-edge-01", "thermal-edge-01", "grid-edge-01", "storage-edge-01", "load-edge-01", "charge-edge-01" -> new DeviceTrendMeta("power", "控制功率", "kW", "目标功率", "kW");
            default -> new DeviceTrendMeta("power", "实测功率", "kW", "目标功率", "kW");
        };
    }

    private StationDeviceTrendPointResponse toTrendPoint(DeviceCatalog catalog, TelemetryHistoryEntity historyEntity) {
        StationSample sample = sampleOf(historyEntity);
        double dcBridgePower = round(sample.activePower() / 0.978);
        double capacityRatio = sample.availableCapacity() <= 0 ? 0 : sample.activePower() / sample.availableCapacity();
        return switch (catalog.deviceId()) {
            case "pv-array-a" -> new StationDeviceTrendPointResponse(
                    TIMESTAMP_FORMATTER.format(historyEntity.getReceivedTimestamp()),
                    round(dcBridgePower * 0.56),
                    round(dcBridgePower * 0.58)
            );
            case "pv-array-b" -> new StationDeviceTrendPointResponse(
                    TIMESTAMP_FORMATTER.format(historyEntity.getReceivedTimestamp()),
                    round(dcBridgePower * 0.44),
                    round(dcBridgePower * 0.46)
            );
            case "inverter-group" -> new StationDeviceTrendPointResponse(
                    TIMESTAMP_FORMATTER.format(historyEntity.getReceivedTimestamp()),
                    round(sample.activePower()),
                    round(Math.max(sample.activePower(), sample.availableCapacity() * 0.88))
            );
            case "combiner-box-01" -> new StationDeviceTrendPointResponse(
                    TIMESTAMP_FORMATTER.format(historyEntity.getReceivedTimestamp()),
                    dcBridgePower,
                    round(dcBridgePower * 1.02)
            );
            case "weather-station-01" -> new StationDeviceTrendPointResponse(
                    TIMESTAMP_FORMATTER.format(historyEntity.getReceivedTimestamp()),
                    round(320 + clamp(capacityRatio, 0, 1) * 620),
                    round(14 + clamp(capacityRatio, 0, 1) * 15)
            );
            case "wind-turbine-a" -> new StationDeviceTrendPointResponse(
                    TIMESTAMP_FORMATTER.format(historyEntity.getReceivedTimestamp()),
                    round(sample.activePower() * 0.52),
                    round(sample.availableCapacity() * 0.44)
            );
            case "wind-turbine-b" -> new StationDeviceTrendPointResponse(
                    TIMESTAMP_FORMATTER.format(historyEntity.getReceivedTimestamp()),
                    round(sample.activePower() * 0.48),
                    round(sample.availableCapacity() * 0.41)
            );
            case "yaw-controller-01" -> new StationDeviceTrendPointResponse(
                    TIMESTAMP_FORMATTER.format(historyEntity.getReceivedTimestamp()),
                    round(12 + clamp(capacityRatio, 0, 1) * 8),
                    round(10 + clamp(capacityRatio, 0, 1) * 7)
            );
            case "anemometer-01" -> new StationDeviceTrendPointResponse(
                    TIMESTAMP_FORMATTER.format(historyEntity.getReceivedTimestamp()),
                    round(2.6 + clamp(capacityRatio, 0, 1) * 7),
                    round(14 + clamp(capacityRatio, 0, 1) * 15)
            );
            case "wind-edge-01" -> new StationDeviceTrendPointResponse(
                    TIMESTAMP_FORMATTER.format(historyEntity.getReceivedTimestamp()),
                    round(sample.activePower() * 0.18),
                    round(sample.activePower() * 0.16)
            );
            case "grid-breaker-01", "grid-transformer-01", "grid-meter-01" -> new StationDeviceTrendPointResponse(
                    TIMESTAMP_FORMATTER.format(historyEntity.getReceivedTimestamp()),
                    round(sample.activePower()),
                    round(Math.max(sample.activePower(), sample.availableCapacity() * 0.3))
            );
            case "grid-protection-01" -> new StationDeviceTrendPointResponse(
                    TIMESTAMP_FORMATTER.format(historyEntity.getReceivedTimestamp()),
                    round(100 + sample.packetLossRate() * 2),
                    round(110 + sample.packetLossRate() * 2)
            );
            case "grid-edge-01" -> new StationDeviceTrendPointResponse(
                    TIMESTAMP_FORMATTER.format(historyEntity.getReceivedTimestamp()),
                    round(sample.activePower() * 0.11),
                    round(sample.activePower() * 0.1)
            );
            case "storage-pcs-01" -> new StationDeviceTrendPointResponse(
                    TIMESTAMP_FORMATTER.format(historyEntity.getReceivedTimestamp()),
                    round(sample.activePower()),
                    round(sample.activePower() * 0.96)
            );
            case "storage-bms-01" -> new StationDeviceTrendPointResponse(
                    TIMESTAMP_FORMATTER.format(historyEntity.getReceivedTimestamp()),
                    round(58 + sample.activePower() / 12),
                    round(60 + sample.activePower() / 12)
            );
            case "battery-cluster-01" -> new StationDeviceTrendPointResponse(
                    TIMESTAMP_FORMATTER.format(historyEntity.getReceivedTimestamp()),
                    round(690 + sample.activePower() * 0.2),
                    round(700 + sample.activePower() * 0.18)
            );
            case "thermal-unit-01" -> new StationDeviceTrendPointResponse(
                    TIMESTAMP_FORMATTER.format(historyEntity.getReceivedTimestamp()),
                    round(23 + sample.packetLossRate() * 0.5),
                    round(25 + sample.packetLossRate() * 0.4)
            );
            case "storage-edge-01" -> new StationDeviceTrendPointResponse(
                    TIMESTAMP_FORMATTER.format(historyEntity.getReceivedTimestamp()),
                    round(sample.activePower() * 0.12),
                    round(sample.activePower() * 0.10)
            );
            case "load-feeder-01" -> new StationDeviceTrendPointResponse(
                    TIMESTAMP_FORMATTER.format(historyEntity.getReceivedTimestamp()),
                    round(sample.activePower() * 0.56),
                    round(sample.activePower() * 0.52)
            );
            case "load-feeder-02" -> new StationDeviceTrendPointResponse(
                    TIMESTAMP_FORMATTER.format(historyEntity.getReceivedTimestamp()),
                    round(sample.activePower() * 0.44),
                    round(sample.activePower() * 0.41)
            );
            case "hvac-controller-01" -> new StationDeviceTrendPointResponse(
                    TIMESTAMP_FORMATTER.format(historyEntity.getReceivedTimestamp()),
                    round(22 + sample.packetLossRate() * 0.2),
                    round(21 + sample.packetLossRate() * 0.15)
            );
            case "load-meter-01" -> new StationDeviceTrendPointResponse(
                    TIMESTAMP_FORMATTER.format(historyEntity.getReceivedTimestamp()),
                    round(sample.activePower()),
                    round(sample.activePower() * 0.98)
            );
            case "load-edge-01" -> new StationDeviceTrendPointResponse(
                    TIMESTAMP_FORMATTER.format(historyEntity.getReceivedTimestamp()),
                    round(sample.activePower() * 0.14),
                    round(sample.activePower() * 0.12)
            );
            case "charge-pile-01" -> new StationDeviceTrendPointResponse(
                    TIMESTAMP_FORMATTER.format(historyEntity.getReceivedTimestamp()),
                    round(sample.activePower() * 0.76),
                    round(sample.availableCapacity() * 0.22)
            );
            case "charge-pile-02" -> new StationDeviceTrendPointResponse(
                    TIMESTAMP_FORMATTER.format(historyEntity.getReceivedTimestamp()),
                    round(sample.activePower() * 0.24),
                    round(sample.availableCapacity() * 0.1)
            );
            case "charge-distributor-01" -> new StationDeviceTrendPointResponse(
                    TIMESTAMP_FORMATTER.format(historyEntity.getReceivedTimestamp()),
                    round(sample.activePower()),
                    round(sample.activePower() * 0.92)
            );
            case "queue-sensor-01" -> new StationDeviceTrendPointResponse(
                    TIMESTAMP_FORMATTER.format(historyEntity.getReceivedTimestamp()),
                    round(3 + sample.packetLossRate()),
                    round(8 + sample.packetLossRate())
            );
            case "charge-edge-01" -> new StationDeviceTrendPointResponse(
                    TIMESTAMP_FORMATTER.format(historyEntity.getReceivedTimestamp()),
                    round(sample.activePower() * 0.1),
                    round(sample.activePower() * 0.08)
            );
            default -> new StationDeviceTrendPointResponse(
                    TIMESTAMP_FORMATTER.format(historyEntity.getReceivedTimestamp()),
                    round(sample.activePower()),
                    round(Math.max(sample.activePower(), sample.availableCapacity() * 0.9))
            );
        };
    }

    private StationSample sampleOf(TelemetrySnapshotEntity snapshot) {
        if (snapshot == null) {
            return StationSample.missing(OffsetDateTime.now(clock));
        }
        return new StationSample(
                snapshot.getActivePower(),
                snapshot.getAvailableCapacity(),
                snapshot.getLatency(),
                snapshot.getPacketLossRate(),
                snapshot.getDataAge(),
                snapshot.getLinkState(),
                snapshot.isOnline(),
                snapshot.getReceivedTimestamp(),
                false
        );
    }

    private StationSample sampleOf(TelemetryHistoryEntity historyEntity) {
        return new StationSample(
                historyEntity.getActivePower(),
                historyEntity.getAvailableCapacity(),
                historyEntity.getLatency(),
                historyEntity.getPacketLossRate(),
                historyEntity.getDataAge(),
                historyEntity.getLinkState(),
                historyEntity.isOnline(),
                historyEntity.getReceivedTimestamp(),
                false
        );
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * 计算当前负荷率。
     *
     * @param sample 场站采样
     * @return 负荷率
     */
    private double loadRatio(StationSample sample) {
        if (sample.availableCapacity() <= 0) {
            return 0.0;
        }
        return Math.max(0.0, sample.activePower() / sample.availableCapacity());
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private record DeviceCatalog(
            String deviceId,
            String deviceName,
            String deviceType,
            boolean controllable,
            int order
    ) {
    }

    private record DeviceReading(
            String status,
            String communicationStatus,
            Double activePower,
            LinkState linkState,
            int latency,
            double packetLossRate,
            long dataAge,
            OffsetDateTime updatedAt,
            Double minimumPower,
            Double maximumPower,
            List<ResourceMetricResponse> metrics
    ) {
    }

    private record DeviceTrendMeta(
            String mode,
            String primaryLabel,
            String primaryUnit,
            String secondaryLabel,
            String secondaryUnit
    ) {
    }

    private record StationSample(
            double activePower,
            double availableCapacity,
            int latency,
            double packetLossRate,
            long dataAge,
            LinkState linkState,
            boolean online,
            OffsetDateTime updatedAt,
            boolean snapshotMissing
    ) {

        private static StationSample missing(OffsetDateTime updatedAt) {
            return new StationSample(0, 0, 999, 100, 0, LinkState.S3, false, updatedAt, true);
        }
    }
}

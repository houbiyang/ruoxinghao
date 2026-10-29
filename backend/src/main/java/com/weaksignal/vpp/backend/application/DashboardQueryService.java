package com.weaksignal.vpp.backend.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weaksignal.vpp.backend.api.ApiException;
import com.weaksignal.vpp.backend.api.dto.DashboardSummaryResponse;
import com.weaksignal.vpp.backend.api.dto.EventResponse;
import com.weaksignal.vpp.backend.api.dto.ExperimentReplayPointResponse;
import com.weaksignal.vpp.backend.api.dto.PowerFlowTrendPointResponse;
import com.weaksignal.vpp.backend.api.dto.PowerTrendPointResponse;
import com.weaksignal.vpp.backend.api.dto.ResourceControlActionResponse;
import com.weaksignal.vpp.backend.api.dto.ResourceDeviceStatusResponse;
import com.weaksignal.vpp.backend.api.dto.ResourceMetricResponse;
import com.weaksignal.vpp.backend.api.dto.ResourceQualityPointResponse;
import com.weaksignal.vpp.backend.api.dto.ResourceTelemetryPointResponse;
import com.weaksignal.vpp.backend.api.dto.ScenePositionResponse;
import com.weaksignal.vpp.backend.api.dto.StationDetailResponse;
import com.weaksignal.vpp.backend.api.dto.StationStateResponse;
import com.weaksignal.vpp.backend.domain.DemoScenario;
import com.weaksignal.vpp.backend.domain.DispatchCommandStatus;
import com.weaksignal.vpp.backend.domain.ResourceType;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.ControlCommandEntity;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.FaultEventEntity;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.ResourceDeviceEntity;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.StationEntity;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.TelemetryHistoryEntity;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.TelemetrySnapshotEntity;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.FaultEventRepository;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.ControlCommandRepository;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.ResourceDeviceRepository;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.StationRepository;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.TelemetryHistoryRepository;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.TelemetrySnapshotRepository;
import com.weaksignal.vpp.backend.infrastructure.redis.RedisRealtimeStateStore;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 总览与场站查询服务。
 */
@Service
public class DashboardQueryService {

    private static final DateTimeFormatter LABEL_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final long POWER_FLOW_BUCKET_SECONDS = 30;
    private static final int POWER_FLOW_TELEMETRY_LIMIT = 10_000;
    private static final String ACTIVE_POWER_COMMAND_TYPE = "SET_ACTIVE_POWER";

    private final StationRepository stationRepository;
    private final TelemetrySnapshotRepository telemetrySnapshotRepository;
    private final TelemetryHistoryRepository telemetryHistoryRepository;
    private final ResourceDeviceRepository resourceDeviceRepository;
    private final FaultEventRepository faultEventRepository;
    private final StationLayoutRegistry stationLayoutRegistry;
    private final RedisRealtimeStateStore redisRealtimeStateStore;
    private final ControlCommandRepository controlCommandRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    /**
     * 注入查询依赖。
     */
    public DashboardQueryService(
            StationRepository stationRepository,
            TelemetrySnapshotRepository telemetrySnapshotRepository,
            TelemetryHistoryRepository telemetryHistoryRepository,
            ResourceDeviceRepository resourceDeviceRepository,
            FaultEventRepository faultEventRepository,
            StationLayoutRegistry stationLayoutRegistry,
            RedisRealtimeStateStore redisRealtimeStateStore,
            ControlCommandRepository controlCommandRepository,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.stationRepository = stationRepository;
        this.telemetrySnapshotRepository = telemetrySnapshotRepository;
        this.telemetryHistoryRepository = telemetryHistoryRepository;
        this.resourceDeviceRepository = resourceDeviceRepository;
        this.faultEventRepository = faultEventRepository;
        this.stationLayoutRegistry = stationLayoutRegistry;
        this.redisRealtimeStateStore = redisRealtimeStateStore;
        this.controlCommandRepository = controlCommandRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * 查询场站列表。
     *
     * @return 场站列表
     */
    public List<StationStateResponse> getStations() {
        List<StationEntity> stations = stationRepository.findAll();
        List<StationStateResponse> responses = new ArrayList<>();
        for (StationEntity station : stations) {
            TelemetrySnapshotEntity snapshot = telemetrySnapshotRepository.findByStationId(station.getStationId()).orElse(null);
            responses.add(toStationState(station, snapshot));
        }
        return responses;
    }

    /**
     * 查询场站详情。
     *
     * @param stationId 场站标识
     * @return 场站详情
     */
    public StationDetailResponse getStationDetail(String stationId) {
        StationEntity station = stationRepository.findByStationId(stationId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "STATION_NOT_FOUND", "场站不存在", java.util.Map.of("stationId", stationId)));
        TelemetrySnapshotEntity snapshot = telemetrySnapshotRepository.findByStationId(stationId).orElse(null);
        List<ResourceDeviceEntity> devices = resourceDeviceRepository.findByStationId(stationId);
        List<TelemetryHistoryEntity> history = telemetryHistoryRepository.findByStationIdOrderByReceivedTimestampDesc(stationId, PageRequest.of(0, 8));
        List<FaultEventEntity> events = faultEventRepository.findAllByOrderByOccurredAtDesc(PageRequest.of(0, 6)).stream()
                .filter(event -> stationId.equals(event.getStationId()))
                .limit(4)
                .toList();

        List<ResourceMetricResponse> metrics = buildMetrics(station, snapshot);
        List<ResourceDeviceStatusResponse> deviceStatuses = devices.stream()
                .map(device -> new ResourceDeviceStatusResponse(
                        device.getDeviceId(),
                        device.getDeviceName(),
                        mapDeviceStatus(device.getStatus()),
                        snapshot == null ? "--" : String.format("%.1f kW", snapshot.getActivePower()),
                        "当前设备参与 " + station.getStationName() + " 功率调节。"
                ))
                .toList();

        List<ResourceControlActionResponse> controlActions = List.of(
                new ResourceControlActionResponse(stationId + "-action-01", "功率下发", "可执行", "用于比赛演示的场景切换和功率约束动作。"),
                new ResourceControlActionResponse(stationId + "-action-02", "链路诊断", "可执行", "触发场站通信质量复核。"),
                new ResourceControlActionResponse(stationId + "-action-03", "本地保守模式", stationId.equals("grid-link-00") ? "已锁定" : "可执行", "弱信号失联时进入本地自治兜底策略。")
        );

        List<TelemetryHistoryEntity> orderedHistory = history.stream()
                .sorted((left, right) -> left.getReceivedTimestamp().compareTo(right.getReceivedTimestamp()))
                .toList();
        List<ControlCommandEntity> executedCommands = loadExecutedCommands(stationId, orderedHistory);
        List<ResourceTelemetryPointResponse> powerTrend = orderedHistory.stream()
                .map(item -> new ResourceTelemetryPointResponse(
                        LABEL_FORMATTER.format(item.getReceivedTimestamp()),
                        item.getActivePower(),
                        findCommandTarget(item.getReceivedTimestamp(), executedCommands)
                ))
                .toList();

        List<ResourceQualityPointResponse> qualityTrend = history.stream()
                .sorted((left, right) -> left.getReceivedTimestamp().compareTo(right.getReceivedTimestamp()))
                .map(item -> new ResourceQualityPointResponse(
                        LABEL_FORMATTER.format(item.getReceivedTimestamp()),
                        item.getLatency(),
                        item.getPacketLossRate()
                ))
                .toList();

        return new StationDetailResponse(
                stationId,
                buildOverviewText(station, snapshot),
                metrics,
                deviceStatuses,
                controlActions,
                powerTrend,
                qualityTrend,
                events.stream().map(this::toEventResponse).toList()
        );
    }

    /**
     * 查询趋势时间窗内已执行的真实有功控制指令。
     *
     * @param stationId 场站标识
     * @param orderedHistory 按接收时间升序排列的遥测
     * @return 已执行控制指令
     */
    private List<ControlCommandEntity> loadExecutedCommands(
            String stationId,
            List<TelemetryHistoryEntity> orderedHistory
    ) {
        if (orderedHistory.isEmpty()) {
            return List.of();
        }
        return controlCommandRepository.findByStationIdAndCommandTypeAndStatusAndSendTimeBetweenOrderBySendTimeAsc(
                stationId,
                ACTIVE_POWER_COMMAND_TYPE,
                DispatchCommandStatus.EXECUTED.name(),
                orderedHistory.getFirst().getReceivedTimestamp(),
                orderedHistory.getLast().getReceivedTimestamp()
        );
    }

    /**
     * 返回遥测时刻处于指令接收有效时间窗内的真实目标功率。
     *
     * @param telemetryTimestamp 遥测接收时间
     * @param commands 已执行控制指令
     * @return 目标功率；该时刻没有真实指令时返回 {@code null}
     */
    private Double findCommandTarget(
            OffsetDateTime telemetryTimestamp,
            List<ControlCommandEntity> commands
    ) {
        return commands.stream()
                .filter(command -> !telemetryTimestamp.isBefore(command.getSendTime()))
                .filter(command -> !telemetryTimestamp.isAfter(command.getExpireAt()))
                .reduce((previous, current) -> current)
                .map(this::readTargetPower)
                .orElse(null);
    }

    /**
     * 从真实控制指令载荷读取目标功率。
     *
     * @param command 控制指令
     * @return 有限目标功率；载荷不完整时返回 {@code null}
     */
    private Double readTargetPower(ControlCommandEntity command) {
        try {
            double targetPower = objectMapper.readTree(command.getCommandPayload()).path("targetPower").asDouble(Double.NaN);
            return Double.isFinite(targetPower) ? targetPower : null;
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            return null;
        }
    }

    /**
     * 查询总览摘要。
     *
     * @return 摘要信息
     */
    public DashboardSummaryResponse getDashboardSummary() {
        List<StationStateResponse> stations = getStations();
        double totalOutputPower = stations.stream().mapToDouble(StationStateResponse::activePower).sum();
        double adjustableCapacity = stations.stream()
                .filter(StationStateResponse::online)
                .mapToDouble(station -> Math.max(0, station.availableCapacity() - station.activePower()))
                .sum();
        int onlineStationCount = (int) stations.stream().filter(StationStateResponse::online).count();
        int alarmCount = (int) faultEventRepository.findAllByOrderByOccurredAtDesc(PageRequest.of(0, 20)).stream()
                .filter(event -> !"提示".equals(event.getLevel()))
                .count();
        DemoScenario activeScenario = redisRealtimeStateStore.getActiveScenario().orElse(DemoScenario.STABLE);

        return new DashboardSummaryResponse(
                totalOutputPower,
                adjustableCapacity,
                onlineStationCount,
                alarmCount,
                activeScenario.getControlMode(),
                activeScenario
        );
    }

    /**
     * 查询功率趋势。
     *
     * @param from 起始时间
     * @param to 截止时间
     * @param limit 限制数量
     * @return 趋势点
     */
    public List<PowerTrendPointResponse> getPowerTrend(OffsetDateTime from, OffsetDateTime to, int limit) {
        List<TelemetryHistoryEntity> history = telemetryHistoryRepository.findByReceivedTimestampBetweenOrderByReceivedTimestampAsc(
                from,
                to,
                PageRequest.of(0, limit)
        );
        return history.stream()
                .map(item -> new PowerTrendPointResponse(
                        LABEL_FORMATTER.format(item.getReceivedTimestamp()),
                        Math.max(item.getActivePower(), item.getAvailableCapacity() * 0.9),
                        item.getActivePower()
                ))
                .toList();
    }

    /**
     * 查询模拟器最近上报的逐场站真实功率遥测。
     *
     * <p>该查询不依赖实验运行标识，因此在未注入故障、故障注入、调度执行和故障恢复阶段
     * 都返回同一条连续时间轴，供实时注入页面持续绘制功率曲线。</p>
     *
     * @param from 起始时间
     * @param to 截止时间
     * @param limit 最大返回数量
     * @param bucketSeconds 按场站保留最新样本的聚合时间窗，0 表示不聚合
     * @return 按接收时间升序排列的真实遥测点
     */
    public List<ExperimentReplayPointResponse> getRealtimePowerTelemetry(
            OffsetDateTime from,
            OffsetDateTime to,
            int limit,
            int bucketSeconds
    ) {
        List<TelemetryHistoryEntity> history = new ArrayList<>(telemetryHistoryRepository
                .findByReceivedTimestampBetweenOrderByReceivedTimestampDesc(
                        from,
                        to,
                        PageRequest.of(0, Math.max(1, limit))
                ));
        history.sort(Comparator.comparing(TelemetryHistoryEntity::getReceivedTimestamp));
        return downsampleRealtimeTelemetry(history, bucketSeconds)
                .stream()
                .map(sample -> new ExperimentReplayPointResponse(
                        sample.getReceivedTimestamp(),
                        sample.getStationId(),
                        sample.getActivePower(),
                        sample.getAvailableCapacity(),
                        sample.getLatency(),
                        sample.getPacketLossRate(),
                        sample.getDataAge(),
                        sample.getLinkState().name(),
                        sample.isOnline()
                ))
                .toList();
    }

    /**
     * 以场站和时间窗为粒度保留最后一个遥测样本。
     *
     * <p>功率曲线本身按固定时间窗展示，因此无需向前端传输同一场站的高频重复点。</p>
     *
     * @param history 已按接收时间升序排列的历史数据
     * @param bucketSeconds 时间窗秒数，0 表示不聚合
     * @return 聚合后并按接收时间升序排列的遥测数据
     */
    private List<TelemetryHistoryEntity> downsampleRealtimeTelemetry(
            List<TelemetryHistoryEntity> history,
            int bucketSeconds
    ) {
        if (bucketSeconds <= 0 || history.isEmpty()) {
            return history;
        }
        Map<String, TelemetryHistoryEntity> latestSamples = new LinkedHashMap<>();
        for (TelemetryHistoryEntity sample : history) {
            long bucketEpochSecond = Math.floorDiv(sample.getReceivedTimestamp().toInstant().getEpochSecond(), bucketSeconds)
                    * (long) bucketSeconds;
            latestSamples.put(sample.getStationId() + '|' + bucketEpochSecond, sample);
        }
        return latestSamples.values().stream()
                .sorted(Comparator.comparing(TelemetryHistoryEntity::getReceivedTimestamp))
                .toList();
    }

    /**
     * 按遥测真实接收时间聚合模拟器发电与用电功率。
     *
     * @param from 起始时间
     * @param to 截止时间
     * @param limit 最大返回点数
     * @return 功率流趋势点
     */
    public List<PowerFlowTrendPointResponse> getPowerFlowTrend(OffsetDateTime from, OffsetDateTime to, int limit) {
        Map<String, ResourceType> resourceTypesByStation = new HashMap<>();
        for (StationEntity station : stationRepository.findAll()) {
            resourceTypesByStation.put(station.getStationId(), station.getResourceType());
        }

        Map<Long, Map<String, TelemetryHistoryEntity>> samplesByBucket = new LinkedHashMap<>();
        List<TelemetryHistoryEntity> history = telemetryHistoryRepository
                .findByReceivedTimestampBetweenOrderByReceivedTimestampAsc(
                        from,
                        to,
                        PageRequest.of(0, POWER_FLOW_TELEMETRY_LIMIT)
                );
        for (TelemetryHistoryEntity sample : history) {
            long bucketEpochSecond = Math.floorDiv(sample.getReceivedTimestamp().toInstant().getEpochSecond(), POWER_FLOW_BUCKET_SECONDS)
                    * POWER_FLOW_BUCKET_SECONDS;
            samplesByBucket
                    .computeIfAbsent(bucketEpochSecond, ignored -> new LinkedHashMap<>())
                    .put(sample.getStationId(), sample);
        }

        List<PowerFlowTrendPointResponse> points = new ArrayList<>();
        for (Map.Entry<Long, Map<String, TelemetryHistoryEntity>> bucket : samplesByBucket.entrySet()) {
            double generationPower = 0;
            double consumptionPower = 0;
            for (TelemetryHistoryEntity sample : bucket.getValue().values()) {
                ResourceType resourceType = resourceTypesByStation.get(sample.getStationId());
                if (resourceType == null) {
                    continue;
                }
                double activePower = sample.getActivePower();
                switch (resourceType) {
                    case PHOTOVOLTAIC, WIND, THERMAL -> generationPower += Math.max(activePower, 0);
                    case LOAD, CHARGING -> consumptionPower += Math.abs(activePower);
                    case GRID, STORAGE -> {
                        if (activePower >= 0) {
                            generationPower += activePower;
                        } else {
                            consumptionPower += Math.abs(activePower);
                        }
                    }
                }
            }
            points.add(new PowerFlowTrendPointResponse(
                    OffsetDateTime.ofInstant(Instant.ofEpochSecond(bucket.getKey()), ZoneOffset.UTC),
                    generationPower,
                    consumptionPower,
                    bucket.getValue().size()
            ));
        }

        int fromIndex = Math.max(0, points.size() - limit);
        return points.subList(fromIndex, points.size());
    }

    /**
     * 查询最近事件。
     *
     * @param limit 条数
     * @return 最近事件
     */
    public List<EventResponse> getLatestEvents(int limit) {
        return faultEventRepository.findAllByOrderByOccurredAtDesc(PageRequest.of(0, limit)).stream()
                .map(this::toEventResponse)
                .toList();
    }

    private StationStateResponse toStationState(StationEntity station, TelemetrySnapshotEntity snapshot) {
        if (snapshot == null) {
            return new StationStateResponse(
                    station.getStationId(),
                    station.getStationName(),
                    station.getResourceType(),
                    0,
                    0,
                    com.weaksignal.vpp.backend.domain.LinkState.S3,
                    0,
                    100,
                    0,
                    false,
                    new ScenePositionResponse(
                            stationLayoutRegistry.getPosition(station.getStationId()).x(),
                            stationLayoutRegistry.getPosition(station.getStationId()).y()
                    ),
                    0,
                    null,
                    null
            );
        }

        return new StationStateResponse(
                station.getStationId(),
                station.getStationName(),
                station.getResourceType(),
                snapshot.getActivePower(),
                snapshot.getAvailableCapacity(),
                snapshot.getLinkState(),
                snapshot.getLatency(),
                snapshot.getPacketLossRate(),
                snapshot.getDataAge(),
                snapshot.isOnline(),
                new ScenePositionResponse(
                        stationLayoutRegistry.getPosition(station.getStationId()).x(),
                        stationLayoutRegistry.getPosition(station.getStationId()).y()
                ),
                snapshot.getReceivedTimestamp() == null
                        ? snapshot.getDataAge()
                        : Math.max(0, java.time.Duration.between(snapshot.getReceivedTimestamp(), OffsetDateTime.now(clock)).toSeconds()),
                snapshot.getActiveFaultCode(),
                snapshot.getReceivedTimestamp()
        );
    }

    private List<ResourceMetricResponse> buildMetrics(StationEntity station, TelemetrySnapshotEntity snapshot) {
        if (snapshot == null) {
            return List.of(
                    new ResourceMetricResponse("在线状态", "离线", ""),
                    new ResourceMetricResponse("最近功率", 0, "kW"),
                    new ResourceMetricResponse("链路状态", "S3", "")
            );
        }

        List<ResourceMetricResponse> metrics = new ArrayList<>();
        metrics.add(new ResourceMetricResponse(powerMetricLabel(station.getResourceType(), snapshot.getActivePower()), Math.abs(snapshot.getActivePower()), "kW"));
        metrics.add(new ResourceMetricResponse("可用容量", snapshot.getAvailableCapacity(), "kW"));
        metrics.add(new ResourceMetricResponse("通信时延", snapshot.getLatency(), "ms"));
        metrics.add(new ResourceMetricResponse("丢包率", snapshot.getPacketLossRate(), "%"));
        metrics.add(new ResourceMetricResponse("数据时效", snapshot.getDataAge(), "s"));
        metrics.add(new ResourceMetricResponse("链路状态", snapshot.getLinkState().name(), ""));
        if (station.getResourceType() == ResourceType.STORAGE) {
            metrics.add(new ResourceMetricResponse("调节特性", "双向功率爬坡受控", ""));
        }
        if (station.getResourceType() == ResourceType.PHOTOVOLTAIC) {
            metrics.add(new ResourceMetricResponse("出力特性", "受日照平滑波动影响", ""));
        }
        return metrics;
    }

    /**
     * 返回资源功率指标名称。
     *
     * @param resourceType 资源类型
     * @param activePower 实时有功功率
     * @return 功率指标名称
     */
    private String powerMetricLabel(ResourceType resourceType, double activePower) {
        return switch (resourceType) {
            case PHOTOVOLTAIC, WIND, THERMAL -> "发电功率";
            case GRID -> activePower >= 0 ? "市电购电" : "市电上网";
            case STORAGE -> activePower >= 0 ? "储能放电" : "储能充电";
            case CHARGING -> "充电功率";
            case LOAD -> "用电功率";
        };
    }

    private String buildOverviewText(StationEntity station, TelemetrySnapshotEntity snapshot) {
        if (snapshot == null) {
            return station.getStationName() + " 当前尚未收到实时遥测，前端将显示离线占位信息。";
        }
        return station.getStationName() + " 当前功率为 " + String.format("%.1f", snapshot.getActivePower())
                + "kW，链路状态 " + snapshot.getLinkState().name()
                + "，通信时延 " + snapshot.getLatency() + "ms。";
    }

    private String mapDeviceStatus(String status) {
        return switch (status) {
            case "RUNNING" -> "运行";
            case "STANDBY" -> "待机";
            case "ALARM" -> "告警";
            default -> "离线";
        };
    }

    private EventResponse toEventResponse(FaultEventEntity event) {
        return new EventResponse(
                event.getEventId(),
                event.getTitle(),
                event.getDescription(),
                event.getLevel(),
                event.getOccurredAt().format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                event.getStationId()
        );
    }
}

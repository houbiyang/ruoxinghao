package com.weaksignal.vpp.backend.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weaksignal.vpp.backend.api.ApiException;
import com.weaksignal.vpp.backend.api.dto.ExperimentComparisonResponse;
import com.weaksignal.vpp.backend.api.dto.ExperimentMetricSnapshotResponse;
import com.weaksignal.vpp.backend.api.dto.ExperimentQualityMetricsResponse;
import com.weaksignal.vpp.backend.api.dto.ExperimentQualityTrendPointResponse;
import com.weaksignal.vpp.backend.api.dto.ExperimentReplayPointResponse;
import com.weaksignal.vpp.backend.api.dto.ExperimentRunResponse;
import com.weaksignal.vpp.backend.api.dto.ExperimentStateDistributionResponse;
import com.weaksignal.vpp.backend.api.dto.ExperimentStationQualityRankResponse;
import com.weaksignal.vpp.backend.api.dto.ExperimentSummaryResponse;
import com.weaksignal.vpp.backend.api.dto.ExperimentTimelinePointResponse;
import com.weaksignal.vpp.backend.domain.ExperimentScenarioDefinition;
import com.weaksignal.vpp.backend.domain.LinkState;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.ExperimentRunEntity;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.FaultEventEntity;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.LinkStatusLogEntity;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.TelemetryHistoryEntity;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.ControlCommandEntity;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.DispatchPlanEntity;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.ControlCommandRepository;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.DispatchPlanRepository;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.ExperimentRunRepository;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.FaultEventRepository;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.LinkStatusLogRepository;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.TelemetryHistoryRepository;
import com.weaksignal.vpp.backend.market.MarketSignalService;
import com.weaksignal.vpp.backend.market.MarketSignalSnapshot;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 实验结果分析服务。
 */
@Service
public class ExperimentAnalysisService {

    private static final int DEFAULT_BEFORE_WINDOW_SECONDS = 60;
    private static final int DEFAULT_QUERY_LIMIT = 600;
    private static final String LOAD_STATION_ID = "load-oasis-04";
    private static final String CHARGING_STATION_ID = "charge-corridor-05";
    private static final String GRID_STATION_ID = "grid-link-00";
    private static final String STORAGE_STATION_ID = "storage-hub-03";
    private static final List<String> DEFAULT_STATION_IDS = List.of(
            "pv-gobi-01",
            "wind-ridge-02",
            "thermal-plant-06",
            "grid-link-00",
            "storage-hub-03",
            "load-oasis-04",
            "charge-corridor-05"
    );

    private final ExperimentRunRepository experimentRunRepository;
    private final TelemetryHistoryRepository telemetryHistoryRepository;
    private final LinkStatusLogRepository linkStatusLogRepository;
    private final FaultEventRepository faultEventRepository;
    private final ControlCommandRepository controlCommandRepository;
    private final DispatchPlanRepository dispatchPlanRepository;
    private final MarketSignalService marketSignalService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    /**
     * 注入实验分析依赖。
     *
     * @param experimentRunRepository 实验运行仓储
     * @param telemetryHistoryRepository 遥测历史仓储
     * @param linkStatusLogRepository 链路状态日志仓储
     * @param faultEventRepository 故障事件仓储
     * @param controlCommandRepository 控制命令仓储
     * @param dispatchPlanRepository 调度计划仓储
     * @param marketSignalService 市场信号服务
     * @param objectMapper JSON 组件
     * @param clock 系统时钟
     */
    public ExperimentAnalysisService(
            ExperimentRunRepository experimentRunRepository,
            TelemetryHistoryRepository telemetryHistoryRepository,
            LinkStatusLogRepository linkStatusLogRepository,
            FaultEventRepository faultEventRepository,
            ControlCommandRepository controlCommandRepository,
            DispatchPlanRepository dispatchPlanRepository,
            MarketSignalService marketSignalService,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.experimentRunRepository = experimentRunRepository;
        this.telemetryHistoryRepository = telemetryHistoryRepository;
        this.linkStatusLogRepository = linkStatusLogRepository;
        this.faultEventRepository = faultEventRepository;
        this.controlCommandRepository = controlCommandRepository;
        this.dispatchPlanRepository = dispatchPlanRepository;
        this.marketSignalService = marketSignalService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * 查询实验摘要。
     *
     * @param runId 实验运行标识
     * @return 实验摘要
     */
    public ExperimentSummaryResponse getSummary(String runId) {
        ExperimentRunEntity run = findRun(runId);
        AnalysisWindow window = buildWindow(run);
        List<TelemetryHistoryEntity> duringSamples = loadCurrentExperimentTelemetry(runId, window.duringStart(), window.duringEnd(), DEFAULT_QUERY_LIMIT, window.targetStationIds());
        List<FaultEventEntity> events = loadEvents(window.duringStart(), window.duringEnd(), window.targetStationIds());
        ExperimentMetricSnapshotResponse snapshot = aggregate(duringSamples);
        JsonNode summaryNode = readResultSummary(run);
        JsonNode distributedControlNode = summaryNode.path("distributedControl");
        JsonNode telemetryValidationNode = summaryNode.path("postDispatchTelemetryValidation");
        String dispatchPlanId = textValue(summaryNode, "dispatchPlanId");
        JsonNode planAuditNode = readPlanAudit(dispatchPlanId);
        String controllerType = firstTextValue(summaryNode, "controllerType", distributedControlNode, "controllerType");
        if (controllerType == null) {
            controllerType = textValue(planAuditNode, "optimizerName");
        }
        Double plannedBalanceErrorKw = doubleValue(distributedControlNode, "plannedBalanceErrorKw");
        if (plannedBalanceErrorKw == null) {
            plannedBalanceErrorKw = doubleValue(planAuditNode, "plannedBalanceErrorKw");
        }
        Integer constraintViolationCount = integerValue(
                distributedControlNode.path("constraintEvidence"),
                "constraintViolationCount"
        );
        if (constraintViolationCount == null && planAuditNode.hasNonNull("optimizationStatus")) {
            constraintViolationCount = 0;
        }
        List<ControlCommandEntity> planCommands = dispatchPlanId == null
                ? List.of() : controlCommandRepository.findByDispatchPlanIdOrderByCreatedAtAsc(dispatchPlanId);
        int executedCommandCount = (int) planCommands.stream()
                .filter(command -> "EXECUTED".equals(command.getStatus()))
                .count();
        int failedCommandCount = (int) planCommands.stream()
                .filter(command -> "REJECTED".equals(command.getStatus())
                        || "FAILED".equals(command.getStatus())
                        || "TIMEOUT".equals(command.getStatus()))
                .count();
        int pendingCommandCount = planCommands.size() - executedCommandCount - failedCommandCount;

        return new ExperimentSummaryResponse(
                run.getRunId(),
                run.getScenarioCode(),
                resolveScenarioName(run.getScenarioCode()),
                run.getStatus(),
                run.getStartedAt(),
                run.getEndedAt(),
                Duration.between(run.getStartedAt(), window.duringEnd()).toSeconds(),
                window.targetStationIds(),
                snapshot.sampleCount(),
                events.size(),
                dominantState(snapshot.stateCounts()),
                textValue(summaryNode, "orchestrationStatus"),
                dateTimeValue(summaryNode, "orchestrationTriggeredAt"),
                dateTimeValue(summaryNode, "orchestrationCompletedAt"),
                textValue(summaryNode, "dispatchContextSnapshotId"),
                dispatchPlanId,
                textValue(summaryNode, "agentRunId"),
                textValue(summaryNode, "planStatus"),
                textValue(summaryNode, "executionBatchId"),
                booleanValue(summaryNode, "safetyGatePassed"),
                textValue(summaryNode, "failureReason"),
                textValue(summaryNode, "controlStrategy"),
                longValue(summaryNode, "randomSeed"),
                controllerType,
                firstTextValue(summaryNode, "convergenceStatus", distributedControlNode, "convergenceStatus"),
                integerValue(distributedControlNode, "iteration"),
                plannedBalanceErrorKw,
                doubleValue(telemetryValidationNode, "actualBalanceErrorKw"),
                longValue(distributedControlNode, "messageSentCount"),
                longValue(distributedControlNode, "messageLostCount"),
                constraintViolationCount,
                planCommands.size(),
                executedCommandCount,
                failedCommandCount,
                pendingCommandCount,
                objectValue(summaryNode, "localAutonomyEvidence")
        );
    }

    /**
     * 读取实验运行摘要中的结构化数据。
     *
     * @param run 实验运行实体
     * @return 结构化摘要节点，摘要缺失或无效时返回空对象
     */
    private JsonNode readResultSummary(ExperimentRunEntity run) {
        String resultSummary = run.getResultSummary();
        if (resultSummary == null || !resultSummary.startsWith("{")) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(resultSummary.split("；", 2)[0]);
        } catch (Exception exception) {
            return objectMapper.createObjectNode();
        }
    }

    /**
     * 读取调度计划创建时保存的求解审计数据。
     *
     * @param dispatchPlanId 调度计划标识
     * @return 计划审计节点，计划不存在或载荷无效时返回空对象
     */
    private JsonNode readPlanAudit(String dispatchPlanId) {
        if (dispatchPlanId == null) {
            return objectMapper.createObjectNode();
        }
        return dispatchPlanRepository.findByPlanId(dispatchPlanId)
                .map(DispatchPlanEntity::getPayloadJson)
                .filter(payload -> !payload.isBlank())
                .map(payload -> {
                    try {
                        return objectMapper.readTree(payload);
                    } catch (Exception exception) {
                        return objectMapper.createObjectNode();
                    }
                })
                .orElseGet(objectMapper::createObjectNode);
    }

    /**
     * 读取摘要文本字段。
     *
     * @param summaryNode 摘要节点
     * @param fieldName 字段名称
     * @return 非空文本值，不存在时返回 {@code null}
     */
    private String textValue(JsonNode summaryNode, String fieldName) {
        JsonNode fieldNode = summaryNode.path(fieldName);
        return fieldNode.isTextual() && !fieldNode.textValue().isBlank() ? fieldNode.textValue() : null;
    }

    /**
     * 读取摘要时间字段。
     *
     * @param summaryNode 摘要节点
     * @param fieldName 字段名称
     * @return 时间值，不存在时返回 {@code null}
     */
    private OffsetDateTime dateTimeValue(JsonNode summaryNode, String fieldName) {
        String value = textValue(summaryNode, fieldName);
        return value == null ? null : OffsetDateTime.parse(value);
    }

    /**
     * 读取摘要布尔字段。
     *
     * @param summaryNode 摘要节点
     * @param fieldName 字段名称
     * @return 布尔值，不存在时返回 {@code null}
     */
    private Boolean booleanValue(JsonNode summaryNode, String fieldName) {
        JsonNode fieldNode = summaryNode.path(fieldName);
        return fieldNode.isBoolean() ? fieldNode.booleanValue() : null;
    }

    private String firstTextValue(
            JsonNode primaryNode,
            String primaryField,
            JsonNode fallbackNode,
            String fallbackField
    ) {
        String primaryValue = textValue(primaryNode, primaryField);
        return primaryValue == null ? textValue(fallbackNode, fallbackField) : primaryValue;
    }

    private Long longValue(JsonNode summaryNode, String fieldName) {
        JsonNode fieldNode = summaryNode.path(fieldName);
        return fieldNode.isNumber() ? fieldNode.longValue() : null;
    }

    private Integer integerValue(JsonNode summaryNode, String fieldName) {
        JsonNode fieldNode = summaryNode.path(fieldName);
        return fieldNode.isNumber() ? fieldNode.intValue() : null;
    }

    private Double doubleValue(JsonNode summaryNode, String fieldName) {
        JsonNode fieldNode = summaryNode.path(fieldName);
        return fieldNode.isNumber() ? fieldNode.doubleValue() : null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> objectValue(JsonNode summaryNode, String fieldName) {
        JsonNode fieldNode = summaryNode.path(fieldName);
        return fieldNode.isObject() ? objectMapper.convertValue(fieldNode, Map.class) : Map.of();
    }

    /**
     * 查询实验前后指标对比。
     *
     * @param runId 实验运行标识
     * @return 实验前后指标对比
     */
    public ExperimentComparisonResponse getComparison(String runId) {
        ExperimentRunEntity run = findRun(runId);
        AnalysisWindow window = buildWindow(run);
        List<TelemetryHistoryEntity> beforeSamples = loadTelemetry(window.beforeStart(), window.beforeEnd(), DEFAULT_QUERY_LIMIT, window.targetStationIds());
        List<TelemetryHistoryEntity> duringSamples = loadCurrentExperimentTelemetry(runId, window.duringStart(), window.duringEnd(), DEFAULT_QUERY_LIMIT, window.targetStationIds());
        List<TelemetryHistoryEntity> beforeSystemSamples = loadTelemetry(window.beforeStart(), window.beforeEnd(), DEFAULT_QUERY_LIMIT, DEFAULT_STATION_IDS);
        List<TelemetryHistoryEntity> duringSystemSamples = loadCurrentExperimentTelemetry(runId, window.duringStart(), window.duringEnd(), DEFAULT_QUERY_LIMIT, DEFAULT_STATION_IDS);
        List<LinkStatusLogEntity> duringLogs = loadLinkLogs(window.duringStart(), window.duringEnd(), window.targetStationIds());
        List<ControlCommandEntity> beforeCommands = controlCommandRepository.findBySendTimeBetweenOrderBySendTimeAsc(window.beforeStart(), window.beforeEnd());
        List<ControlCommandEntity> duringCommands = controlCommandRepository.findBySendTimeBetweenOrderBySendTimeAsc(window.duringStart(), window.duringEnd());
        MarketSignalSnapshot marketSignalSnapshot = resolveLatestMarketSignal();
        WindowMetrics beforeMetrics = buildWindowMetrics(beforeSystemSamples, List.of(), beforeCommands, marketSignalSnapshot, window.beforeStart(), window.beforeEnd());
        WindowMetrics duringMetrics = buildWindowMetrics(duringSystemSamples, duringLogs, duringCommands, marketSignalSnapshot, window.duringStart(), window.duringEnd());

        return new ExperimentComparisonResponse(
                toRunResponse(run),
                window.targetStationIds(),
                aggregate(beforeSamples),
                aggregate(duringSamples),
                buildDerivedMetrics(beforeMetrics, duringMetrics)
        );
    }

    /**
     * 查询实验链路状态时间线。
     *
     * @param runId 实验运行标识
     * @return 链路状态时间线
     */
    public List<ExperimentTimelinePointResponse> getTimeline(String runId) {
        ExperimentRunEntity run = findRun(runId);
        AnalysisWindow window = buildWindow(run);
        List<TelemetryHistoryEntity> samples = loadCurrentExperimentTelemetry(runId, window.duringStart(), window.duringEnd(), DEFAULT_QUERY_LIMIT, window.targetStationIds());
        Map<String, String> eventTitleMap = loadEvents(window.duringStart(), window.duringEnd(), window.targetStationIds())
                .stream()
                .collect(Collectors.toMap(
                        event -> event.getStationId() + "@" + event.getOccurredAt().withNano(0),
                        FaultEventEntity::getTitle,
                        (first, second) -> first
                ));
        List<ExperimentTimelinePointResponse> points = new ArrayList<>(samples.stream()
                .map(sample -> new ExperimentTimelinePointResponse(
                        sample.getReceivedTimestamp(),
                        sample.getStationId(),
                        sample.getLinkState().name(),
                        sample.getLatency(),
                        round(sample.getPacketLossRate()),
                        sample.isOnline(),
                        eventTitleMap.getOrDefault(sample.getStationId() + "@" + sample.getReceivedTimestamp().withNano(0), "")
                ))
                .toList());
        points.addAll(loadLinkLogs(window.duringStart(), window.duringEnd(), window.targetStationIds())
                .stream()
                .map(log -> new ExperimentTimelinePointResponse(
                        log.getOccurredAt(),
                        log.getStationId(),
                        log.getCurrentState().name(),
                        0,
                        0,
                        log.getCurrentState() != LinkState.S3,
                        log.getReason()
                ))
                .toList());
        points.sort(Comparator.comparing(ExperimentTimelinePointResponse::timestamp));
        return points;
    }

    /**
     * 查询实验遥测回放点。
     *
     * @param runId 实验运行标识
     * @param stationId 场站标识
     * @param allStations 是否查询全系统场站
     * @param includeBaseline 是否包含实验开始前的基线数据
     * @param from 起始时间
     * @param to 截止时间
     * @param limit 返回数量
     * @return 遥测回放点
     */
    public List<ExperimentReplayPointResponse> getReplay(
            String runId,
            String stationId,
            boolean allStations,
            boolean includeBaseline,
            OffsetDateTime from,
            OffsetDateTime to,
            int limit
    ) {
        ExperimentRunEntity run = findRun(runId);
        AnalysisWindow window = buildWindow(run);
        OffsetDateTime queryFrom = from == null
                ? (includeBaseline ? window.beforeStart() : window.duringStart())
                : from;
        OffsetDateTime queryTo = to == null ? window.duringEnd() : to;
        List<String> stationIds = stationId == null || stationId.isBlank()
                ? (allStations ? DEFAULT_STATION_IDS : window.targetStationIds())
                : List.of(stationId);
        List<TelemetryHistoryEntity> samples;
        if (includeBaseline && queryFrom.isBefore(run.getStartedAt())) {
            OffsetDateTime baselineEnd = queryTo.isBefore(run.getStartedAt()) ? queryTo : run.getStartedAt();
            List<TelemetryHistoryEntity> baselineSamples = loadTelemetry(queryFrom, baselineEnd, limit, stationIds);
            int remainingLimit = Math.max(0, limit - baselineSamples.size());
            List<TelemetryHistoryEntity> experimentSamples = remainingLimit == 0 || !queryTo.isAfter(run.getStartedAt())
                    ? List.of()
                    : loadCurrentExperimentTelemetry(runId, run.getStartedAt(), queryTo, remainingLimit, stationIds);
            samples = new ArrayList<>(baselineSamples.size() + experimentSamples.size());
            samples.addAll(baselineSamples);
            samples.addAll(experimentSamples);
            samples.sort(Comparator.comparing(TelemetryHistoryEntity::getReceivedTimestamp));
        } else {
            samples = loadCurrentExperimentTelemetry(runId, queryFrom, queryTo, limit, stationIds);
        }
        return samples.stream()
                .map(this::toReplayPoint)
                .toList();
    }

    /**
     * 兼容仅查询目标场站的旧版回放调用。
     *
     * @param runId 实验运行标识
     * @param stationId 场站标识
     * @param from 起始时间
     * @param to 截止时间
     * @param limit 返回数量
     * @return 遥测回放点
     */
    public List<ExperimentReplayPointResponse> getReplay(
            String runId,
            String stationId,
            OffsetDateTime from,
            OffsetDateTime to,
            int limit
    ) {
        return getReplay(runId, stationId, false, false, from, to, limit);
    }

    /**
     * 查询实验通信质量指标。
     *
     * @param runId 实验运行标识
     * @return 通信质量指标
     */
    public ExperimentQualityMetricsResponse getQualityMetrics(String runId) {
        ExperimentRunEntity run = findRun(runId);
        AnalysisWindow window = buildWindow(run);
        List<TelemetryHistoryEntity> beforeSamples = loadTelemetry(window.beforeStart(), window.beforeEnd(), DEFAULT_QUERY_LIMIT, window.targetStationIds());
        List<TelemetryHistoryEntity> duringSamples = loadCurrentExperimentTelemetry(runId, window.duringStart(), window.duringEnd(), DEFAULT_QUERY_LIMIT, window.targetStationIds());
        ExperimentMetricSnapshotResponse duringSnapshot = aggregate(duringSamples);

        List<ExperimentQualityTrendPointResponse> trends = duringSamples.stream()
                .map(sample -> new ExperimentQualityTrendPointResponse(
                        sample.getReceivedTimestamp(),
                        sample.getStationId(),
                        sample.getLatency(),
                        round(sample.getPacketLossRate()),
                        sample.getDataAge()
                ))
                .toList();
        List<ExperimentStateDistributionResponse> distribution = duringSnapshot.stateCounts().entrySet().stream()
                .map(entry -> new ExperimentStateDistributionResponse(entry.getKey(), entry.getValue()))
                .toList();
        List<ExperimentStationQualityRankResponse> ranking = buildStationRanking(duringSamples);

        return new ExperimentQualityMetricsResponse(
                aggregate(beforeSamples),
                duringSnapshot,
                trends,
                distribution,
                ranking
        );
    }

    private ExperimentRunEntity findRun(String runId) {
        return experimentRunRepository.findByRunId(runId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "EXPERIMENT_RUN_NOT_FOUND",
                        "实验运行不存在",
                        Map.of("runId", runId)
                ));
    }

    private AnalysisWindow buildWindow(ExperimentRunEntity run) {
        OffsetDateTime duringEnd = run.getEndedAt() == null ? OffsetDateTime.now(clock) : run.getEndedAt();
        List<String> targetStationIds = resolveTargetStationIds(run);
        return new AnalysisWindow(
                run.getStartedAt().minusSeconds(DEFAULT_BEFORE_WINDOW_SECONDS),
                run.getStartedAt(),
                run.getStartedAt(),
                duringEnd,
                targetStationIds
        );
    }

    private List<TelemetryHistoryEntity> loadTelemetry(
            OffsetDateTime from,
            OffsetDateTime to,
            int limit,
            List<String> targetStationIds
    ) {
        if (targetStationIds.isEmpty()) {
            return List.of();
        }
        return telemetryHistoryRepository
                .findByStationIdInAndReceivedTimestampBetweenOrderByReceivedTimestampAsc(
                        targetStationIds,
                        from,
                        to,
                        PageRequest.of(0, Math.max(1, limit))
                );
    }

    /**
     * 查询当前实验关联的遥测。新数据始终通过独立关联字段隔离；仅当历史记录尚未迁移时，
     * 才退回至原有时间窗口查询以保持结果页可用。
     *
     * @param runId 实验运行标识
     * @param from 起始时间
     * @param to 截止时间
     * @param limit 最大返回数量
     * @param stationIds 目标场站标识集合
     * @return 当前实验遥测
     */
    private List<TelemetryHistoryEntity> loadCurrentExperimentTelemetry(
            String runId,
            OffsetDateTime from,
            OffsetDateTime to,
            int limit,
            List<String> stationIds
    ) {
        PageRequest pageable = PageRequest.of(0, Math.max(1, limit));
        if (!telemetryHistoryRepository.existsByExperimentRunId(runId)) {
            return loadTelemetry(from, to, limit, stationIds);
        }
        return telemetryHistoryRepository
                .findByExperimentRunIdAndStationIdInAndReceivedTimestampBetweenOrderByReceivedTimestampAsc(
                        runId, stationIds, from, to, pageable
                );
    }

    private List<LinkStatusLogEntity> loadLinkLogs(OffsetDateTime from, OffsetDateTime to, List<String> targetStationIds) {
        Set<String> targetSet = Set.copyOf(targetStationIds);
        return linkStatusLogRepository
                .findByOccurredAtBetweenOrderByOccurredAtAsc(from, to, PageRequest.of(0, DEFAULT_QUERY_LIMIT))
                .stream()
                .filter(log -> targetSet.contains(log.getStationId()))
                .toList();
    }

    private List<FaultEventEntity> loadEvents(OffsetDateTime from, OffsetDateTime to, List<String> targetStationIds) {
        Set<String> targetSet = Set.copyOf(targetStationIds);
        return faultEventRepository
                .findByOccurredAtBetweenOrderByOccurredAtAsc(from, to, PageRequest.of(0, DEFAULT_QUERY_LIMIT))
                .stream()
                .filter(event -> targetSet.contains(event.getStationId()))
                .toList();
    }

    private ExperimentMetricSnapshotResponse aggregate(List<TelemetryHistoryEntity> samples) {
        Map<String, Long> stateCounts = new LinkedHashMap<>();
        for (LinkState state : LinkState.values()) {
            stateCounts.put(state.name(), 0L);
        }
        if (samples.isEmpty()) {
            return new ExperimentMetricSnapshotResponse(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, stateCounts);
        }

        double totalPower = 0;
        double totalLatency = 0;
        double totalPacketLossRate = 0;
        double totalDataAge = 0;
        double maxPower = Double.NEGATIVE_INFINITY;
        double minPower = Double.POSITIVE_INFINITY;
        int maxLatency = 0;
        double maxPacketLossRate = 0;
        long maxDataAge = 0;
        long onlineCount = 0;
        for (TelemetryHistoryEntity sample : samples) {
            totalPower += sample.getActivePower();
            totalLatency += sample.getLatency();
            totalPacketLossRate += sample.getPacketLossRate();
            totalDataAge += sample.getDataAge();
            maxPower = Math.max(maxPower, sample.getActivePower());
            minPower = Math.min(minPower, sample.getActivePower());
            maxLatency = Math.max(maxLatency, sample.getLatency());
            maxPacketLossRate = Math.max(maxPacketLossRate, sample.getPacketLossRate());
            maxDataAge = Math.max(maxDataAge, sample.getDataAge());
            if (sample.isOnline()) {
                onlineCount++;
            }
            stateCounts.merge(sample.getLinkState().name(), 1L, Long::sum);
        }
        double size = samples.size();
        return new ExperimentMetricSnapshotResponse(
                samples.size(),
                round(totalPower / size),
                round(maxPower),
                round(minPower),
                round(totalLatency / size),
                maxLatency,
                round(totalPacketLossRate / size),
                round(maxPacketLossRate),
                round(totalDataAge / size),
                maxDataAge,
                round(onlineCount * 100.0 / size),
                stateCounts
        );
    }

    private List<ExperimentComparisonResponse.DerivedMetricResponse> buildDerivedMetrics(
            WindowMetrics beforeMetrics,
            WindowMetrics duringMetrics
    ) {
        return List.of(
                derivedMetric("peak-load", "峰值负荷", beforeMetrics.peakLoad(), duringMetrics.peakLoad(), "kW", false),
                derivedMetric("grid-purchase-cost", "购电成本", beforeMetrics.gridPurchaseCost(), duringMetrics.gridPurchaseCost(), "元", false),
                derivedMetric("curtailment", "弃光弃风", beforeMetrics.curtailment(), duringMetrics.curtailment(), "kWh", false),
                derivedMetric("soc-swing", "SOC波动", beforeMetrics.socSwing(), duringMetrics.socSwing(), "%", false),
                derivedMetric("recovery-time", "链路恢复时间", beforeMetrics.recoveryTime(), duringMetrics.recoveryTime(), "s", false),
                derivedMetric("execution-success-rate", "执行成功率", beforeMetrics.executionSuccessRate(), duringMetrics.executionSuccessRate(), "%", true)
        );
    }

    private ExperimentComparisonResponse.DerivedMetricResponse derivedMetric(
            String metricCode,
            String metricName,
            BigDecimal beforeValue,
            BigDecimal duringValue,
            String unit,
            boolean higherIsBetter
    ) {
        if (beforeValue == null || duringValue == null) {
            return new ExperimentComparisonResponse.DerivedMetricResponse(
                    metricCode,
                    metricName,
                    beforeValue,
                    duringValue,
                    unit,
                    null,
                    null,
                    higherIsBetter,
                    false
            );
        }
        BigDecimal improvementValue = higherIsBetter
                ? duringValue.subtract(beforeValue)
                : beforeValue.subtract(duringValue);
        BigDecimal improvementRate = beforeValue.compareTo(BigDecimal.ZERO) == 0
                ? null
                : improvementValue.multiply(BigDecimal.valueOf(100))
                .divide(beforeValue.abs(), 2, RoundingMode.HALF_UP);
        return new ExperimentComparisonResponse.DerivedMetricResponse(
                metricCode,
                metricName,
                beforeValue,
                duringValue,
                unit,
                scale(improvementValue, 2),
                improvementRate,
                higherIsBetter,
                true
        );
    }

    private WindowMetrics buildWindowMetrics(
            List<TelemetryHistoryEntity> samples,
            List<LinkStatusLogEntity> linkLogs,
            List<ControlCommandEntity> commands,
            MarketSignalSnapshot marketSignalSnapshot,
            OffsetDateTime windowStart,
            OffsetDateTime windowEnd
    ) {
        return new WindowMetrics(
                calculatePeakLoad(samples),
                calculateGridPurchaseCost(samples, marketSignalSnapshot, windowStart, windowEnd),
                calculateCurtailment(samples, windowStart, windowEnd),
                calculateSocSwing(samples),
                calculateRecoveryTime(samples, linkLogs),
                calculateExecutionSuccessRate(commands)
        );
    }

    private BigDecimal calculatePeakLoad(List<TelemetryHistoryEntity> samples) {
        Map<OffsetDateTime, Double> loadSumByTimestamp = new LinkedHashMap<>();
        samples.stream()
                .filter(sample -> LOAD_STATION_ID.equals(sample.getStationId()) || CHARGING_STATION_ID.equals(sample.getStationId()))
                .forEach(sample -> loadSumByTimestamp.merge(sample.getReceivedTimestamp(), sample.getActivePower(), Double::sum));
        return loadSumByTimestamp.values().stream()
                .max(Double::compareTo)
                .map(value -> scale(BigDecimal.valueOf(value), 2))
                .orElse(null);
    }

    private BigDecimal calculateGridPurchaseCost(
            List<TelemetryHistoryEntity> samples,
            MarketSignalSnapshot marketSignalSnapshot,
            OffsetDateTime windowStart,
            OffsetDateTime windowEnd
    ) {
        if (marketSignalSnapshot == null || marketSignalSnapshot.realTimePrice() == null) {
            return null;
        }
        List<TelemetryHistoryEntity> gridSamples = samples.stream()
                .filter(sample -> GRID_STATION_ID.equals(sample.getStationId()))
                .toList();
        if (gridSamples.isEmpty()) {
            return null;
        }
        double averageImportPower = gridSamples.stream()
                .mapToDouble(sample -> Math.max(0.0, sample.getActivePower()))
                .average()
                .orElse(0.0);
        BigDecimal durationHours = durationHours(windowStart, windowEnd);
        return scale(BigDecimal.valueOf(averageImportPower).multiply(durationHours).multiply(marketSignalSnapshot.realTimePrice()), 2);
    }

    private BigDecimal calculateCurtailment(
            List<TelemetryHistoryEntity> samples,
            OffsetDateTime windowStart,
            OffsetDateTime windowEnd
    ) {
        Map<String, List<TelemetryHistoryEntity>> renewableSamples = samples.stream()
                .filter(sample -> sample.getStationId().startsWith("pv-") || sample.getStationId().startsWith("wind-"))
                .collect(Collectors.groupingBy(TelemetryHistoryEntity::getStationId));
        if (renewableSamples.isEmpty()) {
            return null;
        }
        BigDecimal durationHours = durationHours(windowStart, windowEnd);
        BigDecimal totalCurtailment = renewableSamples.values().stream()
                .map(stationSamples -> stationSamples.stream()
                        .map(sample -> BigDecimal.valueOf(Math.max(0.0, sample.getAvailableCapacity() - sample.getActivePower())))
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(stationSamples.size()), 6, RoundingMode.HALF_UP)
                        .multiply(durationHours))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return scale(totalCurtailment, 2);
    }

    private BigDecimal calculateSocSwing(List<TelemetryHistoryEntity> samples) {
        List<Double> socValues = samples.stream()
                .filter(sample -> STORAGE_STATION_ID.equals(sample.getStationId()))
                .map(this::readSoc)
                .flatMap(Optional::stream)
                .toList();
        if (socValues.isEmpty()) {
            return null;
        }
        double minSoc = socValues.stream().min(Double::compareTo).orElse(0.0);
        double maxSoc = socValues.stream().max(Double::compareTo).orElse(0.0);
        return scale(BigDecimal.valueOf(maxSoc - minSoc), 2);
    }

    private BigDecimal calculateRecoveryTime(List<TelemetryHistoryEntity> samples, List<LinkStatusLogEntity> linkLogs) {
        Optional<OffsetDateTime> degradedAt = samples.stream()
                .filter(sample -> sample.getLinkState() != LinkState.S0)
                .map(TelemetryHistoryEntity::getReceivedTimestamp)
                .findFirst();
        if (degradedAt.isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        Optional<OffsetDateTime> recoveredAt = linkLogs.stream()
                .filter(log -> log.getOccurredAt().isAfter(degradedAt.get()))
                .filter(log -> log.getCurrentState() == LinkState.S0 || log.getCurrentState() == LinkState.S4)
                .map(LinkStatusLogEntity::getOccurredAt)
                .findFirst()
                .or(() -> samples.stream()
                        .filter(sample -> sample.getReceivedTimestamp().isAfter(degradedAt.get()))
                        .filter(sample -> sample.getLinkState() == LinkState.S0 || sample.getLinkState() == LinkState.S4)
                        .map(TelemetryHistoryEntity::getReceivedTimestamp)
                        .findFirst());
        if (recoveredAt.isEmpty()) {
            return null;
        }
        return scale(BigDecimal.valueOf(Duration.between(degradedAt.get(), recoveredAt.get()).toSeconds()), 2);
    }

    private BigDecimal calculateExecutionSuccessRate(List<ControlCommandEntity> commands) {
        if (commands.isEmpty()) {
            return null;
        }
        long succeededCount = commands.stream()
                .filter(command -> "EXECUTED".equals(command.getStatus()))
                .count();
        return scale(BigDecimal.valueOf(succeededCount * 100.0 / commands.size()), 2);
    }

    private MarketSignalSnapshot resolveLatestMarketSignal() {
        try {
            return marketSignalService.getLatestActiveSnapshot();
        } catch (ApiException exception) {
            if (exception.getStatus() == HttpStatus.NOT_FOUND || exception.getStatus() == HttpStatus.BAD_REQUEST) {
                return null;
            }
            throw exception;
        }
    }

    private Optional<Double> readSoc(TelemetryHistoryEntity sample) {
        try {
            JsonNode rootNode = objectMapper.readTree(sample.getPayloadJson());
            JsonNode socNode = rootNode.path("metrics").path("soc");
            if (socNode.isNumber()) {
                return Optional.of(socNode.asDouble());
            }
        } catch (Exception exception) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    private BigDecimal durationHours(OffsetDateTime start, OffsetDateTime end) {
        long seconds = Math.max(0, Duration.between(start, end).getSeconds());
        return BigDecimal.valueOf(seconds).divide(BigDecimal.valueOf(3600L), 6, RoundingMode.HALF_UP);
    }

    private BigDecimal scale(BigDecimal value, int scale) {
        return value.setScale(scale, RoundingMode.HALF_UP);
    }

    private List<ExperimentStationQualityRankResponse> buildStationRanking(List<TelemetryHistoryEntity> samples) {
        return samples.stream()
                .collect(Collectors.groupingBy(TelemetryHistoryEntity::getStationId))
                .entrySet()
                .stream()
                .map(entry -> {
                    ExperimentMetricSnapshotResponse snapshot = aggregate(entry.getValue());
                    double qualityScore = Math.max(
                            0,
                            100 - snapshot.averageLatency() * 0.25
                                    - snapshot.averagePacketLossRate() * 5
                                    - snapshot.averageDataAge() * 1.5
                                    + snapshot.onlineRate() * 0.1
                    );
                    return new ExperimentStationQualityRankResponse(
                            entry.getKey(),
                            snapshot.averageLatency(),
                            snapshot.averagePacketLossRate(),
                            snapshot.averageDataAge(),
                            snapshot.onlineRate(),
                            round(Math.min(100, qualityScore))
                    );
                })
                .sorted(Comparator.comparing(ExperimentStationQualityRankResponse::qualityScore).reversed())
                .toList();
    }

    private ExperimentReplayPointResponse toReplayPoint(TelemetryHistoryEntity sample) {
        return new ExperimentReplayPointResponse(
                sample.getReceivedTimestamp(),
                sample.getStationId(),
                round(sample.getActivePower()),
                round(sample.getAvailableCapacity()),
                sample.getLatency(),
                round(sample.getPacketLossRate()),
                sample.getDataAge(),
                sample.getLinkState().name(),
                sample.isOnline()
        );
    }

    private ExperimentRunResponse toRunResponse(ExperimentRunEntity entity) {
        return new ExperimentRunResponse(
                entity.getRunId(),
                entity.getScenarioCode(),
                resolveScenarioName(entity.getScenarioCode()),
                entity.getStatus(),
                entity.getStartedAt(),
                entity.getEndedAt(),
                entity.getResultSummary()
        );
    }

    private String resolveScenarioName(String scenarioCode) {
        try {
            return ExperimentScenarioDefinition.fromCode(scenarioCode).getScenarioName();
        } catch (IllegalArgumentException exception) {
            return scenarioCode;
        }
    }

    private List<String> resolveTargetStationIds(ExperimentRunEntity run) {
        String resultSummary = run.getResultSummary();
        if (resultSummary != null && resultSummary.startsWith("{")) {
            String jsonPart = resultSummary.split("；", 2)[0];
            try {
                Map<String, Object> summary = objectMapper.readValue(jsonPart, new TypeReference<>() {
                });
                Object targetStationIds = summary.get("targetStationIds");
                if (targetStationIds instanceof List<?> targetList && !targetList.isEmpty()) {
                    return targetList.stream()
                            .filter(String.class::isInstance)
                            .map(String.class::cast)
                            .toList();
                }
            } catch (Exception exception) {
                return resolveDefinitionTargets(run.getScenarioCode());
            }
        }
        return resolveDefinitionTargets(run.getScenarioCode());
    }

    private List<String> resolveDefinitionTargets(String scenarioCode) {
        try {
            return ExperimentScenarioDefinition.fromCode(scenarioCode).getTargetStationIds();
        } catch (IllegalArgumentException exception) {
            return DEFAULT_STATION_IDS;
        }
    }

    private String dominantState(Map<String, Long> stateCounts) {
        EnumMap<LinkState, Long> normalizedCounts = new EnumMap<>(LinkState.class);
        for (LinkState state : LinkState.values()) {
            normalizedCounts.put(state, stateCounts.getOrDefault(state.name(), 0L));
        }
        return normalizedCounts.entrySet()
                .stream()
                .max(Map.Entry.comparingByValue())
                .map(entry -> entry.getKey().name())
                .orElse(LinkState.S0.name());
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private record WindowMetrics(
            BigDecimal peakLoad,
            BigDecimal gridPurchaseCost,
            BigDecimal curtailment,
            BigDecimal socSwing,
            BigDecimal recoveryTime,
            BigDecimal executionSuccessRate
    ) {
    }

    private record AnalysisWindow(
            OffsetDateTime beforeStart,
            OffsetDateTime beforeEnd,
            OffsetDateTime duringStart,
            OffsetDateTime duringEnd,
            List<String> targetStationIds
    ) {
    }
}

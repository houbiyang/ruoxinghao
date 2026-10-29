package com.weaksignal.vpp.backend.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weaksignal.vpp.backend.api.ApiException;
import com.weaksignal.vpp.backend.api.dto.ExperimentRunListItemResponse;
import com.weaksignal.vpp.backend.api.dto.ExperimentRunResponse;
import com.weaksignal.vpp.backend.api.dto.ExperimentDeleteResponse;
import com.weaksignal.vpp.backend.api.dto.ExperimentScenarioResponse;
import com.weaksignal.vpp.backend.api.dto.ExperimentStartRequest;
import com.weaksignal.vpp.backend.api.dto.ExperimentStartResponse;
import com.weaksignal.vpp.backend.api.dto.ExperimentStopResponse;
import com.weaksignal.vpp.backend.domain.DemoScenario;
import com.weaksignal.vpp.backend.domain.ExperimentScenarioDefinition;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.ExperimentRunEntity;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.StationEntity;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.ExperimentRunRepository;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.StationRepository;
import com.weaksignal.vpp.backend.infrastructure.redis.RedisRealtimeStateStore;
import com.weaksignal.vpp.backend.mqtt.MqttGateway;
import com.weaksignal.vpp.backend.mqtt.dto.ScenarioApplyCommandMessage;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 故障注入实验服务。
 */
@Service
public class ExperimentService {

    private static final String INJECTING_STATUS = "INJECTING";
    private static final String EXPERIMENT_PREFIX = "T";
    private static final String ORCHESTRATION_STATUS_PENDING = "PENDING";

    private final ExperimentRunRepository experimentRunRepository;
    private final StationRepository stationRepository;
    private final RedisRealtimeStateStore redisRealtimeStateStore;
    private final MqttGateway mqttGateway;
    private final ExperimentFaultObservationService experimentFaultObservationService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    /**
     * 注入实验服务依赖。
     *
     * @param experimentRunRepository 实验运行仓储
     * @param stationRepository 场站仓储
     * @param redisRealtimeStateStore Redis 状态存储
     * @param mqttGateway MQTT 网关
     * @param experimentFaultObservationService 实验故障观测服务
     * @param objectMapper JSON 组件
     * @param clock 系统时钟
     */
    @Autowired
    public ExperimentService(
            ExperimentRunRepository experimentRunRepository,
            StationRepository stationRepository,
            RedisRealtimeStateStore redisRealtimeStateStore,
            MqttGateway mqttGateway,
            ExperimentFaultObservationService experimentFaultObservationService,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.experimentRunRepository = experimentRunRepository;
        this.stationRepository = stationRepository;
        this.redisRealtimeStateStore = redisRealtimeStateStore;
        this.mqttGateway = mqttGateway;
        this.experimentFaultObservationService = experimentFaultObservationService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * 兼容旧测试和离线工具的实验服务构造方法。
     *
     * @param experimentRunRepository 实验运行仓储
     * @param redisRealtimeStateStore Redis 状态存储
     * @param mqttGateway MQTT 网关
     * @param objectMapper JSON 组件
     * @param clock 系统时钟
     */
    public ExperimentService(
            ExperimentRunRepository experimentRunRepository,
            RedisRealtimeStateStore redisRealtimeStateStore,
            MqttGateway mqttGateway,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this(experimentRunRepository, null, redisRealtimeStateStore, mqttGateway, null, objectMapper, clock);
    }

    /**
     * 查询实验场景清单。
     *
     * @return 实验场景清单
     */
    public List<ExperimentScenarioResponse> listScenarios() {
        return Arrays.stream(ExperimentScenarioDefinition.values())
                .filter(definition -> !"LEGACY".equals(definition.getCategory()))
                .map(this::toScenarioResponse)
                .toList();
    }

    /**
     * 查询最近实验运行。
     *
     * @param limit 返回数量
     * @return 实验运行列表
     */
    public List<ExperimentRunResponse> listLatestRuns(int limit) {
        return experimentRunRepository.findAllByOrderByStartedAtDesc(PageRequest.of(0, limit))
                .stream()
                .map(this::toRunResponse)
                .toList();
    }

    /**
     * 查询最近实验运行的轻量列表。
     *
     * <p>历史选择器不需要解析结果摘要，单独提供该列表可避免传输大型 JSON 字段。</p>
     *
     * @param limit 返回数量
     * @return 不包含结果摘要的实验运行列表
     */
    public List<ExperimentRunListItemResponse> listLatestRunItems(int limit) {
        return experimentRunRepository.findAllByOrderByStartedAtDesc(PageRequest.of(0, limit))
                .stream()
                .map(this::toRunListItemResponse)
                .toList();
    }

    /**
     * 按运行标识查询实验。
     *
     * @param runId 实验运行标识
     * @return 实验运行记录
     */
    public ExperimentRunResponse getRun(String runId) {
        return experimentRunRepository.findByRunId(runId)
                .map(this::toRunResponse)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "EXPERIMENT_RUN_NOT_FOUND", "实验运行记录不存在", null));
    }

    /**
     * 查询当前运行实验。
     *
     * @return 当前运行实验
     */
    public ExperimentRunResponse getCurrentRun() {
        return experimentRunRepository
                .findFirstByStatusInOrderByStartedAtDesc(
                        List.of("STARTING", INJECTING_STATUS, "FAULT_OBSERVED", "ORCHESTRATING", "RECOVERING")
                )
                .map(this::toRunResponse)
                .orElse(null);
    }

    /**
     * 启动实验。
     *
     * @param scenarioCode 场景编码
     * @param request 启动请求
     * @return 启动结果
     */
    @Transactional
    public ExperimentStartResponse startExperiment(String scenarioCode, ExperimentStartRequest request) {
        ExperimentScenarioDefinition definition = resolveDefinition(scenarioCode);
        experimentRunRepository
                .findFirstByStatusInOrderByStartedAtDesc(List.of("STARTING", INJECTING_STATUS, "FAULT_OBSERVED", "ORCHESTRATING", "RECOVERING"))
                .ifPresent(existing -> {
                    throw new ApiException(
                            HttpStatus.CONFLICT,
                            "EXPERIMENT_ALREADY_RUNNING",
                            "已有实验正在运行",
                            Map.of("runId", existing.getRunId(), "scenarioCode", existing.getScenarioCode())
                    );
                });

        OffsetDateTime now = OffsetDateTime.now(clock);
        String runId = UUID.randomUUID().toString();
        List<String> targetStationIds = request.targetStationIds() == null || request.targetStationIds().isEmpty()
                ? definition.getTargetStationIds()
                : request.targetStationIds();
        int durationSeconds = request.durationSeconds() == null ? definition.getDurationSeconds() : request.durationSeconds();
        boolean automaticRecovery = request.automaticRecovery() == null || request.automaticRecovery();
        Map<String, Double> faultParameters = resolveFaultParameters(definition, request.faultParameters());
        String controlStrategy = resolveControlStrategy(request.controlStrategy());
        validateTargetStations(definition, targetStationIds);

        ExperimentRunEntity runEntity = new ExperimentRunEntity();
        runEntity.setRunId(runId);
        runEntity.setScenarioCode(definition.getScenarioCode());
        runEntity.setStatus(INJECTING_STATUS);
        runEntity.setStartedAt(now);
        runEntity.setResultSummary(buildSummary(definition, targetStationIds, faultParameters, durationSeconds,
                automaticRecovery, controlStrategy, request.randomSeed(), request.remark(), now));
        experimentRunRepository.save(runEntity);

        redisRealtimeStateStore.saveActiveScenario(definition.getDemoScenario());
        mqttGateway.publishGlobalScenario(new ScenarioApplyCommandMessage(
                UUID.randomUUID().toString(),
                definition.getDemoScenario(),
                now,
                "backend-experiment",
                "START_EXPERIMENT",
                definition.getScenarioCode(),
                runId,
                targetStationIds,
                faultParameters,
                request.severityLevel() == null ? 3 : request.severityLevel(),
                durationSeconds,
                false,
                controlStrategy,
                request.randomSeed()
        ));

        return new ExperimentStartResponse(runId, definition.getScenarioCode(), INJECTING_STATUS, now);
    }

    /**
     * 停止实验。
     *
     * @param runId 运行标识
     * @return 停止结果
     */
    @Transactional
    public ExperimentStopResponse stopExperiment(String runId) {
        ExperimentRunEntity runEntity = experimentRunRepository.findByRunId(runId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "EXPERIMENT_RUN_NOT_FOUND",
                        "实验运行不存在",
                        Map.of("runId", runId)
                ));
        if ("COMPLETED".equals(runEntity.getStatus()) || "INJECTION_NOT_OBSERVED".equals(runEntity.getStatus())
                || "FAILED".equals(runEntity.getStatus()) || "STOPPED".equals(runEntity.getStatus())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "EXPERIMENT_NOT_RUNNING",
                    "实验运行已结束",
                    Map.of("runId", runId, "status", runEntity.getStatus())
            );
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        if (experimentFaultObservationService == null) {
            runEntity.setStatus("STOPPED");
            runEntity.setEndedAt(now);
            experimentRunRepository.save(runEntity);
            redisRealtimeStateStore.saveActiveScenario(DemoScenario.STABLE);
            mqttGateway.publishGlobalScenario(new ScenarioApplyCommandMessage(
                    UUID.randomUUID().toString(), DemoScenario.STABLE, now, "backend-experiment",
                    "STOP_EXPERIMENT", runEntity.getScenarioCode(), runId, List.of(), Map.of(), null, null, true
            ));
            return new ExperimentStopResponse(runId, "STOPPED", now);
        }
        if (!experimentFaultObservationService.beginRecovery(runId, true)) {
            throw new ApiException(HttpStatus.CONFLICT, "EXPERIMENT_RECOVERY_NOT_STARTED", "实验恢复流程未启动", Map.of("runId", runId));
        }
        String currentStatus = experimentRunRepository.findByRunId(runId)
                .map(ExperimentRunEntity::getStatus)
                .orElse("RECOVERING");
        return new ExperimentStopResponse(runId, currentStatus, now);
    }

    /**
     * 删除已停止的实验运行记录。
     * 原始遥测数据未按实验运行标识建立外键关联，因此本操作仅删除运行记录，避免误删共享遥测数据。
     *
     * @param runId 实验运行标识
     * @return 删除结果
     */
    @Transactional
    public ExperimentDeleteResponse deleteExperiment(String runId) {
        ExperimentRunEntity runEntity = experimentRunRepository.findByRunId(runId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "EXPERIMENT_RUN_NOT_FOUND",
                        "实验运行不存在",
                        Map.of("runId", runId)
                ));
        if (List.of("RUNNING", "STARTING", INJECTING_STATUS, "FAULT_OBSERVED", "ORCHESTRATING", "RECOVERING").contains(runEntity.getStatus())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "EXPERIMENT_RUNNING_CANNOT_DELETE",
                    "运行中的实验必须先停止后才能删除记录",
                    Map.of("runId", runId)
            );
        }
        experimentRunRepository.delete(runEntity);
        return new ExperimentDeleteResponse(runId, OffsetDateTime.now(clock));
    }

    private ExperimentScenarioDefinition resolveDefinition(String scenarioCode) {
        try {
            return ExperimentScenarioDefinition.fromCode(scenarioCode);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "EXPERIMENT_SCENARIO_NOT_FOUND",
                    "实验场景不存在",
                    Map.of("scenarioCode", scenarioCode)
            );
        }
    }

    private ExperimentScenarioResponse toScenarioResponse(ExperimentScenarioDefinition definition) {
        return new ExperimentScenarioResponse(
                definition.getScenarioCode(),
                definition.getScenarioName(),
                definition.getDescription(),
                definition.getTargetStationIds(),
                definition.getCategory(),
                definition.getSupportedResourceTypes(),
                definition.getExpectedLinkState(),
                definition.getDurationSeconds(),
                definition.getParameterDefinitions(),
                definition.getExpectedEvidenceType(),
                definition.isAutomaticRecoverySupported(),
                true
        );
    }

    private Map<String, Double> resolveFaultParameters(
            ExperimentScenarioDefinition definition,
            Map<String, Double> requestParameters
    ) {
        Map<String, Double> resolved = new LinkedHashMap<>();
        definition.getParameterDefinitions().forEach((key, parameter) -> {
            double value = requestParameters != null && requestParameters.containsKey(key)
                    ? requestParameters.get(key)
                    : parameter.defaultValue();
            if (!Double.isFinite(value) || value < parameter.minimum() || value > parameter.maximum()) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "EXPERIMENT_PARAMETER_OUT_OF_RANGE",
                        "实验参数不在场景允许范围内",
                        Map.of("scenarioCode", definition.getScenarioCode(), "parameter", key)
                );
            }
            resolved.put(key, value);
        });
        if (requestParameters != null && requestParameters.keySet().stream()
                .anyMatch(key -> !definition.getParameterDefinitions().containsKey(key))) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "EXPERIMENT_PARAMETER_UNSUPPORTED",
                    "实验请求包含场景未定义参数",
                    Map.of("scenarioCode", definition.getScenarioCode())
            );
        }
        return Map.copyOf(resolved);
    }

    private void validateTargetStations(ExperimentScenarioDefinition definition, List<String> targetStationIds) {
        if (stationRepository == null) {
            return;
        }
        if (targetStationIds.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EXPERIMENT_TARGET_REQUIRED", "实验至少需要一个目标场站", Map.of());
        }
        for (String stationId : targetStationIds) {
            StationEntity station = stationRepository.findByStationId(stationId)
                    .orElseThrow(() -> new ApiException(
                            HttpStatus.NOT_FOUND,
                            "EXPERIMENT_TARGET_STATION_NOT_FOUND",
                            "实验目标场站不存在",
                            Map.of("stationId", stationId)
                    ));
            if (!station.isEnabled() || !definition.getSupportedResourceTypes().contains(station.getResourceType())) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "EXPERIMENT_TARGET_RESOURCE_UNSUPPORTED",
                        "目标场站不支持当前故障场景",
                        Map.of("stationId", stationId, "scenarioCode", definition.getScenarioCode())
                );
            }
        }
    }

    private String resolveControlStrategy(String requestedStrategy) {
        String strategy = requestedStrategy == null || requestedStrategy.isBlank()
                ? "ADAPTIVE" : requestedStrategy.trim().toUpperCase(java.util.Locale.ROOT);
        if (!List.of("CENTRALIZED", "FIXED_WEIGHT", "ADAPTIVE").contains(strategy)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EXPERIMENT_CONTROL_STRATEGY_UNSUPPORTED",
                    "实验对照策略不支持", Map.of("controlStrategy", strategy));
        }
        return strategy;
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

    /**
     * 转换为不包含结果摘要的实验历史列表项。
     *
     * @param entity 实验运行实体
     * @return 实验历史列表项
     */
    private ExperimentRunListItemResponse toRunListItemResponse(ExperimentRunEntity entity) {
        return new ExperimentRunListItemResponse(
                entity.getRunId(),
                entity.getScenarioCode(),
                resolveScenarioName(entity.getScenarioCode()),
                entity.getStatus(),
                entity.getStartedAt(),
                entity.getEndedAt()
        );
    }

    /**
     * 解析场景展示名称，并兼容历史场景编码。
     *
     * @param scenarioCode 场景编码
     * @return 场景展示名称
     */
    private String resolveScenarioName(String scenarioCode) {
        try {
            return ExperimentScenarioDefinition.fromCode(scenarioCode).getScenarioName();
        } catch (IllegalArgumentException exception) {
            return scenarioCode;
        }
    }

    private String buildSummary(
            ExperimentScenarioDefinition definition,
            List<String> targetStationIds,
            Map<String, Double> faultParameters,
            int durationSeconds,
            Boolean automaticRecovery,
            String controlStrategy,
            Long randomSeed,
            String remark,
            OffsetDateTime startedAt
    ) {
        try {
            LinkedHashMap<String, Object> summaryPayload = new LinkedHashMap<>();
            summaryPayload.put("targetStationIds", targetStationIds);
            summaryPayload.put("durationSeconds", durationSeconds);
            summaryPayload.put("automaticRecovery", automaticRecovery == null || automaticRecovery);
            summaryPayload.put("controlStrategy", controlStrategy);
            summaryPayload.put("randomSeed", randomSeed);
            summaryPayload.put("remark", remark == null ? "" : remark);
            summaryPayload.put("injectionStartedAt", startedAt);
            summaryPayload.put("injectionDeadline", startedAt.plusSeconds(definition.getInjectionObservationTimeoutSeconds()));
            summaryPayload.put("lifecycleStatus", INJECTING_STATUS);
            summaryPayload.put("evidenceStatus", "PENDING");
            summaryPayload.put("faultObservedAt", null);
            summaryPayload.put("expectedFaultValues", Map.of("requiredLinkState", definition.getExpectedLinkState().name()));
            summaryPayload.put("faultParameters", faultParameters);
            summaryPayload.put("observedFaultValues", Map.of());
            summaryPayload.put("observedLinkStates", Map.of());
            summaryPayload.put("observationFailureReason", null);
            summaryPayload.put("recoveryStartedAt", null);
            summaryPayload.put("recoveredAt", null);
            summaryPayload.put("recoveryReportStatus", null);
            summaryPayload.put("recoveredLinkStates", Map.of());
            summaryPayload.put("orchestrationStatus", ORCHESTRATION_STATUS_PENDING);
            summaryPayload.put("orchestrationTriggeredAt", null);
            summaryPayload.put("orchestrationCompletedAt", null);
            summaryPayload.put("dispatchContextSnapshotId", null);
            summaryPayload.put("dispatchPlanId", null);
            summaryPayload.put("agentRunId", null);
            summaryPayload.put("planStatus", null);
            summaryPayload.put("executionBatchId", null);
            summaryPayload.put("safetyGatePassed", null);
            summaryPayload.put("failureReason", null);
            summaryPayload.put("expectedLinkState", definition.getExpectedLinkState().name());
            return objectMapper.writeValueAsString(summaryPayload);
        } catch (JsonProcessingException exception) {
            return "目标场站=" + String.join(",", targetStationIds) + "，持续时间=" + durationSeconds + "秒。";
        }
    }
}

package com.weaksignal.vpp.backend.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weaksignal.vpp.backend.agent.AiProviderRegistry;
import com.weaksignal.vpp.backend.agent.FaultAgent;
import com.weaksignal.vpp.backend.api.ApiException;
import com.weaksignal.vpp.backend.api.dto.AiProviderStatusResponse;
import com.weaksignal.vpp.backend.api.dto.FaultActionResponse;
import com.weaksignal.vpp.backend.api.dto.FaultAnalysisRequest;
import com.weaksignal.vpp.backend.api.dto.FaultRunResponse;
import com.weaksignal.vpp.backend.domain.LinkState;
import com.weaksignal.vpp.backend.fault.AgentStructuredResponse;
import com.weaksignal.vpp.backend.fault.ApprovalStatus;
import com.weaksignal.vpp.backend.fault.FaultActionType;
import com.weaksignal.vpp.backend.fault.FaultContext;
import com.weaksignal.vpp.backend.fault.FaultRuleDecision;
import com.weaksignal.vpp.backend.fault.FaultRuleEngine;
import com.weaksignal.vpp.backend.fault.FaultRunStatus;
import com.weaksignal.vpp.backend.fault.FaultSafetyDecision;
import com.weaksignal.vpp.backend.fault.FaultSafetyGate;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.AgentDiagnosisResultEntity;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.CommandAckEntity;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.FaultActionPlanEntity;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.FaultDiagnosisRunEntity;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.FaultEventEntity;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.StationEntity;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.TelemetrySnapshotEntity;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.AgentDiagnosisResultRepository;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.CommandAckRepository;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.FaultActionPlanRepository;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.FaultDiagnosisRunRepository;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.FaultEventRepository;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.StationRepository;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.TelemetrySnapshotRepository;
import com.weaksignal.vpp.backend.infrastructure.redis.RedisRealtimeStateStore;
import com.weaksignal.vpp.backend.mqtt.MqttGateway;
import com.weaksignal.vpp.backend.mqtt.dto.CommandAckMessage;
import com.weaksignal.vpp.backend.mqtt.dto.FaultActionCommandMessage;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 多智能体故障诊断、自动裁决、安全执行与审计应用服务。
 */
@Service
public class FaultIntelligenceService {

    private final FaultRuleEngine ruleEngine;
    private final List<FaultAgent> faultAgents;
    private final FaultSafetyGate safetyGate;
    private final StationRepository stationRepository;
    private final TelemetrySnapshotRepository telemetrySnapshotRepository;
    private final RedisRealtimeStateStore redisRealtimeStateStore;
    private final FaultDiagnosisRunRepository runRepository;
    private final AgentDiagnosisResultRepository resultRepository;
    private final FaultActionPlanRepository actionRepository;
    private final FaultEventRepository faultEventRepository;
    private final CommandAckRepository commandAckRepository;
    private final AiProviderRegistry providerRegistry;
    private final MqttGateway mqttGateway;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    /**
     * 注入故障智能编排依赖。
     *
     * @param ruleEngine 确定性规则引擎
     * @param faultAgents 五类逻辑智能体
     * @param safetyGate 安全门
     * @param stationRepository 场站仓储
     * @param telemetrySnapshotRepository 遥测快照仓储
     * @param redisRealtimeStateStore Redis 实时状态
     * @param runRepository 诊断运行仓储
     * @param resultRepository 智能体结果仓储
     * @param actionRepository 动作计划仓储
     * @param faultEventRepository 故障事件仓储
     * @param commandAckRepository 命令回执仓储
     * @param providerRegistry Provider 注册表
     * @param mqttGateway MQTT 网关
     * @param objectMapper JSON 组件
     * @param clock 系统时钟
     */
    public FaultIntelligenceService(
            FaultRuleEngine ruleEngine,
            List<FaultAgent> faultAgents,
            FaultSafetyGate safetyGate,
            StationRepository stationRepository,
            TelemetrySnapshotRepository telemetrySnapshotRepository,
            RedisRealtimeStateStore redisRealtimeStateStore,
            FaultDiagnosisRunRepository runRepository,
            AgentDiagnosisResultRepository resultRepository,
            FaultActionPlanRepository actionRepository,
            FaultEventRepository faultEventRepository,
            CommandAckRepository commandAckRepository,
            AiProviderRegistry providerRegistry,
            MqttGateway mqttGateway,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.ruleEngine = ruleEngine;
        this.faultAgents = faultAgents;
        this.safetyGate = safetyGate;
        this.stationRepository = stationRepository;
        this.telemetrySnapshotRepository = telemetrySnapshotRepository;
        this.redisRealtimeStateStore = redisRealtimeStateStore;
        this.runRepository = runRepository;
        this.resultRepository = resultRepository;
        this.actionRepository = actionRepository;
        this.faultEventRepository = faultEventRepository;
        this.commandAckRepository = commandAckRepository;
        this.providerRegistry = providerRegistry;
        this.mqttGateway = mqttGateway;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * 发起一次有明确结束条件的多智能体故障诊断。
     *
     * @param request 分析请求
     * @return 完整诊断运行
     */
    public FaultRunResponse analyze(FaultAnalysisRequest request) {
        StationEntity station = stationRepository.findByStationId(request.stationId())
                .filter(StationEntity::isEnabled)
                .orElseThrow(() -> notFound("STATION_NOT_FOUND", "场站不存在或未启用", request.stationId()));
        TelemetrySnapshotEntity snapshot = telemetrySnapshotRepository.findByStationId(request.stationId())
                .orElseThrow(() -> notFound("TELEMETRY_NOT_FOUND", "场站尚无实时遥测", request.stationId()));
        OffsetDateTime now = OffsetDateTime.now(clock);
        FaultContext context = createContext(request, snapshot, now);
        FaultRuleDecision ruleDecision = ruleEngine.evaluate(context);
        List<AgentStructuredResponse> agentResults = faultAgents.stream()
                .map(agent -> agent.analyze(context, ruleDecision))
                .toList();
        FaultSafetyDecision safetyDecision = safetyGate.decide(context, ruleDecision, agentResults);

        FaultDiagnosisRunEntity run = new FaultDiagnosisRunEntity();
        run.setRunId(UUID.randomUUID().toString());
        run.setEventId(request.eventId());
        run.setStationId(station.getStationId());
        run.setRuleResultJson(writeJson(ruleDecision));
        run.setFinalDecisionJson(writeJson(safetyDecision));
        run.setStatus(FaultRunStatus.READY.name());
        run.setApprovalStatus(ApprovalStatus.NOT_REQUIRED.name());
        run.setConflictDetected(safetyDecision.conflictDetected());
        run.setFallbackUsed(safetyDecision.fallbackUsed());
        run.setCreatedAt(now);
        run.setUpdatedAt(now);
        runRepository.save(run);

        for (AgentStructuredResponse result : agentResults) {
            persistAgentResult(run.getRunId(), result, now);
        }
        for (var action : safetyDecision.approvedActions()) {
            persistAction(run.getRunId(), action, ruleDecision, false, now);
        }
        if (ruleDecision.matched()) {
            persistFaultEvent(run, ruleDecision, now);
        }
        executeApprovedActions(run);
        return toResponse(run);
    }

    /**
     * 查询最近诊断运行。
     *
     * @param limit 查询条数
     * @return 诊断运行列表
     */
    public List<FaultRunResponse> getLatestRuns(int limit) {
        return runRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, limit)).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * 查询诊断运行详情。
     *
     * @param runId 运行标识
     * @return 诊断运行
     */
    public FaultRunResponse getRun(String runId) {
        return toResponse(requireRun(runId));
    }

    /**
     * 查询诊断运行处置计划。
     *
     * @param runId 运行标识
     * @return 动作列表
     */
    public List<FaultActionResponse> getPlan(String runId) {
        requireRun(runId);
        return actionRepository.findByRunIdOrderByCreatedAtAsc(runId).stream()
                .map(this::toActionResponse)
                .toList();
    }

    /**
     * 兼容旧版复核通过入口，自动裁决模式下正常不会进入待复核状态。
     *
     * @param runId 运行标识
     * @param reason 复核原因
     * @return 更新后的运行
     */
    @Transactional
    public FaultRunResponse approve(String runId, String reason) {
        FaultDiagnosisRunEntity run = requireRun(runId);
        if (!ApprovalStatus.PENDING.name().equals(run.getApprovalStatus())) {
            throw conflict("FAULT_REVIEW_CONFLICT", "当前方案不能重复审批");
        }
        run.setApprovalStatus(ApprovalStatus.APPROVED.name());
        run.setStatus(FaultRunStatus.READY.name());
        run.setReviewReason(reason);
        run.setReviewedAt(OffsetDateTime.now(clock));
        run.setUpdatedAt(OffsetDateTime.now(clock));
        runRepository.save(run);
        return toResponse(run);
    }

    /**
     * 兼容旧版复核拒绝入口，自动裁决模式下正常不会进入待复核状态。
     *
     * @param runId 运行标识
     * @param reason 复核原因
     * @return 更新后的运行
     */
    @Transactional
    public FaultRunResponse reject(String runId, String reason) {
        FaultDiagnosisRunEntity run = requireRun(runId);
        if (!ApprovalStatus.PENDING.name().equals(run.getApprovalStatus())) {
            throw conflict("FAULT_REVIEW_CONFLICT", "当前方案不能重复审批");
        }
        run.setApprovalStatus(ApprovalStatus.REJECTED.name());
        run.setStatus(FaultRunStatus.REJECTED.name());
        run.setReviewReason(reason);
        run.setReviewedAt(OffsetDateTime.now(clock));
        run.setUpdatedAt(OffsetDateTime.now(clock));
        runRepository.save(run);
        actionRepository.findByRunIdOrderByCreatedAtAsc(runId).forEach(action -> {
            action.setStatus("REJECTED");
            action.setUpdatedAt(OffsetDateTime.now(clock));
            actionRepository.save(action);
        });
        return toResponse(run);
    }

    /**
     * 执行安全门已自动放行的方案。
     *
     * @param runId 运行标识
     * @return 更新后的运行
     */
    public FaultRunResponse execute(String runId) {
        FaultDiagnosisRunEntity run = requireRun(runId);
        if (FaultRunStatus.EXECUTED.name().equals(run.getStatus())
                || FaultRunStatus.EXECUTING.name().equals(run.getStatus())) {
            return toResponse(run);
        }
        if (ApprovalStatus.PENDING.name().equals(run.getApprovalStatus())
                || ApprovalStatus.REJECTED.name().equals(run.getApprovalStatus())) {
            throw conflict("FAULT_APPROVAL_REQUIRED", "方案尚未批准或已被拒绝");
        }
        if (!FaultRunStatus.READY.name().equals(run.getStatus())) {
            throw conflict("FAULT_EXECUTION_CONFLICT", "当前诊断状态不允许执行");
        }
        executeApprovedActions(run);
        return toResponse(run);
    }

    /**
     * 处理模拟器故障动作回执。
     *
     * @param message 通用命令回执
     */
    @Transactional
    public void handleAcknowledgement(CommandAckMessage message) {
        FaultActionPlanEntity action = actionRepository.findByMqttCommandId(message.commandId()).orElse(null);
        if (action == null || isTerminalAction(action.getStatus())) {
            return;
        }
        if (!commandAckRepository.existsByCommandIdAndAckStage(message.commandId(), message.ackStage())) {
            CommandAckEntity acknowledgement = new CommandAckEntity();
            acknowledgement.setCommandId(message.commandId());
            acknowledgement.setStationId(message.stationId());
            acknowledgement.setAckStage(message.ackStage());
            acknowledgement.setSuccess(message.success());
            acknowledgement.setMessage(message.message());
            acknowledgement.setAckTime(message.ackTime());
            commandAckRepository.save(acknowledgement);
        }
        String nextStatus = switch (message.ackStage()) {
            case "RECEIVED" -> "ACCEPTED";
            case "EXECUTED" -> "EXECUTED";
            case "REJECTED" -> "REJECTED";
            default -> action.getStatus();
        };
        action.setStatus(nextStatus);
        action.setUpdatedAt(OffsetDateTime.now(clock));
        actionRepository.save(action);
        updateRunCompletion(action.getRunId());
    }

    /**
     * 查询脱敏 Provider 状态。
     *
     * @return Provider 状态
     */
    public List<AiProviderStatusResponse> getProviderStatuses() {
        return providerRegistry.providers().stream()
                .map(provider -> new AiProviderStatusResponse(
                        provider.providerCode(),
                        provider.isEnabled(),
                        provider.isAvailable() && provider.lastErrorAt().isEmpty(),
                        provider.lastErrorAt().orElse(null)
                ))
                .toList();
    }

    private FaultContext createContext(
            FaultAnalysisRequest request,
            TelemetrySnapshotEntity snapshot,
            OffsetDateTime now
    ) {
        long heartbeatAge = request.heartbeatAge() >= 0
                ? request.heartbeatAge()
                : redisRealtimeStateStore.getHeartbeat(request.stationId())
                        .map(heartbeat -> Math.max(0, Duration.between(heartbeat, now).toSeconds()))
                        .orElse(999L);
        return new FaultContext(
                request.eventId(),
                request.stationId(),
                snapshot.getLinkState(),
                snapshot.getLatency(),
                request.jitter(),
                snapshot.getPacketLossRate(),
                request.consecutivePacketLoss(),
                snapshot.getDataAge(),
                heartbeatAge,
                request.acknowledgementAge(),
                request.duplicateMessage(),
                request.staleSequence(),
                request.timeSkewSeconds(),
                request.frozenTelemetry(),
                request.suspectedAttack(),
                snapshot.getActivePower(),
                snapshot.getAvailableCapacity(),
                snapshot.isOnline()
        );
    }

    private void executeApprovedActions(FaultDiagnosisRunEntity run) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        List<FaultActionPlanEntity> actions = actionRepository.findByRunIdOrderByCreatedAtAsc(run.getRunId());
        run.setStatus(actions.isEmpty() ? FaultRunStatus.EXECUTED.name() : FaultRunStatus.EXECUTING.name());
        run.setUpdatedAt(now);
        runRepository.save(run);
        boolean failed = false;
        for (FaultActionPlanEntity action : actions) {
            if (!"READY".equals(action.getStatus())) {
                continue;
            }
            OffsetDateTime expireAt = now.plusSeconds(30);
            String mqttCommandId = action.getActionId();
            FaultActionCommandMessage message = new FaultActionCommandMessage(
                    mqttCommandId,
                    run.getRunId(),
                    action.getStationId(),
                    FaultActionType.valueOf(action.getActionType()),
                    readMap(action.getParametersJson()),
                    now,
                    expireAt
            );
            action.setMqttCommandId(mqttCommandId);
            action.setIssuedAt(now);
            action.setExpireAt(expireAt);
            action.setStatus("SENT");
            action.setUpdatedAt(now);
            actionRepository.save(action);
            boolean published = mqttGateway.publishFaultAction(message);
            if (!published) {
                action.setStatus("FAILED");
                action.setUpdatedAt(OffsetDateTime.now(clock));
                actionRepository.save(action);
            }
            failed |= !published;
        }
        if (failed) {
            run.setStatus(FaultRunStatus.FAILED.name());
            run.setUpdatedAt(OffsetDateTime.now(clock));
            runRepository.save(run);
        }
    }

    private void updateRunCompletion(String runId) {
        FaultDiagnosisRunEntity run = requireRun(runId);
        List<FaultActionPlanEntity> actions = actionRepository.findByRunIdOrderByCreatedAtAsc(runId);
        if (actions.stream().allMatch(action -> "EXECUTED".equals(action.getStatus()))) {
            run.setStatus(FaultRunStatus.EXECUTED.name());
        } else if (actions.stream().anyMatch(action -> "REJECTED".equals(action.getStatus()) || "FAILED".equals(action.getStatus()))) {
            run.setStatus(FaultRunStatus.FAILED.name());
        }
        run.setUpdatedAt(OffsetDateTime.now(clock));
        runRepository.save(run);
    }

    private void persistAgentResult(String runId, AgentStructuredResponse result, OffsetDateTime now) {
        AgentDiagnosisResultEntity entity = new AgentDiagnosisResultEntity();
        entity.setResultId(UUID.randomUUID().toString());
        entity.setRunId(runId);
        entity.setAgentType(result.agentType().name());
        entity.setProvider(result.provider());
        entity.setFaultCode(result.faultCode());
        entity.setSeverity(result.severity().name());
        entity.setConfidence(result.confidence());
        entity.setResultJson(writeJson(result));
        entity.setCreatedAt(now);
        resultRepository.save(entity);
    }

    private void persistAction(
            String runId,
            com.weaksignal.vpp.backend.fault.ProposedFaultAction action,
            FaultRuleDecision ruleDecision,
            boolean requiresApproval,
            OffsetDateTime now
    ) {
        FaultActionPlanEntity entity = new FaultActionPlanEntity();
        entity.setActionId(UUID.randomUUID().toString());
        entity.setRunId(runId);
        entity.setStationId(action.stationId());
        entity.setActionType(action.actionType().name());
        entity.setParametersJson(writeJson(action.parameters()));
        entity.setRiskLevel(ruleDecision.severity().name());
        entity.setStatus("READY");
        entity.setRequiresApproval(requiresApproval);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        actionRepository.save(entity);
    }

    private void persistFaultEvent(
            FaultDiagnosisRunEntity run,
            FaultRuleDecision decision,
            OffsetDateTime now
    ) {
        FaultEventEntity event = new FaultEventEntity();
        event.setEventId(run.getEventId() == null || run.getEventId().isBlank()
                ? UUID.randomUUID().toString()
                : run.getEventId());
        event.setStationId(run.getStationId());
        event.setFaultCode(decision.faultCode());
        event.setTitle("多智能体诊断：" + decision.faultType());
        event.setDescription("规则判定 " + decision.faultCode() + "，处置流程已进入安全裁决。");
        event.setLevel(decision.severity().name().equals("LOW") ? "提示"
                : decision.severity().name().equals("MEDIUM") ? "预警" : "严重");
        event.setStatus("ACTIVE");
        event.setOccurredAt(now);
        faultEventRepository.save(event);
    }

    private FaultRunResponse toResponse(FaultDiagnosisRunEntity run) {
        FaultRuleDecision ruleDecision = readJson(run.getRuleResultJson(), FaultRuleDecision.class);
        FaultSafetyDecision safetyDecision = readJson(run.getFinalDecisionJson(), FaultSafetyDecision.class);
        List<AgentStructuredResponse> agentResults = resultRepository.findByRunIdOrderByCreatedAtAsc(run.getRunId())
                .stream()
                .map(entity -> readJson(entity.getResultJson(), AgentStructuredResponse.class))
                .toList();
        List<FaultActionResponse> actions = actionRepository.findByRunIdOrderByCreatedAtAsc(run.getRunId()).stream()
                .map(this::toActionResponse)
                .toList();
        return new FaultRunResponse(
                run.getRunId(),
                run.getEventId(),
                run.getStationId(),
                FaultRunStatus.valueOf(run.getStatus()),
                ApprovalStatus.valueOf(run.getApprovalStatus()),
                ruleDecision,
                agentResults,
                safetyDecision,
                actions,
                run.isConflictDetected(),
                run.isFallbackUsed(),
                run.getCreatedAt(),
                run.getUpdatedAt()
        );
    }

    private FaultActionResponse toActionResponse(FaultActionPlanEntity entity) {
        return new FaultActionResponse(
                entity.getActionId(),
                entity.getStationId(),
                FaultActionType.valueOf(entity.getActionType()),
                readMap(entity.getParametersJson()),
                entity.getRiskLevel(),
                entity.getStatus(),
                entity.isRequiresApproval(),
                entity.getMqttCommandId(),
                entity.getIssuedAt(),
                entity.getExpireAt()
        );
    }

    private FaultDiagnosisRunEntity requireRun(String runId) {
        return runRepository.findByRunId(runId)
                .orElseThrow(() -> notFound("FAULT_RUN_NOT_FOUND", "诊断运行不存在", runId));
    }

    private ApiException notFound(String code, String message, String identifier) {
        return new ApiException(HttpStatus.NOT_FOUND, code, message, Map.of("identifier", identifier));
    }

    private ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message, Map.of());
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "FAULT_AUDIT_SERIALIZATION_FAILED",
                    "故障审计数据序列化失败", Map.of());
        }
    }

    private <T> T readJson(String value, Class<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "FAULT_AUDIT_PAYLOAD_INVALID",
                    "故障审计数据无法解析", Map.of());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> readMap(String value) {
        try {
            return objectMapper.readValue(value, Map.class);
        } catch (JsonProcessingException exception) {
            return Map.of();
        }
    }

    private boolean isTerminalAction(String status) {
        return "EXECUTED".equals(status) || "REJECTED".equals(status) || "FAILED".equals(status);
    }
}

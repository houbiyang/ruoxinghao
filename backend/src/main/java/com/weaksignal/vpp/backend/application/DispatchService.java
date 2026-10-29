package com.weaksignal.vpp.backend.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weaksignal.vpp.backend.api.ApiException;
import com.weaksignal.vpp.backend.api.dto.CommandAckResponse;
import com.weaksignal.vpp.backend.api.dto.DispatchAllocationResponse;
import com.weaksignal.vpp.backend.api.dto.DispatchCommandResponse;
import com.weaksignal.vpp.backend.api.dto.DispatchExecuteResponse;
import com.weaksignal.vpp.backend.api.dto.DispatchPlanPointResponse;
import com.weaksignal.vpp.backend.api.dto.DispatchPreviewResponse;
import com.weaksignal.vpp.backend.api.dto.DispatchRequest;
import com.weaksignal.vpp.backend.api.dto.DispatchStatusResponse;
import com.weaksignal.vpp.backend.domain.ControlMode;
import com.weaksignal.vpp.backend.domain.DispatchCommandStatus;
import com.weaksignal.vpp.backend.domain.LinkState;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.CommandAckEntity;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.ControlCommandEntity;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.TelemetrySnapshotEntity;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.CommandAckRepository;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.ControlCommandRepository;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.TelemetrySnapshotRepository;
import com.weaksignal.vpp.backend.mqtt.MqttGateway;
import com.weaksignal.vpp.backend.mqtt.dto.CommandAckMessage;
import com.weaksignal.vpp.backend.mqtt.dto.DispatchControlMessage;
import com.weaksignal.vpp.backend.mqtt.dto.DispatchControlReleaseMessage;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 规则型调度应用服务。
 */
@Service
public class DispatchService {

    private static final String COMMAND_TYPE = "SET_ACTIVE_POWER";
    private static final int STATUS_SAMPLE_LIMIT = 200;

    private final TelemetrySnapshotRepository telemetrySnapshotRepository;
    private final ControlCommandRepository controlCommandRepository;
    private final CommandAckRepository commandAckRepository;
    private final MqttGateway mqttGateway;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    /**
     * 注入调度服务依赖。
     *
     * @param telemetrySnapshotRepository 遥测快照仓储
     * @param controlCommandRepository 控制指令仓储
     * @param commandAckRepository 指令回执仓储
     * @param mqttGateway MQTT 发布网关
     * @param objectMapper JSON 组件
     * @param clock 系统时钟
     */
    public DispatchService(
            TelemetrySnapshotRepository telemetrySnapshotRepository,
            ControlCommandRepository controlCommandRepository,
            CommandAckRepository commandAckRepository,
            MqttGateway mqttGateway,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.telemetrySnapshotRepository = telemetrySnapshotRepository;
        this.controlCommandRepository = controlCommandRepository;
        this.commandAckRepository = commandAckRepository;
        this.mqttGateway = mqttGateway;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * 生成未来 24 个整点的确定性日内计划。
     *
     * @return 日内计划
     */
    public List<DispatchPlanPointResponse> generatePlan() {
        double availableCapacity = telemetrySnapshotRepository.findAll().stream()
                .filter(TelemetrySnapshotEntity::isOnline)
                .filter(snapshot -> snapshot.getLinkState() != LinkState.S3)
                .mapToDouble(TelemetrySnapshotEntity::getAvailableCapacity)
                .sum();
        OffsetDateTime firstHour = OffsetDateTime.now(clock).truncatedTo(ChronoUnit.HOURS).plusHours(1);
        List<DispatchPlanPointResponse> plan = new ArrayList<>();
        for (int hourOffset = 0; hourOffset < 24; hourOffset += 1) {
            OffsetDateTime timestamp = firstHour.plusHours(hourOffset);
            double loadFactor = loadFactorForHour(timestamp.getHour());
            double planPower = round(availableCapacity * loadFactor);
            plan.add(new DispatchPlanPointResponse(
                    timestamp,
                    planPower,
                    round(availableCapacity),
                    round(availableCapacity - planPower),
                    loadFactor
            ));
        }
        return plan;
    }

    /**
     * 计算规则型滚动目标分配，不下发指令。
     *
     * @param request 调度请求
     * @return 调度预览
     */
    public DispatchPreviewResponse preview(DispatchRequest request) {
        validateConstraints(request);
        List<TelemetrySnapshotEntity> snapshots = telemetrySnapshotRepository.findByStationIdIn(request.stationIds());
        Map<String, TelemetrySnapshotEntity> snapshotMap = new LinkedHashMap<>();
        snapshots.forEach(snapshot -> snapshotMap.put(snapshot.getStationId(), snapshot));

        List<CapacityCandidate> candidates = new ArrayList<>();
        List<DispatchAllocationResponse> unavailable = new ArrayList<>();
        for (String stationId : request.stationIds()) {
            TelemetrySnapshotEntity snapshot = snapshotMap.get(stationId);
            if (snapshot == null) {
                unavailable.add(unavailableAllocation(stationId, "场站没有实时遥测"));
                continue;
            }
            String unavailableReason = unavailableReason(snapshot);
            if (unavailableReason != null) {
                unavailable.add(new DispatchAllocationResponse(
                        stationId,
                        round(snapshot.getActivePower()),
                        0,
                        round(snapshot.getAvailableCapacity()),
                        0,
                        snapshot.getLinkState(),
                        false,
                        unavailableReason
                ));
                continue;
            }
            double constrainedCapacity = Math.min(snapshot.getAvailableCapacity(), request.maximumPower())
                    * stateCapacityFactor(snapshot.getLinkState());
            candidates.add(new CapacityCandidate(snapshot, constrainedCapacity));
        }

        double totalCapacity = candidates.stream().mapToDouble(CapacityCandidate::capacity).sum();
        if (request.totalTargetPower() > totalCapacity + 0.001) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "TARGET_EXCEEDS_CAPACITY",
                    "总目标功率超过当前链路约束下的可调容量",
                    Map.of("targetPower", request.totalTargetPower(), "constrainedCapacity", round(totalCapacity))
            );
        }

        List<DispatchAllocationResponse> allocations = allocateTargets(request, candidates);
        allocations.addAll(unavailable);
        return new DispatchPreviewResponse(
                round(request.totalTargetPower()),
                round(totalCapacity),
                request.controlMode(),
                true,
                "规则校验通过，可下发至 " + candidates.size() + " 个场站",
                allocations
        );
    }

    /**
     * 创建并下发调度指令。
     *
     * @param request 调度请求
     * @return 执行批次
     */
    @Transactional
    public DispatchExecuteResponse execute(DispatchRequest request) {
        DispatchPreviewResponse preview = preview(request);
        OffsetDateTime now = OffsetDateTime.now(clock);
        String batchId = UUID.randomUUID().toString();
        List<DispatchCommandResponse> responses = new ArrayList<>();

        for (DispatchAllocationResponse allocation : preview.allocations()) {
            if (!allocation.eligible()) {
                continue;
            }
            String commandId = UUID.randomUUID().toString();
            DispatchControlMessage message = new DispatchControlMessage(
                    commandId,
                    batchId,
                    null,
                    null,
                    null,
                    null,
                    allocation.stationId(),
                    COMMAND_TYPE,
                    allocation.targetPower(),
                    now,
                    now.plusSeconds(request.commandTimeoutSeconds()),
                    request.controlMode(),
                    request.maximumRampRate()
            );
            ControlCommandEntity entity = createCommandEntity(message);
            controlCommandRepository.save(entity);
            entity.setStatus(publishAfterTransactionCommit(message)
                    ? DispatchCommandStatus.SENT.name()
                    : DispatchCommandStatus.FAILED.name());
            controlCommandRepository.save(entity);
            responses.add(toCommandResponse(entity));
        }
        return new DispatchExecuteResponse(batchId, now, responses);
    }

    /**
     * 执行调度计划派生出的控制指令。
     *
     * @param commands 计划指令集合
     * @return 执行结果
     */
    @Transactional
    public DispatchExecuteResponse executePlannedCommands(List<PlannedDispatchCommand> commands) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        String batchId = UUID.randomUUID().toString();
        List<DispatchCommandResponse> responses = new ArrayList<>();
        for (PlannedDispatchCommand plannedCommand : commands) {
            String commandId = UUID.randomUUID().toString();
            DispatchControlMessage message = new DispatchControlMessage(
                    commandId,
                    batchId,
                    plannedCommand.dispatchPlanId(),
                    plannedCommand.planVersion(),
                    plannedCommand.dispatchPlanItemId(),
                    plannedCommand.inputSnapshotId(),
                    plannedCommand.stationId(),
                    COMMAND_TYPE,
                    plannedCommand.targetPower(),
                    now,
                    now.plusSeconds(plannedCommand.commandTimeoutSeconds()),
                    plannedCommand.controlMode(),
                    plannedCommand.maximumRampRate()
            );
            ControlCommandEntity entity = createCommandEntity(message);
            controlCommandRepository.save(entity);
            entity.setStatus(publishAfterTransactionCommit(message)
                    ? DispatchCommandStatus.SENT.name()
                    : DispatchCommandStatus.FAILED.name());
            controlCommandRepository.save(entity);
            responses.add(toCommandResponse(entity));
        }
        return new DispatchExecuteResponse(batchId, now, responses);
    }

    /**
     * 在当前数据库事务提交后发布控制指令，避免模拟器回执早于指令记录提交。
     *
     * @param message 控制指令消息
     * @return 已安排发布或已立即发布成功时返回 {@code true}
     */
    private boolean publishAfterTransactionCommit(DispatchControlMessage message) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            return mqttGateway.publishDispatchControl(message);
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                if (!mqttGateway.publishDispatchControl(message)) {
                    markCommandPublishFailed(message.commandId());
                }
            }
        });
        return true;
    }

    /**
     * 将事务提交后发布失败且尚未终态的控制指令标记为失败。
     *
     * @param commandId 控制指令标识
     */
    private void markCommandPublishFailed(String commandId) {
        controlCommandRepository.findByCommandId(commandId).ifPresent(command -> {
            DispatchCommandStatus status = DispatchCommandStatus.valueOf(command.getStatus());
            if (status.isTerminal()) {
                return;
            }
            command.setStatus(DispatchCommandStatus.FAILED.name());
            controlCommandRepository.save(command);
        });
    }

    /**
     * 查询最近调度指令。
     *
     * @param limit 查询条数
     * @return 指令列表
     */
    public List<DispatchCommandResponse> getLatestCommands(int limit) {
        return controlCommandRepository.findByCommandTypeOrderByCreatedAtDesc(
                        COMMAND_TYPE,
                        PageRequest.of(0, limit)
                ).stream()
                .map(this::toCommandResponse)
                .toList();
    }

    /**
     * 查询单条调度指令及其回执。
     *
     * @param commandId 指令标识
     * @return 指令详情
     */
    public DispatchCommandResponse getCommand(String commandId) {
        ControlCommandEntity entity = controlCommandRepository.findByCommandId(commandId)
                .filter(command -> COMMAND_TYPE.equals(command.getCommandType()))
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "DISPATCH_COMMAND_NOT_FOUND",
                        "调度指令不存在",
                        Map.of("commandId", commandId)
                ));
        return toCommandResponse(entity);
    }

    /**
     * 按计划查询全部已生成的控制指令。
     *
     * @param planId 调度计划标识
     * @return 指令列表
     */
    public List<DispatchCommandResponse> getCommandsByPlanId(String planId) {
        return controlCommandRepository.findByDispatchPlanIdOrderByCreatedAtAsc(planId).stream()
                .map(this::toCommandResponse)
                .toList();
    }

    /**
     * 查询调度状态摘要。
     *
     * @return 调度状态
     */
    public DispatchStatusResponse getStatus() {
        List<TelemetrySnapshotEntity> adjustable = telemetrySnapshotRepository.findAll().stream()
                .filter(TelemetrySnapshotEntity::isOnline)
                .filter(snapshot -> snapshot.getLinkState() != LinkState.S3)
                .toList();
        List<ControlCommandEntity> commands = controlCommandRepository.findByCommandTypeOrderByCreatedAtDesc(
                COMMAND_TYPE,
                PageRequest.of(0, STATUS_SAMPLE_LIMIT)
        );
        Map<String, Long> statusCounts = new LinkedHashMap<>();
        for (DispatchCommandStatus status : DispatchCommandStatus.values()) {
            statusCounts.put(status.name(), commands.stream().filter(command -> status.name().equals(command.getStatus())).count());
        }
        long terminalCount = commands.stream().filter(command -> isTerminal(command.getStatus())).count();
        long executedCount = statusCounts.get(DispatchCommandStatus.EXECUTED.name());
        long rejectedCount = statusCounts.get(DispatchCommandStatus.REJECTED.name());
        DispatchControlMessage latestMessage = commands.isEmpty() ? null : readMessage(commands.get(0));
        double currentPlanTarget = generatePlan().stream().findFirst()
                .map(DispatchPlanPointResponse::planPower)
                .orElse(0.0);

        return new DispatchStatusResponse(
                latestMessage == null ? ControlMode.EDGE_COORDINATION : latestMessage.controlMode(),
                adjustable.size(),
                round(adjustable.stream().mapToDouble(TelemetrySnapshotEntity::getAvailableCapacity).sum()),
                currentPlanTarget,
                terminalCount == 0 ? 0 : round(executedCount * 100.0 / terminalCount),
                terminalCount == 0 ? 0 : round(rejectedCount * 100.0 / terminalCount),
                statusCounts.get(DispatchCommandStatus.CREATED.name())
                        + statusCounts.get(DispatchCommandStatus.SENT.name())
                        + statusCounts.get(DispatchCommandStatus.ACCEPTED.name()),
                statusCounts.get(DispatchCommandStatus.TIMEOUT.name()),
                statusCounts,
                latestMessage == null ? null : latestMessage.batchId(),
                OffsetDateTime.now(clock)
        );
    }

    /**
     * 处理模拟器回执并推动指令状态机。
     *
     * @param message 回执消息
     */
    @Transactional
    public void handleAcknowledgement(CommandAckMessage message) {
        ControlCommandEntity command = controlCommandRepository.findByCommandId(message.commandId()).orElse(null);
        if (command == null || !COMMAND_TYPE.equals(command.getCommandType())) {
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

        DispatchCommandStatus currentStatus = DispatchCommandStatus.valueOf(command.getStatus());
        if (currentStatus.isTerminal()) {
            return;
        }
        DispatchCommandStatus targetStatus = switch (message.ackStage()) {
            case "RECEIVED" -> DispatchCommandStatus.ACCEPTED;
            case "EXECUTED" -> DispatchCommandStatus.EXECUTED;
            case "REJECTED" -> DispatchCommandStatus.REJECTED;
            default -> currentStatus;
        };
        if (!message.success() && !"REJECTED".equals(message.ackStage())) {
            targetStatus = DispatchCommandStatus.REJECTED;
        }
        command.setStatus(targetStatus.name());
        controlCommandRepository.save(command);
    }

    /**
     * 将过期且仍在处理中的指令标记为超时。
     *
     * @return 更新数量
     */
    @Transactional
    public int markExpiredCommands() {
        List<ControlCommandEntity> expiredCommands = controlCommandRepository.findByStatusInAndExpireAtBefore(
                List.of(
                        DispatchCommandStatus.SENT.name(),
                        DispatchCommandStatus.ACCEPTED.name()
                ),
                OffsetDateTime.now(clock)
        );
        expiredCommands.forEach(command -> command.setStatus(DispatchCommandStatus.TIMEOUT.name()));
        controlCommandRepository.saveAll(expiredCommands);
        return expiredCommands.size();
    }

    /**
     * 释放指定计划已下发到模拟器的场站功率控制，使场站恢复自然基线运行。
     *
     * @param planId 调度计划标识
     * @return 是否已向全部关联场站成功发布释放消息
     */
    public boolean releasePlannedControls(String planId) {
        List<String> stationIds = controlCommandRepository.findByDispatchPlanIdOrderByCreatedAtAsc(planId).stream()
                .map(ControlCommandEntity::getStationId)
                .distinct()
                .toList();
        OffsetDateTime issuedAt = OffsetDateTime.now(clock);
        boolean allPublished = true;
        for (String stationId : stationIds) {
            boolean published = mqttGateway.publishDispatchControlRelease(new DispatchControlReleaseMessage(
                    UUID.randomUUID().toString(),
                    planId,
                    stationId,
                    issuedAt
            ));
            allPublished = allPublished && published;
        }
        return allPublished;
    }

    /**
     * 将计划下尚未完成的指令标记为失败。
     *
     * @param planId 调度计划标识
     * @return 更新数量
     */
    @Transactional
    public int invalidatePendingCommandsForPlan(String planId) {
        List<ControlCommandEntity> commands = controlCommandRepository.findByDispatchPlanIdOrderByCreatedAtAsc(planId);
        int updatedCount = 0;
        for (ControlCommandEntity command : commands) {
            DispatchCommandStatus status = DispatchCommandStatus.valueOf(command.getStatus());
            if (!status.isTerminal()) {
                command.setStatus(DispatchCommandStatus.FAILED.name());
                updatedCount += 1;
            }
        }
        controlCommandRepository.saveAll(commands);
        return updatedCount;
    }

    private List<DispatchAllocationResponse> allocateTargets(
            DispatchRequest request,
            List<CapacityCandidate> candidates
    ) {
        List<DispatchAllocationResponse> allocations = new ArrayList<>();
        double remainingTarget = round(request.totalTargetPower());
        double remainingCapacity = candidates.stream().mapToDouble(CapacityCandidate::capacity).sum();
        for (int index = 0; index < candidates.size(); index += 1) {
            CapacityCandidate candidate = candidates.get(index);
            double target = index == candidates.size() - 1
                    ? remainingTarget
                    : round(remainingTarget * candidate.capacity() / remainingCapacity);
            target = Math.min(target, candidate.capacity());
            if (target > 0 && target < request.minimumPower()) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "MINIMUM_POWER_VIOLATION",
                        "分配结果低于单场站最小功率",
                        Map.of("stationId", candidate.snapshot().getStationId(), "targetPower", target)
                );
            }
            allocations.add(new DispatchAllocationResponse(
                    candidate.snapshot().getStationId(),
                    round(candidate.snapshot().getActivePower()),
                    round(target),
                    round(candidate.snapshot().getAvailableCapacity()),
                    round(candidate.capacity()),
                    candidate.snapshot().getLinkState(),
                    true,
                    "通过 " + candidate.snapshot().getLinkState().name() + " 链路约束"
            ));
            remainingTarget = round(remainingTarget - target);
            remainingCapacity -= candidate.capacity();
        }
        return allocations;
    }

    private ControlCommandEntity createCommandEntity(DispatchControlMessage message) {
        ControlCommandEntity entity = new ControlCommandEntity();
        entity.setCommandId(message.commandId());
        entity.setStationId(message.stationId());
        entity.setCommandType(COMMAND_TYPE);
        try {
            entity.setCommandPayload(objectMapper.writeValueAsString(message));
        } catch (JsonProcessingException exception) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "DISPATCH_SERIALIZATION_FAILED",
                    "调度指令序列化失败",
                    Map.of("commandId", message.commandId())
            );
        }
        entity.setSendTime(message.issuedAt());
        entity.setExpireAt(message.expireAt());
        entity.setBatchId(message.batchId());
        entity.setDispatchPlanId(message.dispatchPlanId());
        entity.setPlanVersion(message.planVersion());
        entity.setDispatchPlanItemId(message.dispatchPlanItemId());
        entity.setInputSnapshotId(message.inputSnapshotId());
        entity.setStatus(DispatchCommandStatus.CREATED.name());
        entity.setCreatedAt(message.issuedAt());
        return entity;
    }

    private DispatchCommandResponse toCommandResponse(ControlCommandEntity entity) {
        DispatchControlMessage message = readMessage(entity);
        List<CommandAckResponse> acknowledgements = commandAckRepository
                .findByCommandIdOrderByAckTimeAsc(entity.getCommandId()).stream()
                .map(acknowledgement -> new CommandAckResponse(
                        acknowledgement.getAckStage(),
                        acknowledgement.isSuccess(),
                        acknowledgement.getMessage(),
                        acknowledgement.getAckTime()
                ))
                .toList();
        CommandAckResponse latestAck = acknowledgements.isEmpty()
                ? null
                : acknowledgements.get(acknowledgements.size() - 1);
        return new DispatchCommandResponse(
                entity.getCommandId(),
                message.batchId(),
                entity.getDispatchPlanId(),
                entity.getPlanVersion(),
                entity.getDispatchPlanItemId(),
                entity.getInputSnapshotId(),
                entity.getStationId(),
                message.targetPower(),
                message.controlMode(),
                entity.getStatus(),
                entity.getSendTime(),
                entity.getExpireAt(),
                latestAck == null ? null : latestAck.ackStage(),
                latestAck == null ? null : latestAck.message(),
                latestAck == null ? null : latestAck.ackTime(),
                acknowledgements
        );
    }

    private DispatchControlMessage readMessage(ControlCommandEntity entity) {
        try {
            return objectMapper.readValue(entity.getCommandPayload(), DispatchControlMessage.class);
        } catch (JsonProcessingException exception) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "DISPATCH_PAYLOAD_INVALID",
                    "已保存的调度指令载荷无法解析",
                    Map.of("commandId", entity.getCommandId())
            );
        }
    }

    private void validateConstraints(DispatchRequest request) {
        if (request.minimumPower() > request.maximumPower()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_POWER_CONSTRAINT",
                    "单场站最小功率不能大于最大功率",
                    Map.of("minimumPower", request.minimumPower(), "maximumPower", request.maximumPower())
            );
        }
    }

    private String unavailableReason(TelemetrySnapshotEntity snapshot) {
        if (!snapshot.isOnline()) {
            return "场站离线，不能接收中心调度";
        }
        if (snapshot.getLinkState() == LinkState.S3) {
            return "S3 失联场站仅允许本地自治";
        }
        return null;
    }

    private DispatchAllocationResponse unavailableAllocation(String stationId, String message) {
        return new DispatchAllocationResponse(stationId, 0, 0, 0, 0, LinkState.S3, false, message);
    }

    private double stateCapacityFactor(LinkState linkState) {
        return switch (linkState) {
            case S1 -> 0.85;
            case S2 -> 0.60;
            case S0, S4 -> 1.0;
            case S3 -> 0.0;
        };
    }

    private double loadFactorForHour(int hour) {
        if (hour < 6) {
            return 0.55;
        }
        if (hour < 10) {
            return 0.72;
        }
        if (hour < 16) {
            return 0.85;
        }
        if (hour < 21) {
            return 0.92;
        }
        return 0.68;
    }

    private boolean isTerminal(String status) {
        try {
            return DispatchCommandStatus.valueOf(status).isTerminal();
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private double round(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    private record CapacityCandidate(TelemetrySnapshotEntity snapshot, double capacity) {
    }
}

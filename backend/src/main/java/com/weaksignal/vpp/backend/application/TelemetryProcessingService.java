package com.weaksignal.vpp.backend.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weaksignal.vpp.backend.api.ApiException;
import com.weaksignal.vpp.backend.domain.LinkState;
import com.weaksignal.vpp.backend.domain.LinkStateEvaluator;
import com.weaksignal.vpp.backend.domain.StationMetricSample;
import com.weaksignal.vpp.backend.domain.StationPosition;
import com.weaksignal.vpp.backend.domain.StationRealtimeSnapshot;
import com.weaksignal.vpp.backend.domain.StateContext;
import com.weaksignal.vpp.backend.domain.StateDecision;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.FaultEventEntity;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.LinkStatusLogEntity;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.TelemetryHistoryEntity;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.TelemetrySnapshotEntity;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.FaultEventRepository;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.LinkStatusLogRepository;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.TelemetryHistoryRepository;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.TelemetrySnapshotRepository;
import com.weaksignal.vpp.backend.infrastructure.redis.RedisRealtimeStateStore;
import com.weaksignal.vpp.backend.mqtt.dto.HeartbeatReportMessage;
import com.weaksignal.vpp.backend.mqtt.dto.TelemetryReportMessage;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 遥测处理服务。
 */
@Service
public class TelemetryProcessingService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TelemetryProcessingService.class);

    private final TelemetrySnapshotRepository telemetrySnapshotRepository;
    private final TelemetryHistoryRepository telemetryHistoryRepository;
    private final LinkStatusLogRepository linkStatusLogRepository;
    private final FaultEventRepository faultEventRepository;
    private final RedisRealtimeStateStore redisRealtimeStateStore;
    private final LinkStateEvaluator linkStateEvaluator;
    private final StationStateTracker stationStateTracker;
    private final StationLayoutRegistry stationLayoutRegistry;
    private final ExperimentClosedLoopOrchestrationService experimentClosedLoopOrchestrationService;
    private final DispatchConvergenceService dispatchConvergenceService;
    private final ExperimentFaultObservationService experimentFaultObservationService;
    private DistributedExperimentControlService distributedExperimentControlService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    /**
     * 注入遥测处理依赖。
     */
    @Autowired
    public TelemetryProcessingService(
            TelemetrySnapshotRepository telemetrySnapshotRepository,
            TelemetryHistoryRepository telemetryHistoryRepository,
            LinkStatusLogRepository linkStatusLogRepository,
            FaultEventRepository faultEventRepository,
            RedisRealtimeStateStore redisRealtimeStateStore,
            LinkStateEvaluator linkStateEvaluator,
            StationStateTracker stationStateTracker,
            StationLayoutRegistry stationLayoutRegistry,
            ExperimentClosedLoopOrchestrationService experimentClosedLoopOrchestrationService,
            DispatchConvergenceService dispatchConvergenceService,
            ExperimentFaultObservationService experimentFaultObservationService,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.telemetrySnapshotRepository = telemetrySnapshotRepository;
        this.telemetryHistoryRepository = telemetryHistoryRepository;
        this.linkStatusLogRepository = linkStatusLogRepository;
        this.faultEventRepository = faultEventRepository;
        this.redisRealtimeStateStore = redisRealtimeStateStore;
        this.linkStateEvaluator = linkStateEvaluator;
        this.stationStateTracker = stationStateTracker;
        this.stationLayoutRegistry = stationLayoutRegistry;
        this.experimentClosedLoopOrchestrationService = experimentClosedLoopOrchestrationService;
        this.dispatchConvergenceService = dispatchConvergenceService;
        this.experimentFaultObservationService = experimentFaultObservationService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * 注入分布式实验控制服务，避免破坏既有测试构造方法。
     *
     * @param distributedExperimentControlService 分布式实验控制服务
     */
    @Autowired
    public void setDistributedExperimentControlService(DistributedExperimentControlService distributedExperimentControlService) {
        this.distributedExperimentControlService = distributedExperimentControlService;
    }

    /**
     * 兼容旧测试的遥测处理构造方法。
     *
     * @param telemetrySnapshotRepository 遥测快照仓储
     * @param telemetryHistoryRepository 遥测历史仓储
     * @param linkStatusLogRepository 链路状态日志仓储
     * @param faultEventRepository 故障事件仓储
     * @param redisRealtimeStateStore Redis 实时状态存储
     * @param linkStateEvaluator 链路状态判定器
     * @param stationStateTracker 场站状态跟踪器
     * @param stationLayoutRegistry 场站布局注册表
     * @param experimentClosedLoopOrchestrationService 实验闭环服务
     * @param objectMapper JSON 组件
     * @param clock 系统时钟
     */
    public TelemetryProcessingService(
            TelemetrySnapshotRepository telemetrySnapshotRepository,
            TelemetryHistoryRepository telemetryHistoryRepository,
            LinkStatusLogRepository linkStatusLogRepository,
            FaultEventRepository faultEventRepository,
            RedisRealtimeStateStore redisRealtimeStateStore,
            LinkStateEvaluator linkStateEvaluator,
            StationStateTracker stationStateTracker,
            StationLayoutRegistry stationLayoutRegistry,
            ExperimentClosedLoopOrchestrationService experimentClosedLoopOrchestrationService,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this(telemetrySnapshotRepository, telemetryHistoryRepository, linkStatusLogRepository, faultEventRepository,
                redisRealtimeStateStore, linkStateEvaluator, stationStateTracker, stationLayoutRegistry,
                experimentClosedLoopOrchestrationService, null, null, objectMapper, clock);
    }

    /**
     * 处理心跳上报。
     *
     * @param message 心跳消息
     */
    public void handleHeartbeat(HeartbeatReportMessage message) {
        redisRealtimeStateStore.saveHeartbeat(message.stationId(), message.heartbeatAt());
        OffsetDateTime now = OffsetDateTime.now(clock);
        StateContext context = stationStateTracker.getOrCreate(message.stationId(), now);
        stationStateTracker.update(
                message.stationId(),
                new StateContext(
                        context.currentState(),
                        context.lastChangedAt(),
                        message.heartbeatAt(),
                        context.lastSequenceNumber(),
                        context.lastDeviceTimestamp()
                )
        );
    }

    /**
     * 处理遥测上报。
     *
     * @param message 遥测消息
     */
    @Transactional
    public void handleTelemetry(TelemetryReportMessage message) {
        if (telemetryHistoryRepository.existsByMessageId(message.messageId())) {
            LOGGER.info("检测到重复消息，已忽略。stationId={}, messageId={}", message.stationId(), message.messageId());
            return;
        }

        OffsetDateTime receivedAt = OffsetDateTime.now(clock);
        long dataAge = Math.max(0, Duration.between(message.deviceTimestamp(), receivedAt).toSeconds());

        Optional<TelemetrySnapshotEntity> existingSnapshot = telemetrySnapshotRepository.findByStationId(message.stationId());
        if (existingSnapshot.isPresent()) {
            TelemetrySnapshotEntity snapshotEntity = existingSnapshot.get();
            boolean staleSequence = message.sequenceNumber() <= snapshotEntity.getSequenceNumber();
            boolean staleTimestamp = message.deviceTimestamp().isBefore(snapshotEntity.getDeviceTimestamp());
            if (staleSequence || staleTimestamp) {
                LOGGER.warn("检测到过期遥测，已拒绝覆盖最新快照。stationId={}, sequenceNumber={}", message.stationId(), message.sequenceNumber());
                return;
            }
        }

        OffsetDateTime now = receivedAt;
        StateContext context = stationStateTracker.getOrCreate(message.stationId(), now);
        StationMetricSample sample = new StationMetricSample(message.latency(), message.packetLossRate(), dataAge, message.online());
        StateDecision stateDecision = linkStateEvaluator.evaluate(context, sample, now);

        TelemetryHistoryEntity historyEntity = new TelemetryHistoryEntity();
        historyEntity.setStationId(message.stationId());
        historyEntity.setMessageId(message.messageId());
        historyEntity.setSequenceNumber(message.sequenceNumber());
        historyEntity.setDeviceTimestamp(message.deviceTimestamp());
        historyEntity.setReceivedTimestamp(receivedAt);
        historyEntity.setActivePower(message.activePower());
        historyEntity.setAvailableCapacity(message.availableCapacity());
        historyEntity.setLatency(message.latency());
        historyEntity.setPacketLossRate(message.packetLossRate());
        historyEntity.setDataAge(dataAge);
        historyEntity.setLinkState(stateDecision.nextState());
        historyEntity.setQualityCode(message.qualityCode());
        historyEntity.setOnline(message.online());
        historyEntity.setExperimentRunId(message.experimentRunId());
        historyEntity.setActiveFaultCode(message.activeFaultCode());
        historyEntity.setPayloadJson(toJson(message));

        try {
            telemetryHistoryRepository.save(historyEntity);
        } catch (DataIntegrityViolationException exception) {
            LOGGER.warn("重复历史消息写入被唯一约束拦截。stationId={}, messageId={}", message.stationId(), message.messageId());
            return;
        }

        TelemetrySnapshotEntity snapshotEntity = existingSnapshot.orElseGet(TelemetrySnapshotEntity::new);
        snapshotEntity.setStationId(message.stationId());
        snapshotEntity.setMessageId(message.messageId());
        snapshotEntity.setSequenceNumber(message.sequenceNumber());
        snapshotEntity.setDeviceTimestamp(message.deviceTimestamp());
        snapshotEntity.setReceivedTimestamp(receivedAt);
        snapshotEntity.setActivePower(message.activePower());
        snapshotEntity.setAvailableCapacity(message.availableCapacity());
        snapshotEntity.setLatency(message.latency());
        snapshotEntity.setPacketLossRate(message.packetLossRate());
        snapshotEntity.setDataAge(dataAge);
        snapshotEntity.setLinkState(stateDecision.nextState());
        snapshotEntity.setQualityCode(message.qualityCode());
        snapshotEntity.setOnline(message.online());
        snapshotEntity.setExperimentRunId(message.experimentRunId());
        snapshotEntity.setActiveFaultCode(message.activeFaultCode());
        snapshotEntity.setPayloadJson(toJson(message));
        snapshotEntity.setUpdatedAt(receivedAt);
        telemetrySnapshotRepository.save(snapshotEntity);

        StationPosition position = stationLayoutRegistry.getPosition(message.stationId());
        StationRealtimeSnapshot realtimeSnapshot = new StationRealtimeSnapshot(
                message.stationId(),
                message.stationName(),
                message.resourceType(),
                message.activePower(),
                message.availableCapacity(),
                stateDecision.nextState(),
                message.latency(),
                message.packetLossRate(),
                dataAge,
                message.online(),
                position,
                message.sequenceNumber(),
                message.deviceTimestamp(),
                receivedAt
        );
        redisRealtimeStateStore.saveLatest(realtimeSnapshot);
        redisRealtimeStateStore.saveHeartbeat(message.stationId(), receivedAt);

        StateContext updatedContext = new StateContext(
                stateDecision.nextState(),
                stateDecision.changed() ? now : context.lastChangedAt(),
                now,
                message.sequenceNumber(),
                message.deviceTimestamp()
        );
        stationStateTracker.update(message.stationId(), updatedContext);

        if (stateDecision.changed()) {
            persistStateChange(message.stationId(), context.currentState(), stateDecision.nextState(), stateDecision.reason(), now);
            createFaultEventForState(message.stationId(), stateDecision.nextState(), stateDecision.reason(), now);
            if (experimentFaultObservationService != null) {
                experimentFaultObservationService.observeLinkState(message.stationId(), stateDecision.nextState(), now);
            }
        }
        if (experimentFaultObservationService != null) {
            experimentFaultObservationService.observeTelemetry(message, stateDecision.nextState(), receivedAt);
        }
        if (dispatchConvergenceService != null) {
            try {
                if (message.experimentRunId() != null && !message.experimentRunId().isBlank()) {
                    dispatchConvergenceService.observeTelemetry(message.experimentRunId());
                } else {
                    dispatchConvergenceService.observeTelemetryForActiveRound(message.stationId());
                }
            } catch (ApiException exception) {
                LOGGER.warn("跳过当前遥测的收敛复优化。stationId={}, code={}", message.stationId(), exception.getCode());
            }
        }
        if (distributedExperimentControlService != null) {
            distributedExperimentControlService.observeTelemetry(message.stationId());
        }
    }

    private void persistStateChange(String stationId, LinkState previousState, LinkState currentState, String reason, OffsetDateTime occurredAt) {
        LinkStatusLogEntity entity = new LinkStatusLogEntity();
        entity.setStationId(stationId);
        entity.setPreviousState(previousState);
        entity.setCurrentState(currentState);
        entity.setReason(reason);
        entity.setOccurredAt(occurredAt);
        linkStatusLogRepository.save(entity);
    }

    private void createFaultEventForState(String stationId, LinkState linkState, String reason, OffsetDateTime occurredAt) {
        FaultEventEntity entity = new FaultEventEntity();
        entity.setEventId(UUID.randomUUID().toString());
        entity.setStationId(stationId);
        entity.setFaultCode("LINK_" + linkState.name());
        entity.setTitle(resolveEventTitle(linkState));
        entity.setDescription(reason);
        entity.setLevel(resolveEventLevel(linkState));
        entity.setStatus(linkState == LinkState.S0 ? "RESOLVED" : "ACTIVE");
        entity.setOccurredAt(occurredAt);
        entity.setRecoveredAt(linkState == LinkState.S0 ? occurredAt : null);
        faultEventRepository.save(entity);
    }

    private String resolveEventTitle(LinkState linkState) {
        return switch (linkState) {
            case S0 -> "链路恢复正常";
            case S1 -> "链路通信退化";
            case S2 -> "链路严重退化";
            case S3 -> "场站失联自治";
            case S4 -> "链路恢复同步";
        };
    }

    private String resolveEventLevel(LinkState linkState) {
        return switch (linkState) {
            case S0, S4 -> "提示";
            case S1 -> "预警";
            case S2, S3 -> "严重";
        };
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("序列化遥测消息失败", exception);
        }
    }
}

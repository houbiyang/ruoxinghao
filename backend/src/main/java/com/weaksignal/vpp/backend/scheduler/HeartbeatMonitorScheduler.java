package com.weaksignal.vpp.backend.scheduler;

import com.weaksignal.vpp.backend.application.StationStateTracker;
import com.weaksignal.vpp.backend.application.ExperimentFaultObservationService;
import com.weaksignal.vpp.backend.domain.LinkStateEvaluator;
import com.weaksignal.vpp.backend.domain.StateContext;
import com.weaksignal.vpp.backend.domain.StateDecision;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.FaultEventEntity;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.LinkStatusLogEntity;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.TelemetrySnapshotEntity;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.FaultEventRepository;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.LinkStatusLogRepository;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.StationRepository;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.TelemetrySnapshotRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 心跳超时检测调度器。
 */
@Component
public class HeartbeatMonitorScheduler {

    private final StationRepository stationRepository;
    private final TelemetrySnapshotRepository telemetrySnapshotRepository;
    private final StationStateTracker stationStateTracker;
    private final LinkStateEvaluator linkStateEvaluator;
    private final LinkStatusLogRepository linkStatusLogRepository;
    private final FaultEventRepository faultEventRepository;
    private final ExperimentFaultObservationService experimentFaultObservationService;
    private final Clock clock;

    /**
     * 注入调度器依赖。
     */
    public HeartbeatMonitorScheduler(
            StationRepository stationRepository,
            TelemetrySnapshotRepository telemetrySnapshotRepository,
            StationStateTracker stationStateTracker,
            LinkStateEvaluator linkStateEvaluator,
            LinkStatusLogRepository linkStatusLogRepository,
            FaultEventRepository faultEventRepository,
            ExperimentFaultObservationService experimentFaultObservationService,
            Clock clock
    ) {
        this.stationRepository = stationRepository;
        this.telemetrySnapshotRepository = telemetrySnapshotRepository;
        this.stationStateTracker = stationStateTracker;
        this.linkStateEvaluator = linkStateEvaluator;
        this.linkStatusLogRepository = linkStatusLogRepository;
        this.faultEventRepository = faultEventRepository;
        this.experimentFaultObservationService = experimentFaultObservationService;
        this.clock = clock;
    }

    /**
     * 定时检测心跳超时。
     */
    @Transactional
    @Scheduled(fixedDelay = 5000)
    public void monitorHeartbeat() {
        OffsetDateTime now = OffsetDateTime.now(clock);
        stationRepository.findAll().forEach(station -> {
            StateContext context = stationStateTracker.getOrCreate(station.getStationId(), now);
            StateDecision decision = linkStateEvaluator.evaluateHeartbeatTimeout(context, now);
            if (!decision.changed()) {
                return;
            }

            LinkStatusLogEntity statusLogEntity = new LinkStatusLogEntity();
            statusLogEntity.setStationId(station.getStationId());
            statusLogEntity.setPreviousState(context.currentState());
            statusLogEntity.setCurrentState(decision.nextState());
            statusLogEntity.setReason(decision.reason());
            statusLogEntity.setOccurredAt(now);
            linkStatusLogRepository.save(statusLogEntity);

            telemetrySnapshotRepository.findByStationId(station.getStationId()).ifPresent(snapshot -> {
                snapshot.setLinkState(decision.nextState());
                snapshot.setOnline(false);
                snapshot.setUpdatedAt(now);
                telemetrySnapshotRepository.save(snapshot);
            });

            FaultEventEntity faultEventEntity = new FaultEventEntity();
            faultEventEntity.setEventId(UUID.randomUUID().toString());
            faultEventEntity.setStationId(station.getStationId());
            faultEventEntity.setFaultCode("HEARTBEAT_TIMEOUT");
            faultEventEntity.setTitle("场站心跳超时");
            faultEventEntity.setDescription(decision.reason());
            faultEventEntity.setLevel("严重");
            faultEventEntity.setStatus("ACTIVE");
            faultEventEntity.setOccurredAt(now);
            faultEventRepository.save(faultEventEntity);

            stationStateTracker.update(
                    station.getStationId(),
                    new StateContext(
                            decision.nextState(),
                            now,
                            context.lastHeartbeatAt(),
                            context.lastSequenceNumber(),
                            context.lastDeviceTimestamp()
                    )
            );
            experimentFaultObservationService.observeLinkState(station.getStationId(), decision.nextState(), now);
        });
    }
}

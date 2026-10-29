package com.weaksignal.vpp.backend.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weaksignal.vpp.backend.config.RedisCacheProperties;
import com.weaksignal.vpp.backend.config.StateRuleProperties;
import com.weaksignal.vpp.backend.domain.LinkState;
import com.weaksignal.vpp.backend.domain.LinkStateEvaluator;
import com.weaksignal.vpp.backend.domain.ResourceType;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.TelemetryHistoryEntity;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.TelemetrySnapshotEntity;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.FaultEventRepository;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.LinkStatusLogRepository;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.TelemetryHistoryRepository;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.TelemetrySnapshotRepository;
import com.weaksignal.vpp.backend.infrastructure.redis.RedisRealtimeStateStore;
import com.weaksignal.vpp.backend.mqtt.dto.TelemetryReportMessage;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * 遥测处理服务测试。
 */
class TelemetryProcessingServiceTest {

    @Test
    void shouldIgnoreDuplicateMessageId() {
        AtomicInteger historySaveCount = new AtomicInteger();
        AtomicInteger snapshotSaveCount = new AtomicInteger();

        TelemetryHistoryRepository historyRepository = repositoryProxy(
                TelemetryHistoryRepository.class,
                Map.of(
                        "existsByMessageId", arguments -> true,
                        "save", arguments -> {
                            historySaveCount.incrementAndGet();
                            return arguments[0];
                        }
                )
        );
        TelemetrySnapshotRepository snapshotRepository = repositoryProxy(
                TelemetrySnapshotRepository.class,
                Map.of(
                        "findByStationId", arguments -> Optional.empty(),
                        "save", arguments -> {
                            snapshotSaveCount.incrementAndGet();
                            return arguments[0];
                        }
                )
        );

        TelemetryProcessingService service = createService(historyRepository, snapshotRepository);
        service.handleTelemetry(createMessage(10, "message-1"));

        assertEquals(0, historySaveCount.get());
        assertEquals(0, snapshotSaveCount.get());
    }

    @Test
    void shouldRejectRollbackSequenceNumber() {
        AtomicInteger historySaveCount = new AtomicInteger();
        AtomicInteger snapshotSaveCount = new AtomicInteger();

        TelemetrySnapshotEntity existing = new TelemetrySnapshotEntity();
        existing.setStationId("pv-gobi-01");
        existing.setSequenceNumber(12);
        existing.setDeviceTimestamp(OffsetDateTime.parse("2026-06-29T21:29:59+08:00"));
        existing.setLinkState(LinkState.S0);

        TelemetryHistoryRepository historyRepository = repositoryProxy(
                TelemetryHistoryRepository.class,
                Map.of(
                        "existsByMessageId", arguments -> false,
                        "save", arguments -> {
                            historySaveCount.incrementAndGet();
                            return arguments[0];
                        }
                )
        );
        TelemetrySnapshotRepository snapshotRepository = repositoryProxy(
                TelemetrySnapshotRepository.class,
                Map.of(
                        "findByStationId", arguments -> Optional.of(existing),
                        "save", arguments -> {
                            snapshotSaveCount.incrementAndGet();
                            return arguments[0];
                        }
                )
        );

        TelemetryProcessingService service = createService(historyRepository, snapshotRepository);
        service.handleTelemetry(createMessage(8, "message-2"));

        assertEquals(0, historySaveCount.get());
        assertEquals(0, snapshotSaveCount.get());
    }

    @Test
    void shouldPersistExperimentAssociationFields() {
        AtomicReference<TelemetryHistoryEntity> savedHistory = new AtomicReference<>();
        AtomicReference<TelemetrySnapshotEntity> savedSnapshot = new AtomicReference<>();
        TelemetryHistoryRepository historyRepository = repositoryProxy(
                TelemetryHistoryRepository.class,
                Map.of(
                        "existsByMessageId", arguments -> false,
                        "save", arguments -> {
                            savedHistory.set((TelemetryHistoryEntity) arguments[0]);
                            return arguments[0];
                        }
                )
        );
        TelemetrySnapshotRepository snapshotRepository = repositoryProxy(
                TelemetrySnapshotRepository.class,
                Map.of(
                        "findByStationId", arguments -> Optional.empty(),
                        "save", arguments -> {
                            savedSnapshot.set((TelemetrySnapshotEntity) arguments[0]);
                            return arguments[0];
                        }
                )
        );

        createService(historyRepository, snapshotRepository).handleTelemetry(createExperimentMessage(10, "message-experiment", "run-1", "COM_OFFLINE"));

        assertEquals("run-1", savedHistory.get().getExperimentRunId());
        assertEquals("COM_OFFLINE", savedHistory.get().getActiveFaultCode());
        assertEquals("run-1", savedSnapshot.get().getExperimentRunId());
        assertEquals("COM_OFFLINE", savedSnapshot.get().getActiveFaultCode());
    }

    @Test
    void shouldPersistNullAssociationForNormalTelemetryAndRefreshSnapshotAssociation() {
        AtomicReference<TelemetrySnapshotEntity> savedSnapshot = new AtomicReference<>();
        TelemetrySnapshotEntity existingSnapshot = new TelemetrySnapshotEntity();
        existingSnapshot.setStationId("pv-gobi-01");
        existingSnapshot.setSequenceNumber(9);
        existingSnapshot.setDeviceTimestamp(OffsetDateTime.parse("2026-06-29T21:29:57+08:00"));
        existingSnapshot.setExperimentRunId("run-old");
        existingSnapshot.setActiveFaultCode("COM_OFFLINE");
        TelemetryHistoryRepository historyRepository = repositoryProxy(
                TelemetryHistoryRepository.class,
                Map.of("existsByMessageId", arguments -> false, "save", arguments -> arguments[0])
        );
        TelemetrySnapshotRepository snapshotRepository = repositoryProxy(
                TelemetrySnapshotRepository.class,
                Map.of(
                        "findByStationId", arguments -> Optional.of(existingSnapshot),
                        "save", arguments -> {
                            savedSnapshot.set((TelemetrySnapshotEntity) arguments[0]);
                            return arguments[0];
                        }
                )
        );

        createService(historyRepository, snapshotRepository).handleTelemetry(createMessage(10, "message-normal"));

        assertNull(savedSnapshot.get().getExperimentRunId());
        assertNull(savedSnapshot.get().getActiveFaultCode());
    }

    @Test
    void shouldRefreshSnapshotWithLatestExperimentAssociation() {
        AtomicReference<TelemetrySnapshotEntity> savedSnapshot = new AtomicReference<>();
        TelemetrySnapshotEntity existingSnapshot = new TelemetrySnapshotEntity();
        existingSnapshot.setStationId("pv-gobi-01");
        existingSnapshot.setSequenceNumber(9);
        existingSnapshot.setDeviceTimestamp(OffsetDateTime.parse("2026-06-29T21:29:57+08:00"));
        existingSnapshot.setExperimentRunId("run-old");
        existingSnapshot.setActiveFaultCode("COM_OFFLINE");
        TelemetryHistoryRepository historyRepository = repositoryProxy(
                TelemetryHistoryRepository.class,
                Map.of("existsByMessageId", arguments -> false, "save", arguments -> arguments[0])
        );
        TelemetrySnapshotRepository snapshotRepository = repositoryProxy(
                TelemetrySnapshotRepository.class,
                Map.of(
                        "findByStationId", arguments -> Optional.of(existingSnapshot),
                        "save", arguments -> {
                            savedSnapshot.set((TelemetrySnapshotEntity) arguments[0]);
                            return arguments[0];
                        }
                )
        );

        createService(historyRepository, snapshotRepository).handleTelemetry(createExperimentMessage(10, "message-new-experiment", "run-new", "COM_OFFLINE"));

        assertEquals("run-new", savedSnapshot.get().getExperimentRunId());
        assertEquals("COM_OFFLINE", savedSnapshot.get().getActiveFaultCode());
    }

    private TelemetryProcessingService createService(
            TelemetryHistoryRepository historyRepository,
            TelemetrySnapshotRepository snapshotRepository
    ) {
        return new TelemetryProcessingService(
                snapshotRepository,
                historyRepository,
                repositoryProxy(LinkStatusLogRepository.class, Map.of("save", arguments -> arguments[0])),
                repositoryProxy(FaultEventRepository.class, Map.of("save", arguments -> arguments[0])),
                new RedisRealtimeStateStore(null, new ObjectMapper().findAndRegisterModules(), new RedisCacheProperties(45, 20)) {
                    @Override
                    public void saveLatest(com.weaksignal.vpp.backend.domain.StationRealtimeSnapshot snapshot) {
                    }

                    @Override
                    public void saveHeartbeat(String stationId, OffsetDateTime heartbeatAt) {
                    }
                },
                new LinkStateEvaluator(new StateRuleProperties(80, 1.0, 3, 150, 3.0, 6, 500, 20.0, 15, 15, 5, 6)),
                new StationStateTracker(),
                new StationLayoutRegistry(),
                mock(ExperimentClosedLoopOrchestrationService.class),
                new ObjectMapper().findAndRegisterModules(),
                Clock.fixed(OffsetDateTime.parse("2026-06-29T21:30:00+08:00").toInstant(), ZoneId.of("Asia/Shanghai"))
        );
    }

    private TelemetryReportMessage createMessage(long sequenceNumber, String messageId) {
        return new TelemetryReportMessage(
                messageId,
                "pv-gobi-01",
                "光伏",
                ResourceType.PHOTOVOLTAIC,
                sequenceNumber,
                OffsetDateTime.parse("2026-06-29T21:29:58+08:00"),
                OffsetDateTime.parse("2026-06-29T21:29:59+08:00"),
                120,
                160,
                36,
                0.3,
                true,
                "GOOD",
                LinkState.S0,
                Map.of("temperature", 28.0)
        );
    }

    private TelemetryReportMessage createExperimentMessage(
            long sequenceNumber,
            String messageId,
            String experimentRunId,
            String activeFaultCode
    ) {
        TelemetryReportMessage normalMessage = createMessage(sequenceNumber, messageId);
        return new TelemetryReportMessage(
                normalMessage.messageId(), normalMessage.stationId(), normalMessage.stationName(), normalMessage.resourceType(),
                normalMessage.sequenceNumber(), normalMessage.deviceTimestamp(), normalMessage.sendTimestamp(),
                normalMessage.activePower(), normalMessage.availableCapacity(), normalMessage.latency(), normalMessage.packetLossRate(),
                normalMessage.online(), normalMessage.qualityCode(), normalMessage.linkState(), experimentRunId, activeFaultCode,
                normalMessage.metrics()
        );
    }

    @SuppressWarnings("unchecked")
    private <T> T repositoryProxy(Class<T> type, Map<String, java.util.function.Function<Object[], Object>> handlers) {
        return (T) Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[]{type},
                (proxy, method, args) -> {
                    java.util.function.Function<Object[], Object> handler = handlers.get(method.getName());
                    if (handler != null) {
                        return handler.apply(args == null ? new Object[0] : args);
                    }
                    if (method.getName().equals("toString")) {
                        return type.getSimpleName() + "Proxy";
                    }
                    if (method.getReturnType().equals(boolean.class)) {
                        return false;
                    }
                    if (method.getReturnType().equals(int.class)) {
                        return 0;
                    }
                    if (method.getReturnType().equals(long.class)) {
                        return 0L;
                    }
                    return null;
                }
        );
    }
}

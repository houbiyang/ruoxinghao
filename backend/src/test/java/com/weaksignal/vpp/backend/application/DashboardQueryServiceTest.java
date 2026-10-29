package com.weaksignal.vpp.backend.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weaksignal.vpp.backend.api.dto.DashboardSummaryResponse;
import com.weaksignal.vpp.backend.config.RedisCacheProperties;
import com.weaksignal.vpp.backend.domain.DemoScenario;
import com.weaksignal.vpp.backend.domain.LinkState;
import com.weaksignal.vpp.backend.domain.ResourceType;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.ControlCommandEntity;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.FaultEventEntity;
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
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * 总览查询服务测试。
 */
class DashboardQueryServiceTest {

    @Test
    void shouldExcludeOfflineStationsFromOnlineCount() {
        StationEntity onlineStation = new StationEntity();
        onlineStation.setStationId("pv-gobi-01");
        onlineStation.setStationName("光伏");
        onlineStation.setResourceType(ResourceType.PHOTOVOLTAIC);

        StationEntity offlineStation = new StationEntity();
        offlineStation.setStationId("wind-ridge-02");
        offlineStation.setStationName("风电");
        offlineStation.setResourceType(ResourceType.WIND);

        TelemetrySnapshotEntity onlineSnapshot = new TelemetrySnapshotEntity();
        onlineSnapshot.setStationId("pv-gobi-01");
        onlineSnapshot.setActivePower(120);
        onlineSnapshot.setAvailableCapacity(160);
        onlineSnapshot.setLatency(32);
        onlineSnapshot.setPacketLossRate(0.2);
        onlineSnapshot.setDataAge(1);
        onlineSnapshot.setOnline(true);
        onlineSnapshot.setLinkState(LinkState.S0);

        TelemetrySnapshotEntity offlineSnapshot = new TelemetrySnapshotEntity();
        offlineSnapshot.setStationId("wind-ridge-02");
        offlineSnapshot.setActivePower(80);
        offlineSnapshot.setAvailableCapacity(180);
        offlineSnapshot.setLatency(999);
        offlineSnapshot.setPacketLossRate(100);
        offlineSnapshot.setDataAge(20);
        offlineSnapshot.setOnline(false);
        offlineSnapshot.setLinkState(LinkState.S3);

        FaultEventEntity eventEntity = new FaultEventEntity();
        eventEntity.setLevel("严重");
        eventEntity.setOccurredAt(OffsetDateTime.parse("2026-06-29T21:30:00+08:00"));

        StationRepository stationRepository = repositoryProxy(
                StationRepository.class,
                Map.of("findAll", arguments -> List.of(onlineStation, offlineStation))
        );
        TelemetrySnapshotRepository snapshotRepository = repositoryProxy(
                TelemetrySnapshotRepository.class,
                Map.of(
                        "findByStationId", arguments -> {
                            String stationId = String.valueOf(arguments[0]);
                            if ("pv-gobi-01".equals(stationId)) {
                                return Optional.of(onlineSnapshot);
                            }
                            if ("wind-ridge-02".equals(stationId)) {
                                return Optional.of(offlineSnapshot);
                            }
                            return Optional.empty();
                        }
                )
        );
        FaultEventRepository faultEventRepository = repositoryProxy(
                FaultEventRepository.class,
                Map.of("findAllByOrderByOccurredAtDesc", arguments -> List.of(eventEntity))
        );

        RedisRealtimeStateStore realtimeStateStore = new RedisRealtimeStateStore(
                null,
                new ObjectMapper().findAndRegisterModules(),
                new RedisCacheProperties(45, 20)
        ) {
            @Override
            public Optional<DemoScenario> getActiveScenario() {
                return Optional.of(DemoScenario.LOST);
            }
        };

        DashboardQueryService service = new DashboardQueryService(
                stationRepository,
                snapshotRepository,
                repositoryProxy(TelemetryHistoryRepository.class, Map.of()),
                repositoryProxy(ResourceDeviceRepository.class, Map.of()),
                faultEventRepository,
                new StationLayoutRegistry(),
                realtimeStateStore,
                repositoryProxy(ControlCommandRepository.class, Map.of()),
                new ObjectMapper().findAndRegisterModules(),
                Clock.fixed(OffsetDateTime.parse("2026-06-29T21:30:00+08:00").toInstant(), ZoneId.of("Asia/Shanghai"))
        );

        DashboardSummaryResponse summary = service.getDashboardSummary();

        assertEquals(1, summary.onlineStationCount());
        assertEquals(1, summary.alarmCount());
        assertEquals(DemoScenario.LOST, summary.activeScenario());
        assertEquals(200.0, summary.totalOutputPower());
    }

    @Test
    void shouldAggregateSimulatorPowerFlowByReceivedTimeBucket() {
        OffsetDateTime timestamp = OffsetDateTime.parse("2026-07-28T17:30:12+08:00");
        StationEntity photovoltaicStation = station("pv-gobi-01", ResourceType.PHOTOVOLTAIC);
        StationEntity windStation = station("wind-ridge-02", ResourceType.WIND);
        StationEntity loadStation = station("load-oasis-04", ResourceType.LOAD);
        StationEntity storageStation = station("storage-hub-03", ResourceType.STORAGE);

        TelemetryHistoryEntity photovoltaicTelemetry = telemetry("pv-gobi-01", timestamp, 120);
        TelemetryHistoryEntity windTelemetry = telemetry("wind-ridge-02", timestamp.plusSeconds(2), 80);
        TelemetryHistoryEntity loadTelemetry = telemetry("load-oasis-04", timestamp.plusSeconds(3), 140);
        TelemetryHistoryEntity storageTelemetry = telemetry("storage-hub-03", timestamp.plusSeconds(4), -60);

        DashboardQueryService service = new DashboardQueryService(
                repositoryProxy(StationRepository.class, Map.of("findAll", arguments -> List.of(
                        photovoltaicStation,
                        windStation,
                        loadStation,
                        storageStation
                ))),
                repositoryProxy(TelemetrySnapshotRepository.class, Map.of()),
                repositoryProxy(TelemetryHistoryRepository.class, Map.of(
                        "findByReceivedTimestampBetweenOrderByReceivedTimestampAsc", arguments -> List.of(
                                photovoltaicTelemetry,
                                windTelemetry,
                                loadTelemetry,
                                storageTelemetry
                        )
                )),
                repositoryProxy(ResourceDeviceRepository.class, Map.of()),
                repositoryProxy(FaultEventRepository.class, Map.of()),
                new StationLayoutRegistry(),
                new RedisRealtimeStateStore(null, new ObjectMapper().findAndRegisterModules(), new RedisCacheProperties(45, 20)),
                repositoryProxy(ControlCommandRepository.class, Map.of()),
                new ObjectMapper().findAndRegisterModules(),
                Clock.fixed(timestamp.toInstant(), ZoneId.of("Asia/Shanghai"))
        );

        var points = service.getPowerFlowTrend(timestamp.minusMinutes(1), timestamp.plusMinutes(1), 10);

        assertEquals(1, points.size());
        assertEquals(200.0, points.getFirst().generationPower());
        assertEquals(200.0, points.getFirst().consumptionPower());
        assertEquals(4, points.getFirst().sampledStationCount());
    }

    /**
     * 实时功率接口应直接返回模拟器逐场站遥测，保证无实验时也能绘制正常基线。
     */
    @Test
    void shouldReturnRealtimeSimulatorPowerTelemetryWithoutExperimentRun() {
        OffsetDateTime timestamp = OffsetDateTime.parse("2026-07-29T13:00:00+08:00");
        TelemetryHistoryEntity photovoltaicTelemetry = telemetry("pv-gobi-01", timestamp, 126.5);
        TelemetryHistoryEntity windTelemetry = telemetry("wind-ridge-02", timestamp.plusSeconds(1), 82.3);
        DashboardQueryService service = new DashboardQueryService(
                repositoryProxy(StationRepository.class, Map.of()),
                repositoryProxy(TelemetrySnapshotRepository.class, Map.of()),
                repositoryProxy(TelemetryHistoryRepository.class, Map.of(
                        "findByReceivedTimestampBetweenOrderByReceivedTimestampDesc",
                        arguments -> List.of(photovoltaicTelemetry, windTelemetry)
                )),
                repositoryProxy(ResourceDeviceRepository.class, Map.of()),
                repositoryProxy(FaultEventRepository.class, Map.of()),
                new StationLayoutRegistry(),
                new RedisRealtimeStateStore(null, new ObjectMapper().findAndRegisterModules(), new RedisCacheProperties(45, 20)),
                repositoryProxy(ControlCommandRepository.class, Map.of()),
                new ObjectMapper().findAndRegisterModules(),
                Clock.fixed(timestamp.toInstant(), ZoneId.of("Asia/Shanghai"))
        );

        var points = service.getRealtimePowerTelemetry(timestamp.minusMinutes(1), timestamp.plusMinutes(1), 100, 0);

        assertEquals(2, points.size());
        assertEquals("pv-gobi-01", points.getFirst().stationId());
        assertEquals(126.5, points.getFirst().activePower());
        assertEquals("S0", points.getFirst().linkState());
    }

    /**
     * 实时功率接口按场站和时间窗应只保留最新样本，避免重复传输高频遥测。
     */
    @Test
    void shouldDownsampleRealtimeTelemetryByStationAndTimeBucket() {
        OffsetDateTime timestamp = OffsetDateTime.parse("2026-07-29T13:00:00+08:00");
        TelemetryHistoryEntity olderTelemetry = telemetry("pv-gobi-01", timestamp, 100);
        TelemetryHistoryEntity latestTelemetry = telemetry("pv-gobi-01", timestamp.plusSeconds(8), 126.5);
        TelemetryHistoryEntity windTelemetry = telemetry("wind-ridge-02", timestamp.plusSeconds(5), 82.3);
        DashboardQueryService service = new DashboardQueryService(
                repositoryProxy(StationRepository.class, Map.of()),
                repositoryProxy(TelemetrySnapshotRepository.class, Map.of()),
                repositoryProxy(TelemetryHistoryRepository.class, Map.of(
                        "findByReceivedTimestampBetweenOrderByReceivedTimestampDesc",
                        arguments -> List.of(latestTelemetry, windTelemetry, olderTelemetry)
                )),
                repositoryProxy(ResourceDeviceRepository.class, Map.of()),
                repositoryProxy(FaultEventRepository.class, Map.of()),
                new StationLayoutRegistry(),
                new RedisRealtimeStateStore(null, new ObjectMapper().findAndRegisterModules(), new RedisCacheProperties(45, 20)),
                repositoryProxy(ControlCommandRepository.class, Map.of()),
                new ObjectMapper().findAndRegisterModules(),
                Clock.fixed(timestamp.toInstant(), ZoneId.of("Asia/Shanghai"))
        );

        var points = service.getRealtimePowerTelemetry(timestamp.minusMinutes(1), timestamp.plusMinutes(1), 100, 10);

        assertEquals(2, points.size());
        assertEquals(126.5, points.stream()
                .filter(point -> "pv-gobi-01".equals(point.stationId()))
                .findFirst()
                .orElseThrow()
                .activePower());
    }

    /**
     * 场站功率趋势只应展示真实执行指令的目标值，不得使用容量比例构造目标。
     */
    @Test
    void shouldUseExecutedCommandTargetInStationPowerTrend() {
        OffsetDateTime timestamp = OffsetDateTime.parse("2026-07-29T10:00:00+08:00");
        StationEntity thermalStation = station("thermal-plant-06", ResourceType.THERMAL);
        TelemetryHistoryEntity beforeCommand = telemetry("thermal-plant-06", timestamp, 0);
        TelemetryHistoryEntity afterCommand = telemetry("thermal-plant-06", timestamp.plusSeconds(10), 20);
        ControlCommandEntity command = new ControlCommandEntity();
        command.setCommandId("thermal-command-1");
        command.setStationId("thermal-plant-06");
        command.setCommandType("SET_ACTIVE_POWER");
        command.setCommandPayload("{\"targetPower\":88.7}");
        command.setSendTime(timestamp.plusSeconds(1));
        command.setExpireAt(timestamp.plusSeconds(21));
        command.setStatus("EXECUTED");
        command.setCreatedAt(timestamp.plusSeconds(1));

        DashboardQueryService service = new DashboardQueryService(
                repositoryProxy(StationRepository.class, Map.of(
                        "findByStationId", arguments -> Optional.of(thermalStation)
                )),
                repositoryProxy(TelemetrySnapshotRepository.class, Map.of(
                        "findByStationId", arguments -> Optional.empty()
                )),
                repositoryProxy(TelemetryHistoryRepository.class, Map.of(
                        "findByStationIdOrderByReceivedTimestampDesc", arguments -> List.of(afterCommand, beforeCommand)
                )),
                repositoryProxy(ResourceDeviceRepository.class, Map.of(
                        "findByStationId", arguments -> List.of()
                )),
                repositoryProxy(FaultEventRepository.class, Map.of(
                        "findAllByOrderByOccurredAtDesc", arguments -> List.of()
                )),
                new StationLayoutRegistry(),
                new RedisRealtimeStateStore(null, new ObjectMapper().findAndRegisterModules(), new RedisCacheProperties(45, 20)),
                repositoryProxy(ControlCommandRepository.class, Map.of(
                        "findByStationIdAndCommandTypeAndStatusAndSendTimeBetweenOrderBySendTimeAsc", arguments -> List.of(command)
                )),
                new ObjectMapper().findAndRegisterModules(),
                Clock.fixed(timestamp.toInstant(), ZoneId.of("Asia/Shanghai"))
        );

        var detail = service.getStationDetail("thermal-plant-06");

        assertEquals(null, detail.powerTrend().getFirst().target());
        assertEquals(88.7, detail.powerTrend().getLast().target());
    }

    private StationEntity station(String stationId, ResourceType resourceType) {
        StationEntity station = new StationEntity();
        station.setStationId(stationId);
        station.setStationName(stationId);
        station.setResourceType(resourceType);
        return station;
    }

    private TelemetryHistoryEntity telemetry(String stationId, OffsetDateTime timestamp, double activePower) {
        TelemetryHistoryEntity telemetry = new TelemetryHistoryEntity();
        telemetry.setStationId(stationId);
        telemetry.setReceivedTimestamp(timestamp);
        telemetry.setActivePower(activePower);
        telemetry.setAvailableCapacity(Math.max(activePower, 0));
        telemetry.setLatency(20);
        telemetry.setPacketLossRate(0.1);
        telemetry.setDataAge(0);
        telemetry.setLinkState(LinkState.S0);
        telemetry.setOnline(true);
        return telemetry;
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
                    if (method.getReturnType().equals(boolean.class)) {
                        return false;
                    }
                    return null;
                }
        );
    }
}

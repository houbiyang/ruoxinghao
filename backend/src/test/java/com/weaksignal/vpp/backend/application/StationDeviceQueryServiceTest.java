package com.weaksignal.vpp.backend.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.weaksignal.vpp.backend.api.ApiException;
import com.weaksignal.vpp.backend.api.dto.StationDevicePowerTrendResponse;
import com.weaksignal.vpp.backend.api.dto.StationDeviceSnapshotResponse;
import com.weaksignal.vpp.backend.domain.LinkState;
import com.weaksignal.vpp.backend.domain.ResourceType;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.ResourceDeviceEntity;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.StationEntity;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.TelemetryHistoryEntity;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.TelemetrySnapshotEntity;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.ResourceDeviceRepository;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.StationRepository;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.TelemetryHistoryRepository;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.TelemetrySnapshotRepository;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

/**
 * 场站子设备查询服务测试。
 */
class StationDeviceQueryServiceTest {

    @Test
    void shouldReturnFivePhotovoltaicDevicesAndKeepWeatherPowerEmpty() {
        StationDeviceQueryService service = buildService();

        List<StationDeviceSnapshotResponse> devices = service.getDevices("pv-gobi-01");

        assertEquals(5, devices.size());
        assertEquals("inverter-group", devices.get(2).deviceId());
        assertNull(devices.get(4).activePower());
    }

    @Test
    void shouldThrowNotFoundForMissingDevice() {
        StationDeviceQueryService service = buildService();

        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.getDeviceDetail("pv-gobi-01", "missing-device")
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
    }

    @Test
    void shouldBuildWeatherTrendFromStationHistory() {
        StationDeviceQueryService service = buildService();

        StationDevicePowerTrendResponse response = service.getPowerTrend(
                "pv-gobi-01",
                "weather-station-01",
                OffsetDateTime.parse("2026-07-03T14:00:00+08:00"),
                OffsetDateTime.parse("2026-07-03T14:20:00+08:00"),
                10
        );

        assertEquals("environment", response.mode());
        assertEquals(2, response.points().size());
        assertNull(service.getDeviceDetail("pv-gobi-01", "weather-station-01").activePower());
    }

    private StationDeviceQueryService buildService() {
        StationEntity station = new StationEntity();
        station.setStationId("pv-gobi-01");
        station.setStationName("光伏");
        station.setResourceType(ResourceType.PHOTOVOLTAIC);

        TelemetrySnapshotEntity snapshot = new TelemetrySnapshotEntity();
        snapshot.setStationId("pv-gobi-01");
        snapshot.setActivePower(126.0);
        snapshot.setAvailableCapacity(168.0);
        snapshot.setLatency(43);
        snapshot.setPacketLossRate(0.2);
        snapshot.setDataAge(1);
        snapshot.setLinkState(LinkState.S0);
        snapshot.setOnline(true);
        snapshot.setReceivedTimestamp(OffsetDateTime.parse("2026-07-03T15:18:00+08:00"));

        List<ResourceDeviceEntity> devices = List.of(
                device("pv-array-a", "组件方阵 A", "pv-array", "RUNNING"),
                device("pv-array-b", "组件方阵 B", "pv-array", "RUNNING"),
                device("inverter-group", "逆变器组", "inverter-group", "RUNNING"),
                device("combiner-box-01", "汇流箱", "combiner-box", "RUNNING"),
                device("weather-station-01", "气象站", "weather-station", "RUNNING")
        );

        TelemetryHistoryEntity first = new TelemetryHistoryEntity();
        first.setStationId("pv-gobi-01");
        first.setReceivedTimestamp(OffsetDateTime.parse("2026-07-03T15:10:00+08:00"));
        first.setActivePower(118);
        first.setAvailableCapacity(168);
        first.setLatency(41);
        first.setPacketLossRate(0.2);
        first.setDataAge(1);
        first.setLinkState(LinkState.S0);
        first.setOnline(true);

        TelemetryHistoryEntity second = new TelemetryHistoryEntity();
        second.setStationId("pv-gobi-01");
        second.setReceivedTimestamp(OffsetDateTime.parse("2026-07-03T15:15:00+08:00"));
        second.setActivePower(123);
        second.setAvailableCapacity(168);
        second.setLatency(44);
        second.setPacketLossRate(0.3);
        second.setDataAge(1);
        second.setLinkState(LinkState.S0);
        second.setOnline(true);

        StationRepository stationRepository = repositoryProxy(
                StationRepository.class,
                Map.of("findByStationId", arguments -> Optional.of(station))
        );
        ResourceDeviceRepository resourceDeviceRepository = repositoryProxy(
                ResourceDeviceRepository.class,
                Map.of(
                        "findByStationId", arguments -> devices,
                        "findByStationIdAndDeviceId", arguments -> devices.stream()
                                .filter(device -> device.getStationId().equals(arguments[0]) && device.getDeviceId().equals(arguments[1]))
                                .findFirst()
                )
        );
        TelemetrySnapshotRepository telemetrySnapshotRepository = repositoryProxy(
                TelemetrySnapshotRepository.class,
                Map.of("findByStationId", arguments -> Optional.of(snapshot))
        );
        TelemetryHistoryRepository telemetryHistoryRepository = repositoryProxy(
                TelemetryHistoryRepository.class,
                Map.of(
                        "findByStationIdAndReceivedTimestampBetweenOrderByReceivedTimestampAsc", arguments -> List.of(first, second)
                )
        );

        return new StationDeviceQueryService(
                stationRepository,
                resourceDeviceRepository,
                telemetrySnapshotRepository,
                telemetryHistoryRepository,
                Clock.fixed(OffsetDateTime.parse("2026-07-03T15:20:00+08:00").toInstant(), ZoneId.of("Asia/Shanghai"))
        );
    }

    private ResourceDeviceEntity device(String deviceId, String deviceName, String deviceType, String status) {
        ResourceDeviceEntity entity = new ResourceDeviceEntity();
        entity.setDeviceId(deviceId);
        entity.setStationId("pv-gobi-01");
        entity.setDeviceName(deviceName);
        entity.setDeviceType(deviceType);
        entity.setStatus(status);
        entity.setUpdatedAt(OffsetDateTime.parse("2026-07-03T15:18:00+08:00"));
        return entity;
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
                    if (Pageable.class.isAssignableFrom(method.getReturnType())) {
                        return Pageable.unpaged();
                    }
                    if (method.getReturnType().equals(boolean.class)) {
                        return false;
                    }
                    return null;
                }
        );
    }
}

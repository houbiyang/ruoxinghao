package com.weaksignal.vpp.backend.capability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weaksignal.vpp.backend.config.PlanningProperties;
import com.weaksignal.vpp.backend.domain.LinkState;
import com.weaksignal.vpp.backend.domain.ResourceType;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.StationEntity;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.TelemetrySnapshotEntity;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.ResourceCapabilitySnapshotRepository;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.StationRepository;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.TelemetrySnapshotRepository;
import com.weaksignal.vpp.backend.environment.EnvironmentAssessmentService;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 资源能力服务测试。
 */
class ResourceCapabilityServiceTest {

    private StationRepository stationRepository;
    private TelemetrySnapshotRepository telemetrySnapshotRepository;
    private ResourceCapabilityService resourceCapabilityService;
    private Clock clock;

    @BeforeEach
    void setUp() {
        stationRepository = mock(StationRepository.class);
        telemetrySnapshotRepository = mock(TelemetrySnapshotRepository.class);
        ResourceCapabilitySnapshotRepository resourceCapabilitySnapshotRepository = mock(ResourceCapabilitySnapshotRepository.class);
        clock = Clock.fixed(Instant.parse("2026-07-04T00:00:00Z"), ZoneId.of("Asia/Shanghai"));
        resourceCapabilityService = new ResourceCapabilityService(
                stationRepository,
                telemetrySnapshotRepository,
                resourceCapabilitySnapshotRepository,
                new ObjectMapper().findAndRegisterModules(),
                new EnvironmentAssessmentService(new ObjectMapper().findAndRegisterModules()),
                new PlanningProperties(60, 15),
                clock
        );
    }

    @Test
    void shouldApplyLinkStateCapacityReduction() {
        StationEntity stableStation = station("pv-gobi-01", ResourceType.PHOTOVOLTAIC);
        StationEntity degradedStation = station("wind-ridge-02", ResourceType.WIND);
        StationEntity lostStation = station("grid-link-00", ResourceType.GRID);
        when(stationRepository.findAll()).thenReturn(List.of(stableStation, degradedStation, lostStation));
        when(telemetrySnapshotRepository.findByStationId("pv-gobi-01"))
                .thenReturn(Optional.of(snapshot("pv-gobi-01", 120.0, LinkState.S0, true, 4,
                        "{\"metrics\":{\"irradiance\":800.0,\"cloudCover\":0.0,\"moduleTemperature\":25.0,"
                                + "\"soilingLossRate\":0.0,\"snowCover\":0.0,\"shadingLossRate\":0.0}}")));
        when(telemetrySnapshotRepository.findByStationId("wind-ridge-02"))
                .thenReturn(Optional.of(snapshot("wind-ridge-02", 200.0, LinkState.S1, true, 4,
                        "{\"metrics\":{\"windSpeed\":12.0,\"turbulenceIntensity\":0.12,\"airDensity\":1.225,"
                                + "\"icingRisk\":0.0,\"gustSpeed\":12.0,\"yawDeviation\":0.0}}")));
        when(telemetrySnapshotRepository.findByStationId("grid-link-00"))
                .thenReturn(Optional.of(snapshot("grid-link-00", 180.0, LinkState.S3, true, 4, "{\"metrics\":{}}")));

        List<ResourceCapabilitySnapshot> snapshots = resourceCapabilityService.createCapabilitySnapshots();

        ResourceCapabilitySnapshot gridSnapshot = snapshots.stream()
                .filter(snapshot -> snapshot.stationId().equals("grid-link-00"))
                .findFirst()
                .orElseThrow();
        ResourceCapabilitySnapshot photovoltaicSnapshot = snapshots.stream()
                .filter(snapshot -> snapshot.stationId().equals("pv-gobi-01"))
                .findFirst()
                .orElseThrow();
        ResourceCapabilitySnapshot windSnapshot = snapshots.stream()
                .filter(snapshot -> snapshot.stationId().equals("wind-ridge-02"))
                .findFirst()
                .orElseThrow();

        assertEquals(120.0, photovoltaicSnapshot.availableCapacity());
        assertEquals(170.0, windSnapshot.availableCapacity());
        assertEquals(0.0, gridSnapshot.availableCapacity());
        assertTrue(!gridSnapshot.dataTrusted());
    }

    @Test
    void shouldLimitStorageBySoc() {
        StationEntity storageStation = station("storage-hub-03", ResourceType.STORAGE);
        when(stationRepository.findAll()).thenReturn(List.of(storageStation));
        when(telemetrySnapshotRepository.findByStationId("storage-hub-03"))
                .thenReturn(Optional.of(snapshot(
                        "storage-hub-03",
                        120.0,
                        LinkState.S0,
                        true,
                        3,
                        "{\"metrics\":{\"soc\":12.0}}"
                )));

        ResourceCapabilitySnapshot snapshot = resourceCapabilityService.createCapabilitySnapshots().getFirst();

        assertEquals(12.0, snapshot.storageConstraint().soc());
        assertTrue(snapshot.maximumPower() <= 8.0);
        assertTrue(snapshot.minimumPower() < 0.0);
    }

    @Test
    void shouldNotApplyEnvironmentDeratingTwice() {
        StationEntity photovoltaicStation = station("pv-gobi-01", ResourceType.PHOTOVOLTAIC);
        when(stationRepository.findAll()).thenReturn(List.of(photovoltaicStation));
        when(telemetrySnapshotRepository.findByStationId("pv-gobi-01"))
                .thenReturn(Optional.of(snapshot(
                        "pv-gobi-01",
                        40.0,
                        LinkState.S0,
                        true,
                        3,
                        "{\"metrics\":{\"irradiance\":160.0,\"cloudCover\":0.85,\"moduleTemperature\":45.0,"
                                + "\"conversionEfficiency\":0.8,\"soilingLossRate\":0.0,\"snowCover\":0.0,"
                                + "\"shadingLossRate\":0.0}}"
                )));

        ResourceCapabilitySnapshot snapshot = resourceCapabilityService.createCapabilitySnapshots().getFirst();

        assertEquals(40.0, snapshot.availableCapacity());
        assertEquals(40.0, snapshot.maximumPower());
        assertTrue(snapshot.environmentAssessment().environmentFactor() < 1.0);
    }

    @Test
    void shouldCapStoragePowerByTelemetryAvailableCapacity() {
        StationEntity storageStation = station("storage-hub-03", ResourceType.STORAGE);
        when(stationRepository.findAll()).thenReturn(List.of(storageStation));
        when(telemetrySnapshotRepository.findByStationId("storage-hub-03"))
                .thenReturn(Optional.of(snapshot(
                        "storage-hub-03",
                        30.0,
                        LinkState.S0,
                        true,
                        3,
                        "{\"metrics\":{\"soc\":50.0}}"
                )));

        ResourceCapabilitySnapshot snapshot = resourceCapabilityService.createCapabilitySnapshots().getFirst();

        assertEquals(30.0, snapshot.storageConstraint().maximumChargePowerKW());
        assertEquals(30.0, snapshot.storageConstraint().maximumDischargePowerKW());
        assertEquals(-30.0, snapshot.minimumPower());
        assertEquals(30.0, snapshot.maximumPower());
    }

    @Test
    void shouldKeepTrustedS4CapabilityAvailableForRecoverySynchronization() {
        StationEntity photovoltaicStation = station("pv-gobi-01", ResourceType.PHOTOVOLTAIC);
        when(stationRepository.findAll()).thenReturn(List.of(photovoltaicStation));
        when(telemetrySnapshotRepository.findByStationId("pv-gobi-01"))
                .thenReturn(Optional.of(snapshot(
                        "pv-gobi-01",
                        120.0,
                        LinkState.S4,
                        true,
                        4,
                        "{\"metrics\":{\"irradiance\":800.0,\"cloudCover\":0.0,\"moduleTemperature\":25.0,"
                                + "\"soilingLossRate\":0.0,\"snowCover\":0.0,\"shadingLossRate\":0.0}}"
                )));

        ResourceCapabilitySnapshot snapshot = resourceCapabilityService.createCapabilitySnapshots().getFirst();

        assertTrue(snapshot.dataTrusted());
        assertEquals(120.0, snapshot.maximumPower());
        assertEquals(0.76, snapshot.capabilityConfidence());
    }

    @Test
    void shouldKeepUnavailableResourceAssessmentTemporarilyValid() {
        StationEntity staleStation = station("thermal-plant-06", ResourceType.THERMAL);
        when(stationRepository.findAll()).thenReturn(List.of(staleStation));
        TelemetrySnapshotEntity staleTelemetry = snapshot("thermal-plant-06", 96.0, LinkState.S3, false, 120, "{\"metrics\":{}}");
        staleTelemetry.setReceivedTimestamp(OffsetDateTime.now(clock).minusMinutes(10));
        when(telemetrySnapshotRepository.findByStationId("thermal-plant-06")).thenReturn(Optional.of(staleTelemetry));

        ResourceCapabilitySnapshot snapshot = resourceCapabilityService.createCapabilitySnapshots().getFirst();

        assertTrue(!snapshot.dataTrusted());
        assertEquals(0.0, snapshot.availableCapacity());
        assertTrue(snapshot.validUntil().isAfter(OffsetDateTime.now(clock)));
    }

    private StationEntity station(String stationId, ResourceType resourceType) {
        StationEntity station = new StationEntity();
        station.setStationId(stationId);
        station.setStationName(stationId);
        station.setResourceType(resourceType);
        station.setEnabled(true);
        return station;
    }

    private TelemetrySnapshotEntity snapshot(
            String stationId,
            double availableCapacity,
            LinkState linkState,
            boolean online,
            long dataAge,
            String payloadJson
    ) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        TelemetrySnapshotEntity snapshot = new TelemetrySnapshotEntity();
        snapshot.setStationId(stationId);
        snapshot.setMessageId("message-" + stationId);
        snapshot.setSequenceNumber(1L);
        snapshot.setDeviceTimestamp(now.minusSeconds(dataAge));
        snapshot.setReceivedTimestamp(now);
        snapshot.setActivePower(availableCapacity * 0.4);
        snapshot.setAvailableCapacity(availableCapacity);
        snapshot.setLatency(30);
        snapshot.setPacketLossRate(0.2);
        snapshot.setDataAge(dataAge);
        snapshot.setLinkState(linkState);
        snapshot.setQualityCode("GOOD");
        snapshot.setOnline(online);
        snapshot.setPayloadJson(payloadJson);
        snapshot.setUpdatedAt(now);
        return snapshot;
    }
}

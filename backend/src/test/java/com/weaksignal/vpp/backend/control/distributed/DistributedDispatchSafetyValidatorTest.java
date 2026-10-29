package com.weaksignal.vpp.backend.control.distributed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.weaksignal.vpp.backend.domain.LinkState;
import com.weaksignal.vpp.backend.domain.ResourceType;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.TelemetrySnapshotEntity;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 分布式调度安全投影和回传复核测试。
 */
class DistributedDispatchSafetyValidatorTest {

    private final DistributedDispatchSafetyValidator validator = new DistributedDispatchSafetyValidator();

    /**
     * 非储能资源不得下发负功率，且目标应受实时爬坡限制。
     */
    @Test
    void shouldProjectNonStorageTargetToPowerAndRampBounds() {
        TelemetrySnapshotEntity snapshot = snapshot("thermal-01", 40.0, 100.0, LinkState.S1, true);

        DistributedDispatchSafetyValidator.TargetValidation validation = validator.validateTarget(
                decision("thermal-01", -20.0, true), snapshot, ResourceType.THERMAL, 10.0
        );

        assertTrue(validation.executable());
        assertEquals(30.0, validation.projectedPowerKw());
        assertEquals(30.0, validation.rampLowerBoundKw());
        assertEquals(50.0, validation.rampUpperBoundKw());
    }

    /**
     * 储能允许双向功率，但必须同时受容量和爬坡限制。
     */
    @Test
    void shouldKeepStorageTargetWithinSignedCapacityAndRampBounds() {
        TelemetrySnapshotEntity snapshot = snapshot("storage-01", -20.0, 30.0, LinkState.S0, true);

        DistributedDispatchSafetyValidator.TargetValidation validation = validator.validateTarget(
                decision("storage-01", -80.0, true), snapshot, ResourceType.STORAGE, 8.0
        );

        assertTrue(validation.executable());
        assertEquals(-28.0, validation.projectedPowerKw());
        assertEquals(-28.0, validation.rampLowerBoundKw());
        assertEquals(-12.0, validation.rampUpperBoundKw());
    }

    /**
     * 电网联络线允许在容量和爬坡边界内双向购售电。
     */
    @Test
    void shouldKeepGridExportTargetWithinSignedCapacityAndRampBounds() {
        TelemetrySnapshotEntity snapshot = snapshot("grid-01", 5.0, 100.0, LinkState.S0, true);

        DistributedDispatchSafetyValidator.TargetValidation validation = validator.validateTarget(
                decision("grid-01", -20.0, true), snapshot, ResourceType.GRID, 30.0
        );

        assertTrue(validation.executable());
        assertEquals(-20.0, validation.projectedPowerKw());
        assertEquals(-25.0, validation.rampLowerBoundKw());
        assertEquals(35.0, validation.rampUpperBoundKw());
    }

    /**
     * S3 失联场站只能由本地自治处理，禁止形成远程命令。
     */
    @Test
    void shouldBlockRemoteTargetForS3Station() {
        TelemetrySnapshotEntity snapshot = snapshot("grid-01", 20.0, 100.0, LinkState.S3, true);

        DistributedDispatchSafetyValidator.TargetValidation validation = validator.validateTarget(
                decision("grid-01", 30.0, true), snapshot, ResourceType.GRID, 10.0
        );

        assertFalse(validation.executable());
        assertEquals("LOCAL_AUTONOMY_OR_OFFLINE", validation.reasonCode());
    }

    /**
     * 回传遥测应拒绝非储能负功率和超过可用容量的功率。
     */
    @Test
    void shouldDetectOutOfRangeTelemetry() {
        TelemetrySnapshotEntity thermal = snapshot("thermal-01", 105.0, 100.0, LinkState.S0, true);
        TelemetrySnapshotEntity storage = snapshot("storage-01", -31.0, 30.0, LinkState.S0, true);

        DistributedDispatchSafetyValidator.TelemetryValidation validation = validator.validateTelemetry(
                List.of(thermal, storage), stationId -> "thermal-01".equals(stationId) ? ResourceType.THERMAL : ResourceType.STORAGE
        );

        assertFalse(validation.valid());
        assertEquals(2, validation.violations().size());
    }

    private DistributedDispatchAgent.DistributedDispatchDecision decision(String stationId, double targetPower, boolean safeToExecute) {
        return new DistributedDispatchAgent.DistributedDispatchDecision(
                stationId, 1, LinkState.S0, "TEST", 0.0, 0.0, targetPower, targetPower, false, safeToExecute
        );
    }

    private TelemetrySnapshotEntity snapshot(String stationId, double activePower, double capacity, LinkState linkState, boolean online) {
        TelemetrySnapshotEntity snapshot = new TelemetrySnapshotEntity();
        snapshot.setStationId(stationId);
        snapshot.setActivePower(activePower);
        snapshot.setAvailableCapacity(capacity);
        snapshot.setLinkState(linkState);
        snapshot.setOnline(online);
        return snapshot;
    }
}

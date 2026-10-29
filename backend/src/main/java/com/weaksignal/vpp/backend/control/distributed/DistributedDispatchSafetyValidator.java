package com.weaksignal.vpp.backend.control.distributed;

import com.weaksignal.vpp.backend.domain.LinkState;
import com.weaksignal.vpp.backend.domain.ResourceType;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.TelemetrySnapshotEntity;
import java.util.ArrayList;
import java.util.List;

/**
 * 分布式调度的下发前功率投影和遥测回传约束校验器。
 */
public class DistributedDispatchSafetyValidator {

    /**
     * 将一致性算法输出投影到当前实时容量、符号和爬坡边界内。
     *
     * @param decision 一致性候选决策
     * @param snapshot 场站最新遥测快照
     * @param resourceType 场站资源类型
     * @param maximumRampRate 最大单次爬坡，单位 kW
     * @return 可执行性与投影后的安全目标
     */
    public TargetValidation validateTarget(
            DistributedDispatchAgent.DistributedDispatchDecision decision,
            TelemetrySnapshotEntity snapshot,
            ResourceType resourceType,
            double maximumRampRate
    ) {
        if (decision == null || snapshot == null || resourceType == null || !Double.isFinite(maximumRampRate)
                || maximumRampRate < 0) {
            return TargetValidation.blocked("INVALID_CONTROL_INPUT", 0.0, 0.0, 0.0);
        }
        double capacity = Math.max(0.0, snapshot.getAvailableCapacity());
        if (!decision.safeToExecute() || !snapshot.isOnline() || snapshot.getLinkState() == LinkState.S3) {
            return TargetValidation.blocked("LOCAL_AUTONOMY_OR_OFFLINE", decision.targetPowerKw(), capacity, maximumRampRate);
        }
        if (!Double.isFinite(decision.targetPowerKw()) || !Double.isFinite(snapshot.getActivePower())) {
            return TargetValidation.blocked("NON_FINITE_POWER", decision.targetPowerKw(), capacity, maximumRampRate);
        }
        double lowerBound = resourceType == ResourceType.STORAGE || resourceType == ResourceType.GRID ? -capacity : 0.0;
        double upperBound = capacity;
        double rampLowerBound = Math.max(lowerBound, snapshot.getActivePower() - maximumRampRate);
        double rampUpperBound = Math.min(upperBound, snapshot.getActivePower() + maximumRampRate);
        double targetPower = clamp(decision.targetPowerKw(), rampLowerBound, rampUpperBound);
        String reason = targetPower == decision.targetPowerKw() ? "WITHIN_LIMITS" : "PROJECTED_TO_LIMITS";
        return new TargetValidation(true, reason, decision.targetPowerKw(), targetPower, lowerBound, upperBound,
                rampLowerBound, rampUpperBound, capacity, maximumRampRate);
    }

    /**
     * 校验回传遥测功率是否仍在当前资源允许区间内。
     *
     * @param snapshots 最新遥测快照集合
     * @param resourceTypeResolver 按场站解析资源类型的函数
     * @return 回传功率的约束校验结果
     */
    public TelemetryValidation validateTelemetry(
            List<TelemetrySnapshotEntity> snapshots,
            java.util.function.Function<String, ResourceType> resourceTypeResolver
    ) {
        List<TelemetryViolation> violations = new ArrayList<>();
        if (snapshots == null || resourceTypeResolver == null) {
            return new TelemetryValidation(false, List.of(new TelemetryViolation("UNKNOWN", "INVALID_TELEMETRY_INPUT", 0.0, 0.0)));
        }
        for (TelemetrySnapshotEntity snapshot : snapshots) {
            ResourceType resourceType = resourceTypeResolver.apply(snapshot.getStationId());
            if (resourceType == null || !Double.isFinite(snapshot.getActivePower()) || !Double.isFinite(snapshot.getAvailableCapacity())) {
                violations.add(new TelemetryViolation(snapshot.getStationId(), "NON_FINITE_OR_UNKNOWN_RESOURCE", snapshot.getActivePower(), snapshot.getAvailableCapacity()));
                continue;
            }
            double capacity = Math.max(0.0, snapshot.getAvailableCapacity());
            boolean valid = resourceType == ResourceType.STORAGE || resourceType == ResourceType.GRID
                    ? Math.abs(snapshot.getActivePower()) <= capacity + 0.001
                    : snapshot.getActivePower() >= -0.001 && snapshot.getActivePower() <= capacity + 0.001;
            if (!valid) {
                violations.add(new TelemetryViolation(snapshot.getStationId(), "POWER_OUT_OF_RANGE", snapshot.getActivePower(), capacity));
            }
        }
        return new TelemetryValidation(violations.isEmpty(), List.copyOf(violations));
    }

    private double clamp(double value, double lowerBound, double upperBound) {
        return Math.max(lowerBound, Math.min(upperBound, value));
    }

    /**
     * 单条候选控制目标的安全校验结果。
     *
     * @param executable 是否允许远程下发
     * @param reasonCode 校验结果编码
     * @param requestedPowerKw 原始候选功率
     * @param projectedPowerKw 投影后安全功率
     * @param lowerBoundKw 资源下界
     * @param upperBoundKw 资源上界
     * @param rampLowerBoundKw 含爬坡的下界
     * @param rampUpperBoundKw 含爬坡的上界
     * @param availableCapacityKw 当前可用容量
     * @param maximumRampRateKw 最大单次爬坡
     */
    public record TargetValidation(
            boolean executable,
            String reasonCode,
            double requestedPowerKw,
            double projectedPowerKw,
            double lowerBoundKw,
            double upperBoundKw,
            double rampLowerBoundKw,
            double rampUpperBoundKw,
            double availableCapacityKw,
            double maximumRampRateKw
    ) {
        private static TargetValidation blocked(String reasonCode, double requestedPowerKw, double capacity, double maximumRampRate) {
            return new TargetValidation(false, reasonCode, requestedPowerKw, 0.0, 0.0, 0.0, 0.0, 0.0, capacity, maximumRampRate);
        }
    }

    /**
     * 一条回传遥测的功率越界证据。
     *
     * @param stationId 场站标识
     * @param reasonCode 越界原因
     * @param activePowerKw 实际有功功率
     * @param availableCapacityKw 当前可用容量
     */
    public record TelemetryViolation(String stationId, String reasonCode, double activePowerKw, double availableCapacityKw) {
    }

    /**
     * 一组回传遥测的约束校验结果。
     *
     * @param valid 是否全部满足约束
     * @param violations 越界证据
     */
    public record TelemetryValidation(boolean valid, List<TelemetryViolation> violations) {
    }
}

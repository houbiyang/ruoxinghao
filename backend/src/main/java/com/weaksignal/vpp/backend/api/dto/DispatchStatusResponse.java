package com.weaksignal.vpp.backend.api.dto;

import com.weaksignal.vpp.backend.domain.ControlMode;
import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 调度执行状态摘要。
 *
 * @param controlMode 当前控制模式
 * @param adjustableStationCount 在线可调场站数
 * @param adjustableCapacity 可调总容量，单位 kW
 * @param currentPlanTarget 当前计划目标，单位 kW
 * @param successRate 指令执行成功率
 * @param rejectionRate 指令拒绝率
 * @param pendingCommandCount 待执行指令数
 * @param timeoutCount 超时指令数
 * @param statusCounts 状态数量分布
 * @param latestBatchId 最近批次标识
 * @param updatedAt 更新时间
 */
public record DispatchStatusResponse(
        ControlMode controlMode,
        int adjustableStationCount,
        double adjustableCapacity,
        double currentPlanTarget,
        double successRate,
        double rejectionRate,
        long pendingCommandCount,
        long timeoutCount,
        Map<String, Long> statusCounts,
        String latestBatchId,
        OffsetDateTime updatedAt
) {
}

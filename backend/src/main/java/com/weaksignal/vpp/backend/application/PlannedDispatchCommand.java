package com.weaksignal.vpp.backend.application;

import com.weaksignal.vpp.backend.domain.ControlMode;

/**
 * 计划执行派生出的场站控制指令。
 *
 * @param stationId 场站标识
 * @param targetPower 目标功率，单位 kW
 * @param controlMode 控制模式
 * @param maximumRampRate 最大爬坡速率，单位 kW/步长
 * @param commandTimeoutSeconds 指令超时秒数
 * @param dispatchPlanId 调度计划标识
 * @param planVersion 调度计划版本
 * @param dispatchPlanItemId 调度计划时间片标识
 * @param inputSnapshotId 输入快照标识
 */
public record PlannedDispatchCommand(
        String stationId,
        double targetPower,
        ControlMode controlMode,
        double maximumRampRate,
        int commandTimeoutSeconds,
        String dispatchPlanId,
        String planVersion,
        String dispatchPlanItemId,
        String inputSnapshotId
) {
}

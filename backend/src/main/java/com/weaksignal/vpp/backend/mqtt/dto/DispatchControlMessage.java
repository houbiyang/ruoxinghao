package com.weaksignal.vpp.backend.mqtt.dto;

import com.weaksignal.vpp.backend.domain.ControlMode;
import java.time.OffsetDateTime;

/**
 * 场站有功功率控制消息。
 *
 * @param commandId 指令标识
 * @param batchId 调度批次标识
 * @param dispatchPlanId 调度计划标识
 * @param planVersion 计划版本号
 * @param dispatchPlanItemId 调度计划项标识
 * @param inputSnapshotId 输入快照标识
 * @param stationId 场站标识
 * @param commandType 指令类型
 * @param targetPower 目标功率，单位 kW
 * @param issuedAt 下发时间
 * @param expireAt 过期时间
 * @param controlMode 控制模式
 * @param maximumRampRate 最大爬坡速率，单位 kW/周期
 */
public record DispatchControlMessage(
        String commandId,
        String batchId,
        String dispatchPlanId,
        String planVersion,
        String dispatchPlanItemId,
        String inputSnapshotId,
        String stationId,
        String commandType,
        double targetPower,
        OffsetDateTime issuedAt,
        OffsetDateTime expireAt,
        ControlMode controlMode,
        double maximumRampRate
) {
}

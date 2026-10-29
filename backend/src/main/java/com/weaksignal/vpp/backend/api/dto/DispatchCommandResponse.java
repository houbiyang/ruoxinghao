package com.weaksignal.vpp.backend.api.dto;

import com.weaksignal.vpp.backend.domain.ControlMode;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 调度控制指令响应。
 *
 * @param commandId 指令标识
 * @param batchId 批次标识
 * @param dispatchPlanId 调度计划标识
 * @param planVersion 调度计划版本
 * @param dispatchPlanItemId 调度计划时间片标识
 * @param inputSnapshotId 输入快照标识
 * @param stationId 场站标识
 * @param targetPower 目标功率，单位 kW
 * @param controlMode 控制模式
 * @param status 当前状态
 * @param sendTime 下发时间
 * @param expireAt 过期时间
 * @param latestAckStage 最新回执阶段
 * @param latestAckMessage 最新回执说明
 * @param latestAckTime 最新回执时间
 * @param acknowledgements 全部回执
 */
public record DispatchCommandResponse(
        String commandId,
        String batchId,
        String dispatchPlanId,
        String planVersion,
        String dispatchPlanItemId,
        String inputSnapshotId,
        String stationId,
        double targetPower,
        ControlMode controlMode,
        String status,
        OffsetDateTime sendTime,
        OffsetDateTime expireAt,
        String latestAckStage,
        String latestAckMessage,
        OffsetDateTime latestAckTime,
        List<CommandAckResponse> acknowledgements
) {
}

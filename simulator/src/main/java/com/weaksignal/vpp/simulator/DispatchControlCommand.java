package com.weaksignal.vpp.simulator;

import java.time.OffsetDateTime;

/**
 * 模拟器接收的调度控制指令。
 *
 * @param commandId 指令标识
 * @param dispatchPlanId 调度计划标识
 * @param planVersion 计划版本号
 * @param dispatchPlanItemId 调度计划项标识
 * @param stationId 场站标识
 * @param targetPower 目标功率，单位 kW
 * @param expireAt 过期时间
 * @param maximumRampRate 最大爬坡速率，单位 kW/周期
 * @param controlMode 控制模式
 */
public record DispatchControlCommand(
        String commandId,
        String dispatchPlanId,
        String planVersion,
        String dispatchPlanItemId,
        String stationId,
        double targetPower,
        OffsetDateTime expireAt,
        double maximumRampRate,
        String controlMode
) {

    /**
     * 创建不启用站端实时补差的兼容控制指令。
     *
     * @param commandId 指令标识
     * @param dispatchPlanId 调度计划标识
     * @param planVersion 计划版本号
     * @param dispatchPlanItemId 调度计划项标识
     * @param stationId 场站标识
     * @param targetPower 目标功率，单位 kW
     * @param expireAt 过期时间
     * @param maximumRampRate 最大爬坡速率，单位 kW/周期
     */
    public DispatchControlCommand(
            String commandId,
            String dispatchPlanId,
            String planVersion,
            String dispatchPlanItemId,
            String stationId,
            double targetPower,
            OffsetDateTime expireAt,
            double maximumRampRate
    ) {
        this(commandId, dispatchPlanId, planVersion, dispatchPlanItemId, stationId, targetPower,
                expireAt, maximumRampRate, null);
    }
}

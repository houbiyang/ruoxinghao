package com.weaksignal.vpp.backend.api.dto;

import com.weaksignal.vpp.backend.domain.LinkState;

/**
 * 单场站调度分配结果。
 *
 * @param stationId 场站标识
 * @param currentPower 当前功率，单位 kW
 * @param targetPower 目标功率，单位 kW
 * @param availableCapacity 可用容量，单位 kW
 * @param constrainedCapacity 约束后容量，单位 kW
 * @param linkState 链路状态
 * @param eligible 是否可下发
 * @param validationMessage 校验说明
 */
public record DispatchAllocationResponse(
        String stationId,
        double currentPower,
        double targetPower,
        double availableCapacity,
        double constrainedCapacity,
        LinkState linkState,
        boolean eligible,
        String validationMessage
) {
}

package com.weaksignal.vpp.backend.api.dto;

import com.weaksignal.vpp.backend.domain.ControlMode;
import java.util.List;

/**
 * 调度预览结果。
 *
 * @param totalTargetPower 总目标功率，单位 kW
 * @param totalConstrainedCapacity 约束后总容量，单位 kW
 * @param controlMode 控制模式
 * @param valid 是否通过校验
 * @param message 结果说明
 * @param allocations 场站分配列表
 */
public record DispatchPreviewResponse(
        double totalTargetPower,
        double totalConstrainedCapacity,
        ControlMode controlMode,
        boolean valid,
        String message,
        List<DispatchAllocationResponse> allocations
) {
}

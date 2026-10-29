package com.weaksignal.vpp.backend.api.dto;

import com.weaksignal.vpp.backend.domain.ControlMode;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * 调度预览与执行请求。
 *
 * @param totalTargetPower 总目标功率，单位 kW
 * @param controlMode 控制模式
 * @param stationIds 参与分配的场站标识
 * @param minimumPower 单场站最小功率，单位 kW
 * @param maximumPower 单场站最大功率，单位 kW
 * @param maximumRampRate 最大爬坡速率，单位 kW/周期
 * @param commandTimeoutSeconds 指令超时秒数
 * @param allowLocalAutonomy 是否允许失联场站保持本地自治
 */
public record DispatchRequest(
        @DecimalMin(value = "0.0", inclusive = false, message = "总目标功率必须大于零")
        double totalTargetPower,
        @NotNull(message = "控制模式不能为空")
        ControlMode controlMode,
        @NotEmpty(message = "至少选择一个场站")
        List<String> stationIds,
        @DecimalMin(value = "0.0", message = "最小功率不能小于零")
        double minimumPower,
        @DecimalMin(value = "0.0", inclusive = false, message = "最大功率必须大于零")
        double maximumPower,
        @DecimalMin(value = "0.0", inclusive = false, message = "最大爬坡速率必须大于零")
        double maximumRampRate,
        @Min(value = 5, message = "指令超时不能少于 5 秒")
        @Max(value = 300, message = "指令超时不能超过 300 秒")
        int commandTimeoutSeconds,
        boolean allowLocalAutonomy
) {
}

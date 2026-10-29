package com.weaksignal.vpp.backend.api.dto;

/**
 * 资源功率趋势点响应。
 *
 * @param label 标签
 * @param power 实测功率
 * @param target 目标功率
 */
public record ResourceTelemetryPointResponse(
        String label,
        double power,
        Double target
) {
}

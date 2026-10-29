package com.weaksignal.vpp.backend.api.dto;

/**
 * 资源指标响应。
 *
 * @param label 指标名称
 * @param value 指标值
 * @param unit 单位
 */
public record ResourceMetricResponse(
        String label,
        Object value,
        String unit
) {
}


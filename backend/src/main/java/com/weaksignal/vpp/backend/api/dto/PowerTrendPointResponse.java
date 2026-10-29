package com.weaksignal.vpp.backend.api.dto;

/**
 * 功率趋势点响应。
 *
 * @param label 时间标签
 * @param planPower 计划功率
 * @param actualPower 实测功率
 */
public record PowerTrendPointResponse(
        String label,
        double planPower,
        double actualPower
) {
}


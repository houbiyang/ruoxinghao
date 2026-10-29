package com.weaksignal.vpp.backend.api.dto;

import java.time.OffsetDateTime;

/**
 * 日内调度计划点。
 *
 * @param timestamp 计划时刻
 * @param planPower 计划功率，单位 kW
 * @param availableCapacity 可用容量，单位 kW
 * @param reserveCapacity 备用容量，单位 kW
 * @param loadFactor 规则负荷系数
 */
public record DispatchPlanPointResponse(
        OffsetDateTime timestamp,
        double planPower,
        double availableCapacity,
        double reserveCapacity,
        double loadFactor
) {
}

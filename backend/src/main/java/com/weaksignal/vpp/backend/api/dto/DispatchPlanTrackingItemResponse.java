package com.weaksignal.vpp.backend.api.dto;

import java.time.OffsetDateTime;

/**
 * 调度计划跟踪项响应。
 *
 * @param dispatchPlanItemId 时间片标识
 * @param intervalStart 时间片开始
 * @param intervalEnd 时间片结束
 * @param plannedNetPower 计划净功率
 * @param actualNetPower 实际净功率
 * @param trackingError 跟踪误差
 * @param latestCommandStatus 最近指令状态
 */
public record DispatchPlanTrackingItemResponse(
        String dispatchPlanItemId,
        OffsetDateTime intervalStart,
        OffsetDateTime intervalEnd,
        double plannedNetPower,
        double actualNetPower,
        double trackingError,
        String latestCommandStatus
) {
}

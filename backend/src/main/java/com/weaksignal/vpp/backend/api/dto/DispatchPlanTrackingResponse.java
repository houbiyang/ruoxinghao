package com.weaksignal.vpp.backend.api.dto;

import java.util.List;

/**
 * 调度计划跟踪响应。
 *
 * @param planId 计划标识
 * @param version 计划版本
 * @param items 跟踪项列表
 */
public record DispatchPlanTrackingResponse(
        String planId,
        String version,
        List<DispatchPlanTrackingItemResponse> items
) {
}

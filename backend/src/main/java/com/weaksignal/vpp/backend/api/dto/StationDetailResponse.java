package com.weaksignal.vpp.backend.api.dto;

import java.util.List;

/**
 * 场站详情响应。
 *
 * @param stationId 场站标识
 * @param overview 概览说明
 * @param metrics 指标列表
 * @param deviceStatuses 设备状态列表
 * @param controlActions 控制动作列表
 * @param powerTrend 功率趋势
 * @param qualityTrend 质量趋势
 * @param eventFocus 重点事件
 */
public record StationDetailResponse(
        String stationId,
        String overview,
        List<ResourceMetricResponse> metrics,
        List<ResourceDeviceStatusResponse> deviceStatuses,
        List<ResourceControlActionResponse> controlActions,
        List<ResourceTelemetryPointResponse> powerTrend,
        List<ResourceQualityPointResponse> qualityTrend,
        List<EventResponse> eventFocus
) {
}

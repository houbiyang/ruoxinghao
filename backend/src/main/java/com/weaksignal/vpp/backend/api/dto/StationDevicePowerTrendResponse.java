package com.weaksignal.vpp.backend.api.dto;

import java.util.List;

/**
 * 场站子设备执行趋势响应。
 *
 * @param deviceId 设备标识
 * @param mode 趋势模式，power 表示功率执行，environment 表示环境监测
 * @param primaryLabel 主序列名称
 * @param primaryUnit 主序列单位
 * @param secondaryLabel 次序列名称
 * @param secondaryUnit 次序列单位
 * @param points 趋势点列表
 */
public record StationDevicePowerTrendResponse(
        String deviceId,
        String mode,
        String primaryLabel,
        String primaryUnit,
        String secondaryLabel,
        String secondaryUnit,
        List<StationDeviceTrendPointResponse> points
) {
}

package com.weaksignal.vpp.backend.api.dto;

/**
 * 场站子设备双序列趋势点。
 *
 * @param timestamp 时间戳
 * @param primaryValue 主序列值
 * @param secondaryValue 次序列值
 */
public record StationDeviceTrendPointResponse(
        String timestamp,
        Double primaryValue,
        Double secondaryValue
) {
}

package com.weaksignal.vpp.backend.api.dto;

import com.weaksignal.vpp.backend.domain.LinkState;

/**
 * 场站子设备链路质量趋势点。
 *
 * @param timestamp 时间戳
 * @param latency 时延
 * @param packetLossRate 丢包率
 * @param dataAge 数据时效
 * @param linkState 链路状态
 */
public record StationDeviceQualityTrendPointResponse(
        String timestamp,
        int latency,
        double packetLossRate,
        long dataAge,
        LinkState linkState
) {
}

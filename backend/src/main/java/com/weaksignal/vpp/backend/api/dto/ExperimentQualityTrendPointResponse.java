package com.weaksignal.vpp.backend.api.dto;

import java.time.OffsetDateTime;

/**
 * 通信质量趋势点响应。
 *
 * @param timestamp 时间
 * @param stationId 场站标识
 * @param latency 时延
 * @param packetLossRate 丢包率
 * @param dataAge 数据时效
 */
public record ExperimentQualityTrendPointResponse(
        OffsetDateTime timestamp,
        String stationId,
        int latency,
        double packetLossRate,
        long dataAge
) {
}

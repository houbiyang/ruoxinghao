package com.weaksignal.vpp.backend.api.dto;

/**
 * 场站通信质量排名响应。
 *
 * @param stationId 场站标识
 * @param averageLatency 平均时延
 * @param averagePacketLossRate 平均丢包率
 * @param averageDataAge 平均数据时效
 * @param onlineRate 在线率
 * @param qualityScore 质量评分
 */
public record ExperimentStationQualityRankResponse(
        String stationId,
        double averageLatency,
        double averagePacketLossRate,
        double averageDataAge,
        double onlineRate,
        double qualityScore
) {
}

package com.weaksignal.vpp.backend.api.dto;

import java.util.Map;

/**
 * 实验指标快照响应。
 *
 * @param sampleCount 样本数量
 * @param averagePower 平均功率
 * @param maxPower 最大功率
 * @param minPower 最小功率
 * @param averageLatency 平均时延
 * @param maxLatency 最大时延
 * @param averagePacketLossRate 平均丢包率
 * @param maxPacketLossRate 最大丢包率
 * @param averageDataAge 平均数据时效
 * @param maxDataAge 最大数据时效
 * @param onlineRate 在线率
 * @param stateCounts S0-S4 状态出现次数
 */
public record ExperimentMetricSnapshotResponse(
        long sampleCount,
        double averagePower,
        double maxPower,
        double minPower,
        double averageLatency,
        int maxLatency,
        double averagePacketLossRate,
        double maxPacketLossRate,
        double averageDataAge,
        long maxDataAge,
        double onlineRate,
        Map<String, Long> stateCounts
) {
}

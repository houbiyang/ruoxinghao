package com.weaksignal.vpp.backend.domain;

/**
 * 用于状态判定的指标样本。
 *
 * @param latency 时延
 * @param packetLossRate 丢包率
 * @param dataAge 数据年龄
 * @param online 是否在线
 */
public record StationMetricSample(
        int latency,
        double packetLossRate,
        long dataAge,
        boolean online
) {
}


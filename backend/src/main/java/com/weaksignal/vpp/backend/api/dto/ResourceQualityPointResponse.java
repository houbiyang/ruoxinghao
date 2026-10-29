package com.weaksignal.vpp.backend.api.dto;

/**
 * 资源通信质量点响应。
 *
 * @param label 标签
 * @param latency 时延
 * @param packetLossRate 丢包率
 */
public record ResourceQualityPointResponse(
        String label,
        int latency,
        double packetLossRate
) {
}


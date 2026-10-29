package com.weaksignal.vpp.backend.api.dto;

import java.time.OffsetDateTime;

/**
 * 实验链路状态时间线点响应。
 *
 * @param timestamp 时间
 * @param stationId 场站标识
 * @param linkState 链路状态
 * @param latency 时延
 * @param packetLossRate 丢包率
 * @param online 是否在线
 * @param eventTitle 事件标题
 */
public record ExperimentTimelinePointResponse(
        OffsetDateTime timestamp,
        String stationId,
        String linkState,
        int latency,
        double packetLossRate,
        boolean online,
        String eventTitle
) {
}

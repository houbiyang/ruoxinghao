package com.weaksignal.vpp.backend.api.dto;

import java.time.OffsetDateTime;

/**
 * 实验遥测回放点响应。
 *
 * @param timestamp 时间
 * @param stationId 场站标识
 * @param activePower 有功功率
 * @param availableCapacity 可用容量
 * @param latency 时延
 * @param packetLossRate 丢包率
 * @param dataAge 数据时效
 * @param linkState 链路状态
 * @param online 是否在线
 */
public record ExperimentReplayPointResponse(
        OffsetDateTime timestamp,
        String stationId,
        double activePower,
        double availableCapacity,
        int latency,
        double packetLossRate,
        long dataAge,
        String linkState,
        boolean online
) {
}

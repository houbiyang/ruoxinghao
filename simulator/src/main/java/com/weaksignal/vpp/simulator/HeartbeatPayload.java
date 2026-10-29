package com.weaksignal.vpp.simulator;

import java.time.OffsetDateTime;

/**
 * 心跳载荷。
 *
 * @param stationId 场站标识
 * @param online 是否在线
 * @param heartbeatAt 心跳时间
 * @param clientId 客户端标识
 */
public record HeartbeatPayload(
        String stationId,
        boolean online,
        OffsetDateTime heartbeatAt,
        String clientId
) {
}


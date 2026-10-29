package com.weaksignal.vpp.backend.domain;

import java.time.OffsetDateTime;

/**
 * 场站状态上下文。
 *
 * @param currentState 当前链路状态
 * @param lastChangedAt 最近状态切换时间
 * @param lastHeartbeatAt 最近心跳时间
 * @param lastSequenceNumber 最近有效序号
 * @param lastDeviceTimestamp 最近有效设备时间
 */
public record StateContext(
        LinkState currentState,
        OffsetDateTime lastChangedAt,
        OffsetDateTime lastHeartbeatAt,
        long lastSequenceNumber,
        OffsetDateTime lastDeviceTimestamp
) {
}


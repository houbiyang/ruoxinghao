package com.weaksignal.vpp.backend.domain;

import java.time.OffsetDateTime;

/**
 * 场站实时快照。
 *
 * @param stationId 场站标识
 * @param stationName 场站名称
 * @param resourceType 资源类型
 * @param activePower 实时功率
 * @param availableCapacity 可用容量
 * @param linkState 链路状态
 * @param latency 时延
 * @param packetLossRate 丢包率
 * @param dataAge 数据年龄
 * @param online 是否在线
 * @param position 场景坐标
 * @param sequenceNumber 遥测序号
 * @param deviceTimestamp 设备采样时间
 * @param receivedTimestamp 平台接收时间
 */
public record StationRealtimeSnapshot(
        String stationId,
        String stationName,
        ResourceType resourceType,
        double activePower,
        double availableCapacity,
        LinkState linkState,
        int latency,
        double packetLossRate,
        long dataAge,
        boolean online,
        StationPosition position,
        long sequenceNumber,
        OffsetDateTime deviceTimestamp,
        OffsetDateTime receivedTimestamp
) {
}


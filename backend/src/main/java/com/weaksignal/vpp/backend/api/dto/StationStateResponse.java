package com.weaksignal.vpp.backend.api.dto;

import com.weaksignal.vpp.backend.domain.LinkState;
import com.weaksignal.vpp.backend.domain.ResourceType;
import java.time.OffsetDateTime;

/**
 * 场站列表响应。
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
 * @param heartbeatAge 心跳年龄，单位秒
 * @param activeFaultCode 当前活动故障编码
 * @param updatedAt 最近遥测接收时间
 */
public record StationStateResponse(
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
        ScenePositionResponse position,
        long heartbeatAge,
        String activeFaultCode,
        OffsetDateTime updatedAt
) {
    /**
     * 兼容既有场站状态构造调用。
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
     */
    public StationStateResponse(
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
            ScenePositionResponse position
    ) {
        this(stationId, stationName, resourceType, activePower, availableCapacity, linkState, latency,
                packetLossRate, dataAge, online, position, dataAge, null, null);
    }
}

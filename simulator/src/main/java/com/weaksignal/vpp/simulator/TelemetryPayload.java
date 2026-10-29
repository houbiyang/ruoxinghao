package com.weaksignal.vpp.simulator;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 遥测载荷。
 *
 * @param messageId 消息标识
 * @param stationId 场站标识
 * @param stationName 场站名称
 * @param resourceType 资源类型
 * @param sequenceNumber 序号
 * @param deviceTimestamp 设备采样时间
 * @param sendTimestamp 发送时间
 * @param activePower 实时功率
 * @param availableCapacity 可用容量
 * @param latency 时延
 * @param packetLossRate 丢包率
 * @param online 是否在线
 * @param qualityCode 质量代码
 * @param linkState 链路状态
 * @param experimentRunId 实验运行标识
 * @param activeFaultCode 当前故障编码
 * @param metrics 指标集
 */
public record TelemetryPayload(
        String messageId,
        String stationId,
        String stationName,
        String resourceType,
        long sequenceNumber,
        OffsetDateTime deviceTimestamp,
        OffsetDateTime sendTimestamp,
        double activePower,
        double availableCapacity,
        int latency,
        double packetLossRate,
        boolean online,
        String qualityCode,
        String linkState,
        String experimentRunId,
        String activeFaultCode,
        Map<String, Double> metrics
) {
}

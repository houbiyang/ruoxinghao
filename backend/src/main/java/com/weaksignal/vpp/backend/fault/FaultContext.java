package com.weaksignal.vpp.backend.fault;

import com.weaksignal.vpp.backend.domain.LinkState;

/**
 * 不可变故障分析上下文。
 *
 * @param eventId 触发事件标识
 * @param stationId 场站标识
 * @param linkState 当前链路状态
 * @param latency 时延，单位 ms
 * @param jitter 抖动，单位 ms
 * @param packetLossRate 丢包率，单位百分比
 * @param consecutivePacketLoss 连续丢包长度
 * @param dataAge 数据年龄，单位秒
 * @param heartbeatAge 心跳年龄，单位秒
 * @param acknowledgementAge 指令确认年龄，单位秒
 * @param duplicateMessage 是否重复消息
 * @param staleSequence 是否序列号回退
 * @param timeSkewSeconds 时间偏差，单位秒
 * @param frozenTelemetry 是否遥测冻结
 * @param suspectedAttack 是否疑似攻击
 * @param activePower 当前功率，单位 kW
 * @param availableCapacity 可用容量，单位 kW
 * @param online 是否在线
 */
public record FaultContext(
        String eventId,
        String stationId,
        LinkState linkState,
        int latency,
        int jitter,
        double packetLossRate,
        int consecutivePacketLoss,
        long dataAge,
        long heartbeatAge,
        long acknowledgementAge,
        boolean duplicateMessage,
        boolean staleSequence,
        long timeSkewSeconds,
        boolean frozenTelemetry,
        boolean suspectedAttack,
        double activePower,
        double availableCapacity,
        boolean online
) {
}

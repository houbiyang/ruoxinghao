package com.weaksignal.vpp.backend.mqtt.dto;

import com.weaksignal.vpp.backend.domain.LinkState;
import com.weaksignal.vpp.backend.domain.ResourceType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 遥测上报消息。
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
 * @param linkState 场站自报状态
 * @param experimentRunId 实验运行标识
 * @param activeFaultCode 当前故障编码
 * @param metrics 扩展指标
 */
public record TelemetryReportMessage(
        @NotBlank String messageId,
        @NotBlank String stationId,
        @NotBlank String stationName,
        @NotNull ResourceType resourceType,
        @Min(0) long sequenceNumber,
        @NotNull OffsetDateTime deviceTimestamp,
        @NotNull OffsetDateTime sendTimestamp,
        double activePower,
        double availableCapacity,
        @Min(0) int latency,
        double packetLossRate,
        boolean online,
        @NotBlank String qualityCode,
        @NotNull LinkState linkState,
        String experimentRunId,
        String activeFaultCode,
        Map<String, Double> metrics
) {
    /**
     * 兼容未携带实验关联字段的普通遥测。
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
     * @param linkState 自报链路状态
     * @param metrics 扩展指标
     */
    public TelemetryReportMessage(
            String messageId,
            String stationId,
            String stationName,
            ResourceType resourceType,
            long sequenceNumber,
            OffsetDateTime deviceTimestamp,
            OffsetDateTime sendTimestamp,
            double activePower,
            double availableCapacity,
            int latency,
            double packetLossRate,
            boolean online,
            String qualityCode,
            LinkState linkState,
            Map<String, Double> metrics
    ) {
        this(messageId, stationId, stationName, resourceType, sequenceNumber, deviceTimestamp, sendTimestamp,
                activePower, availableCapacity, latency, packetLossRate, online, qualityCode, linkState,
                null, null, metrics);
    }
}

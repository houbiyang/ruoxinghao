package com.weaksignal.vpp.backend.mqtt.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;

/**
 * 故障上报消息。
 *
 * @param eventId 事件标识
 * @param stationId 场站标识
 * @param faultCode 故障代码
 * @param title 故障标题
 * @param description 故障描述
 * @param level 故障级别
 * @param occurredAt 发生时间
 */
public record FaultReportMessage(
        @NotBlank String eventId,
        @NotBlank String stationId,
        @NotBlank String faultCode,
        @NotBlank String title,
        @NotBlank String description,
        @NotBlank String level,
        @NotNull OffsetDateTime occurredAt
) {
}


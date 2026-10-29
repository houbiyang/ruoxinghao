package com.weaksignal.vpp.backend.mqtt.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;

/**
 * 心跳上报消息。
 *
 * @param stationId 场站标识
 * @param online 是否在线
 * @param heartbeatAt 心跳时间
 * @param clientId 客户端标识
 */
public record HeartbeatReportMessage(
        @NotBlank String stationId,
        boolean online,
        @NotNull OffsetDateTime heartbeatAt,
        @NotBlank String clientId
) {
}


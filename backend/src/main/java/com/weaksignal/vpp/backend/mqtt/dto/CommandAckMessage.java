package com.weaksignal.vpp.backend.mqtt.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;

/**
 * 命令确认消息。
 *
 * @param commandId 命令标识
 * @param stationId 场站标识
 * @param ackStage 确认阶段
 * @param success 是否成功
 * @param message 说明消息
 * @param ackTime 确认时间
 */
public record CommandAckMessage(
        @NotBlank String commandId,
        @NotBlank String stationId,
        @NotBlank String ackStage,
        boolean success,
        String message,
        @NotNull OffsetDateTime ackTime
) {
}


package com.weaksignal.vpp.backend.api.dto;

import java.time.OffsetDateTime;

/**
 * 控制指令回执。
 *
 * @param ackStage 回执阶段
 * @param success 是否成功
 * @param message 回执说明
 * @param ackTime 回执时间
 */
public record CommandAckResponse(
        String ackStage,
        boolean success,
        String message,
        OffsetDateTime ackTime
) {
}

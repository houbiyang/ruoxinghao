package com.weaksignal.vpp.backend.mqtt.dto;

import java.time.OffsetDateTime;

/**
 * 场站调度控制释放消息。
 *
 * @param releaseId 释放指令标识
 * @param dispatchPlanId 调度计划标识
 * @param stationId 场站标识
 * @param issuedAt 发布时间
 */
public record DispatchControlReleaseMessage(
        String releaseId,
        String dispatchPlanId,
        String stationId,
        OffsetDateTime issuedAt
) {
}

package com.weaksignal.vpp.backend.api.dto;

import java.time.OffsetDateTime;

/**
 * 停止实验响应。
 *
 * @param runId 运行标识
 * @param status 运行状态
 * @param stoppedAt 停止时间
 */
public record ExperimentStopResponse(
        String runId,
        String status,
        OffsetDateTime stoppedAt
) {
}

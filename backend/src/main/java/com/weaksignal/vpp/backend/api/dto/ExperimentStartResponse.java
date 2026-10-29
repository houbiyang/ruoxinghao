package com.weaksignal.vpp.backend.api.dto;

import java.time.OffsetDateTime;

/**
 * 启动实验响应。
 *
 * @param experimentRunId 实验运行标识
 * @param scenarioCode 场景编码
 * @param status 运行状态
 * @param startedAt 启动时间
 */
public record ExperimentStartResponse(
        String experimentRunId,
        String scenarioCode,
        String status,
        OffsetDateTime startedAt
) {
}

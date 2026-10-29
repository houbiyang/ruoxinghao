package com.weaksignal.vpp.backend.api.dto;

import java.time.OffsetDateTime;

/**
 * 实验运行响应。
 *
 * @param runId 运行标识
 * @param scenarioCode 场景编码
 * @param scenarioName 场景名称
 * @param status 运行状态
 * @param startedAt 启动时间
 * @param endedAt 结束时间
 * @param resultSummary 结果摘要
 */
public record ExperimentRunResponse(
        String runId,
        String scenarioCode,
        String scenarioName,
        String status,
        OffsetDateTime startedAt,
        OffsetDateTime endedAt,
        String resultSummary
) {
}

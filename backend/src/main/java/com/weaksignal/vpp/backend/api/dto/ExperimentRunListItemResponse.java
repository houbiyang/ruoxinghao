package com.weaksignal.vpp.backend.api.dto;

import java.time.OffsetDateTime;

/**
 * 实验历史列表项。
 *
 * <p>该模型刻意不包含大体积的结果摘要，供历史记录选择器等只需元数据的场景使用。</p>
 *
 * @param runId 实验运行标识
 * @param scenarioCode 场景编码
 * @param scenarioName 场景名称
 * @param status 实验状态
 * @param startedAt 开始时间
 * @param endedAt 结束时间
 */
public record ExperimentRunListItemResponse(
        String runId,
        String scenarioCode,
        String scenarioName,
        String status,
        OffsetDateTime startedAt,
        OffsetDateTime endedAt
) {
}

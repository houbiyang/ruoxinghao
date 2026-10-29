package com.weaksignal.vpp.backend.api.dto;

import java.time.OffsetDateTime;

/**
 * 实验运行记录删除响应。
 *
 * @param runId 已删除的实验运行标识
 * @param deletedAt 删除时间
 */
public record ExperimentDeleteResponse(
        String runId,
        OffsetDateTime deletedAt
) {
}

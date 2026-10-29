package com.weaksignal.vpp.simulator;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 场景生命周期报告。
 *
 * @param experimentRunId 实验运行标识
 * @param scenarioCode 场景编码
 * @param status 场景执行状态
 * @param targetStationIds 目标场站标识
 * @param reportedAt 报告时间
 * @param failureReason 失败原因
 */
public record ScenarioReportPayload(
        String experimentRunId,
        String scenarioCode,
        String status,
        List<String> targetStationIds,
        OffsetDateTime reportedAt,
        String failureReason
) {
}

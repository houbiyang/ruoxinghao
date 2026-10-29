package com.weaksignal.vpp.backend.mqtt.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 模拟器场景生命周期报告。
 *
 * @param experimentRunId 实验运行标识
 * @param scenarioCode 场景编码
 * @param status 场景执行状态
 * @param targetStationIds 目标场站标识
 * @param reportedAt 报告时间
 * @param failureReason 失败原因
 */
public record ScenarioReportMessage(
        @NotBlank String experimentRunId,
        @NotBlank String scenarioCode,
        @NotBlank String status,
        @NotEmpty List<String> targetStationIds,
        @NotNull OffsetDateTime reportedAt,
        String failureReason
) {
}

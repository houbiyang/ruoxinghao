package com.weaksignal.vpp.backend.mqtt.dto;

import com.weaksignal.vpp.backend.domain.DemoScenario;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * 场景切换命令消息。
 *
 * @param commandId 命令标识
 * @param scenario 目标场景
 * @param requestedAt 请求时间
 * @param source 来源
 * @param commandType 命令类型
 * @param experimentScenarioCode 实验场景编码
 * @param experimentRunId 实验运行标识
 * @param targetStationIds 目标场站
 * @param faultParameters 故障参数
 * @param severityLevel 严重等级
 * @param durationSeconds 持续时间
 * @param automaticRecovery 是否自动恢复
 * @param controlStrategy 对照策略
 * @param randomSeed 本次实验随机种子
 */
public record ScenarioApplyCommandMessage(
        @NotBlank String commandId,
        @NotNull DemoScenario scenario,
        @NotNull OffsetDateTime requestedAt,
        @NotBlank String source,
        String commandType,
        String experimentScenarioCode,
        String experimentRunId,
        List<String> targetStationIds,
        Map<String, Double> faultParameters,
        Integer severityLevel,
        Integer durationSeconds,
        Boolean automaticRecovery,
        String controlStrategy,
        Long randomSeed
) {

    /**
     * 创建普通场景切换命令。
     *
     * @param commandId 命令标识
     * @param scenario 目标场景
     * @param requestedAt 请求时间
     * @param source 来源
     */
    public ScenarioApplyCommandMessage(
            String commandId,
            DemoScenario scenario,
            OffsetDateTime requestedAt,
            String source
    ) {
        this(commandId, scenario, requestedAt, source, "APPLY_SCENARIO", null, null, List.of(), Map.of(), null, null, null, null, null);
    }

    /**
     * 兼容已有实验场景命令构造调用。
     *
     * @param commandId 命令标识
     * @param scenario 目标场景
     * @param requestedAt 请求时间
     * @param source 来源
     * @param commandType 命令类型
     * @param experimentScenarioCode 实验场景编码
     * @param experimentRunId 实验运行标识
     * @param targetStationIds 目标场站
     * @param faultParameters 故障参数
     * @param severityLevel 严重等级
     * @param durationSeconds 持续时间
     * @param automaticRecovery 是否自动恢复
     */
    public ScenarioApplyCommandMessage(String commandId, DemoScenario scenario, OffsetDateTime requestedAt, String source,
                                       String commandType, String experimentScenarioCode, String experimentRunId,
                                       List<String> targetStationIds, Map<String, Double> faultParameters,
                                       Integer severityLevel, Integer durationSeconds, Boolean automaticRecovery) {
        this(commandId, scenario, requestedAt, source, commandType, experimentScenarioCode, experimentRunId,
                targetStationIds, faultParameters, severityLevel, durationSeconds, automaticRecovery, null, null);
    }
}

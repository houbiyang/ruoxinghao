package com.weaksignal.vpp.simulator;

import java.util.List;
import java.util.Map;

/**
 * 实验命令。
 *
 * @param commandType 命令类型
 * @param experimentScenarioCode 实验场景编码
 * @param experimentRunId 实验运行标识
 * @param scenario 关联演示场景
 * @param targetStationIds 目标场站
 * @param faultParameters 故障参数
 * @param severityLevel 严重等级
 * @param durationSeconds 持续时间
 * @param automaticRecovery 是否自动恢复
 * @param controlStrategy 对照策略，仅用于实验审计
 * @param randomSeed 本次运行随机种子
 */
public record ExperimentCommand(
        String commandType,
        String experimentScenarioCode,
        String experimentRunId,
        DemoScenario scenario,
        List<String> targetStationIds,
        Map<String, Double> faultParameters,
        int severityLevel,
        int durationSeconds,
        boolean automaticRecovery,
        String controlStrategy,
        Long randomSeed
) {

    /**
     * 兼容已有模拟器测试与调用。
     *
     * @param commandType 命令类型
     * @param experimentScenarioCode 实验场景编码
     * @param experimentRunId 实验运行标识
     * @param scenario 关联演示场景
     * @param targetStationIds 目标场站
     * @param faultParameters 故障参数
     * @param severityLevel 严重等级
     * @param durationSeconds 持续时间
     * @param automaticRecovery 是否自动恢复
     */
    public ExperimentCommand(String commandType, String experimentScenarioCode, String experimentRunId, DemoScenario scenario,
                             List<String> targetStationIds, Map<String, Double> faultParameters, int severityLevel,
                             int durationSeconds, boolean automaticRecovery) {
        this(commandType, experimentScenarioCode, experimentRunId, scenario, targetStationIds, faultParameters,
                severityLevel, durationSeconds, automaticRecovery, "ADAPTIVE", null);
    }
}

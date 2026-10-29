package com.weaksignal.vpp.backend.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.Map;

/**
 * 启动实验请求。
 *
 * @param targetStationIds 目标场站标识列表
 * @param durationSeconds 持续时间
 * @param faultParameters 故障参数
 * @param severityLevel 严重等级
 * @param automaticRecovery 是否自动恢复
 * @param controlStrategy 对照策略：CENTRALIZED、FIXED_WEIGHT 或 ADAPTIVE
 * @param randomSeed 模拟器本次运行随机种子
 * @param remark 备注
 */
public record ExperimentStartRequest(
        List<String> targetStationIds,
        @Min(10) @Max(3600) Integer durationSeconds,
        Map<String, Double> faultParameters,
        @Min(1) @Max(5) Integer severityLevel,
        Boolean automaticRecovery,
        String controlStrategy,
        Long randomSeed,
        String remark
) {
    /**
     * 兼容旧版仅含目标、时长和备注的启动请求。
     *
     * @param targetStationIds 目标场站标识列表
     * @param durationSeconds 持续时间
     * @param remark 备注
     */
    public ExperimentStartRequest(List<String> targetStationIds, Integer durationSeconds, String remark) {
        this(targetStationIds, durationSeconds, null, null, null, null, null, remark);
    }

    /**
     * 兼容既有六参数调用。
     *
     * @param targetStationIds 目标场站标识列表
     * @param durationSeconds 持续时间
     * @param faultParameters 故障参数
     * @param severityLevel 严重等级
     * @param automaticRecovery 是否自动恢复
     * @param remark 备注
     */
    public ExperimentStartRequest(List<String> targetStationIds, Integer durationSeconds, Map<String, Double> faultParameters,
                                  Integer severityLevel, Boolean automaticRecovery, String remark) {
        this(targetStationIds, durationSeconds, faultParameters, severityLevel, automaticRecovery, null, null, remark);
    }
}

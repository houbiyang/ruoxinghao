package com.weaksignal.vpp.backend.environment;

import java.util.List;

/**
 * 环境感知智能体评估结果。
 *
 * @param stationId 场站标识
 * @param resourceType 资源类型
 * @param environmentFactor 环境修正因子
 * @param riskLevel 环境风险等级
 * @param correctionReasons 修正原因
 * @param affectedDeviceMetrics 受环境影响的设备指标
 * @param deviceDataInterpretation 设备数据解释
 * @param safetyConstraints 安全约束
 * @param dataAvailable 环境关键测量是否完整可用
 * @param missingFields 缺失的环境测量字段
 */
public record EnvironmentAssessmentResult(
        String stationId,
        String resourceType,
        double environmentFactor,
        String riskLevel,
        List<String> correctionReasons,
        List<String> affectedDeviceMetrics,
        String deviceDataInterpretation,
        List<String> safetyConstraints,
        boolean dataAvailable,
        List<String> missingFields
) {
}

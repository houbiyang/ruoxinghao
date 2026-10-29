package com.weaksignal.vpp.backend.api.dto;

import com.weaksignal.vpp.backend.domain.LinkState;
import com.weaksignal.vpp.backend.domain.ResourceType;
import com.weaksignal.vpp.backend.domain.ExperimentParameterDefinition;
import java.util.List;
import java.util.Map;

/**
 * 实验场景响应。
 *
 * @param scenarioCode 场景编码
 * @param scenarioName 场景名称
 * @param description 场景说明
 * @param targetStationIds 默认目标场站
 * @param category 故障类别
 * @param supportedResourceTypes 支持资源类型
 * @param expectedLinkState 预期链路状态
 * @param durationSeconds 默认持续时间
 * @param parameterDefinitions 故障参数定义
 * @param expectedEvidenceType 预期证据类型
 * @param automaticRecoverySupported 是否支持自动恢复
 * @param enabled 是否启用
 */
public record ExperimentScenarioResponse(
        String scenarioCode,
        String scenarioName,
        String description,
        List<String> targetStationIds,
        String category,
        List<ResourceType> supportedResourceTypes,
        LinkState expectedLinkState,
        int durationSeconds,
        Map<String, ExperimentParameterDefinition> parameterDefinitions,
        String expectedEvidenceType,
        boolean automaticRecoverySupported,
        boolean enabled
) {
}

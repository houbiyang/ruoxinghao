package com.weaksignal.vpp.backend.fault;

import com.weaksignal.vpp.backend.domain.ControlMode;
import java.util.List;

/**
 * 统一智能体结构化输出。
 *
 * @param agentType 智能体类型
 * @param provider Provider 标识
 * @param faultCode 故障编码
 * @param faultType 故障类型
 * @param affectedStationIds 受影响场站
 * @param severity 严重程度
 * @param confidence 置信度
 * @param evidence 证据列表
 * @param recommendedMode 建议控制模式
 * @param proposedActions 候选动作
 * @param requiresHumanApproval 兼容字段，自动裁决模式正常为 false
 * @param riskExplanation 风险说明
 */
public record AgentStructuredResponse(
        AgentType agentType,
        String provider,
        String faultCode,
        String faultType,
        List<String> affectedStationIds,
        FaultSeverity severity,
        double confidence,
        List<EvidenceItem> evidence,
        ControlMode recommendedMode,
        List<ProposedFaultAction> proposedActions,
        boolean requiresHumanApproval,
        String riskExplanation
) {
}

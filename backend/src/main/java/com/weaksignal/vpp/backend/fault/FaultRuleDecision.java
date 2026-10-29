package com.weaksignal.vpp.backend.fault;

import com.weaksignal.vpp.backend.domain.LinkState;
import java.util.List;

/**
 * 确定性故障规则判定结果。
 *
 * @param matched 是否命中故障规则
 * @param faultCode 文档故障编码
 * @param faultType 故障类型
 * @param severity 严重程度
 * @param suggestedLinkState 建议链路状态
 * @param evidence 规则证据
 * @param requiresAiAnalysis 是否需要智能体联合分析
 * @param immediateProtectionActions 立即保护动作
 */
public record FaultRuleDecision(
        boolean matched,
        String faultCode,
        String faultType,
        FaultSeverity severity,
        LinkState suggestedLinkState,
        List<EvidenceItem> evidence,
        boolean requiresAiAnalysis,
        List<ProposedFaultAction> immediateProtectionActions
) {
}

package com.weaksignal.vpp.backend.agent;

import com.weaksignal.vpp.backend.fault.AgentType;
import com.weaksignal.vpp.backend.fault.FaultContext;
import com.weaksignal.vpp.backend.fault.FaultRuleDecision;

/**
 * 智能体分析请求。
 *
 * @param agentType 智能体类型
 * @param context 故障上下文
 * @param ruleDecision 确定性规则结论
 */
public record AgentRequest(
        AgentType agentType,
        FaultContext context,
        FaultRuleDecision ruleDecision
) {
}

package com.weaksignal.vpp.backend.agent;

import com.weaksignal.vpp.backend.fault.AgentStructuredResponse;
import com.weaksignal.vpp.backend.fault.FaultContext;
import com.weaksignal.vpp.backend.fault.FaultRuleDecision;

/**
 * 单轮逻辑智能体。
 */
public interface FaultAgent {

    /**
     * 运行一次分析，禁止内部循环对话。
     *
     * @param context 故障上下文
     * @param ruleDecision 规则判定
     * @return 结构化智能体意见
     */
    AgentStructuredResponse analyze(FaultContext context, FaultRuleDecision ruleDecision);
}

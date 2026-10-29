package com.weaksignal.vpp.backend.agent;

import com.weaksignal.vpp.backend.domain.ControlMode;
import com.weaksignal.vpp.backend.fault.AgentStructuredResponse;
import com.weaksignal.vpp.backend.fault.AgentType;
import com.weaksignal.vpp.backend.fault.FaultActionType;
import com.weaksignal.vpp.backend.fault.FaultContext;
import com.weaksignal.vpp.backend.fault.FaultRuleDecision;
import com.weaksignal.vpp.backend.fault.ProposedFaultAction;
import java.util.List;
import java.util.Map;

/**
 * 逻辑智能体公共单轮执行模板。
 */
public abstract class AbstractFaultAgent implements FaultAgent {

    private final AgentType agentType;
    private final String preferredProvider;
    private final AiProviderRegistry providerRegistry;

    /**
     * 创建逻辑智能体。
     *
     * @param agentType 智能体类型
     * @param preferredProvider 首选 Provider
     * @param providerRegistry Provider 注册表
     */
    protected AbstractFaultAgent(
            AgentType agentType,
            String preferredProvider,
            AiProviderRegistry providerRegistry
    ) {
        this.agentType = agentType;
        this.preferredProvider = preferredProvider;
        this.providerRegistry = providerRegistry;
    }

    @Override
    public AgentStructuredResponse analyze(FaultContext context, FaultRuleDecision ruleDecision) {
        AgentRequest request = new AgentRequest(agentType, context, ruleDecision);
        return providerRegistry.analyzeOnce(preferredProvider, request)
                .orElseGet(() -> deterministicResponse(context, ruleDecision));
    }

    /**
     * 构建模型不可用时的确定性结构化意见。
     *
     * @param context 故障上下文
     * @param ruleDecision 规则判定
     * @return 规则型意见
     */
    protected abstract AgentStructuredResponse deterministicResponse(
            FaultContext context,
            FaultRuleDecision ruleDecision
    );

    /**
     * 创建通用规则型意见。
     *
     * @param context 故障上下文
     * @param ruleDecision 规则判定
     * @param mode 建议控制模式
     * @param actions 候选动作
     * @param requiresApproval 是否要求审批
     * @param riskExplanation 风险说明
     * @return 智能体意见
     */
    protected AgentStructuredResponse response(
            FaultContext context,
            FaultRuleDecision ruleDecision,
            ControlMode mode,
            List<ProposedFaultAction> actions,
            boolean requiresApproval,
            String riskExplanation
    ) {
        return new AgentStructuredResponse(
                agentType,
                "rule",
                ruleDecision.faultCode(),
                ruleDecision.faultType(),
                List.of(context.stationId()),
                ruleDecision.severity(),
                ruleDecision.matched() ? 0.95 : 1.0,
                ruleDecision.evidence(),
                mode,
                actions,
                requiresApproval,
                riskExplanation
        );
    }

    /**
     * 创建单个白名单动作。
     *
     * @param actionType 动作类型
     * @param context 故障上下文
     * @param reason 动作原因
     * @return 候选动作
     */
    protected ProposedFaultAction action(FaultActionType actionType, FaultContext context, String reason) {
        return new ProposedFaultAction(actionType, context.stationId(), Map.of(), reason);
    }
}

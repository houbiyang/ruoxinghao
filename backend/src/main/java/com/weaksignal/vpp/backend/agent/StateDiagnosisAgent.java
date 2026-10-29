package com.weaksignal.vpp.backend.agent;

import com.weaksignal.vpp.backend.domain.ControlMode;
import com.weaksignal.vpp.backend.fault.AgentStructuredResponse;
import com.weaksignal.vpp.backend.fault.AgentType;
import com.weaksignal.vpp.backend.fault.FaultActionType;
import com.weaksignal.vpp.backend.fault.FaultContext;
import com.weaksignal.vpp.backend.fault.FaultRuleDecision;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 状态诊断智能体，判断遥测可信度和能力高估风险。
 */
@Component
public class StateDiagnosisAgent extends AbstractFaultAgent {

    /**
     * 创建状态诊断智能体。
     *
     * @param providerRegistry Provider 注册表
     */
    public StateDiagnosisAgent(AiProviderRegistry providerRegistry) {
        super(AgentType.STATE_DIAGNOSIS, "deepseek", providerRegistry);
    }

    @Override
    protected AgentStructuredResponse deterministicResponse(FaultContext context, FaultRuleDecision decision) {
        boolean untrusted = context.frozenTelemetry() || context.staleSequence() || context.dataAge() > 15;
        return response(
                context,
                decision,
                untrusted ? ControlMode.LOCAL_AUTONOMY : ControlMode.EDGE_COORDINATION,
                untrusted
                        ? List.of(
                                action(FaultActionType.MARK_DATA_UNTRUSTED, context, "遥测不满足实时可信条件"),
                                action(FaultActionType.ENABLE_CONSERVATIVE_ESTIMATE, context, "按保守边界估计能力")
                        )
                        : decision.immediateProtectionActions(),
                false,
                untrusted ? "冻结或陈旧数据不得用于扩大可调能力。" : "当前诊断沿用确定性规则证据。"
        );
    }
}

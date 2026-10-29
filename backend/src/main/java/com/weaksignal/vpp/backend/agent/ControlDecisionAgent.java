package com.weaksignal.vpp.backend.agent;

import com.weaksignal.vpp.backend.domain.ControlMode;
import com.weaksignal.vpp.backend.domain.LinkState;
import com.weaksignal.vpp.backend.fault.AgentStructuredResponse;
import com.weaksignal.vpp.backend.fault.AgentType;
import com.weaksignal.vpp.backend.fault.FaultActionType;
import com.weaksignal.vpp.backend.fault.FaultContext;
import com.weaksignal.vpp.backend.fault.FaultRuleDecision;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 控制决策智能体，仅生成受限候选方案。
 */
@Component
public class ControlDecisionAgent extends AbstractFaultAgent {

    /**
     * 创建控制决策智能体。
     *
     * @param providerRegistry Provider 注册表
     */
    public ControlDecisionAgent(AiProviderRegistry providerRegistry) {
        super(AgentType.CONTROL_DECISION, "deepseek", providerRegistry);
    }

    @Override
    protected AgentStructuredResponse deterministicResponse(FaultContext context, FaultRuleDecision decision) {
        if (decision.suggestedLinkState() == LinkState.S3) {
            return response(context, decision, ControlMode.LOCAL_AUTONOMY,
                    List.of(
                            action(FaultActionType.ENTER_LOCAL_AUTONOMY, context, "中心链路不可用"),
                            action(FaultActionType.EXCLUDE_OFFLINE_CAPACITY, context, "避免失联资源继续参与聚合")
                    ),
                    decision.severity().name().equals("CRITICAL"),
                    "失联时只允许本地安全计划，不直接生成任意功率目标。");
        }
        return response(context, decision, ControlMode.EDGE_COORDINATION,
                List.of(action(FaultActionType.LIMIT_CONTROL_RAMP, context, "弱链路下限制控制步长")),
                false,
                "候选方案仅包含限速和边缘协同，不直接发布 MQTT。");
    }
}

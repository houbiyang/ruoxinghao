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
 * 恢复编排智能体，生成身份校验、快照、去重和斜坡恢复顺序。
 */
@Component
public class RecoveryOrchestrationAgent extends AbstractFaultAgent {

    /**
     * 创建恢复编排智能体。
     *
     * @param providerRegistry Provider 注册表
     */
    public RecoveryOrchestrationAgent(AiProviderRegistry providerRegistry) {
        super(AgentType.RECOVERY_ORCHESTRATION, "deepseek", providerRegistry);
    }

    @Override
    protected AgentStructuredResponse deterministicResponse(FaultContext context, FaultRuleDecision decision) {
        boolean recovery = decision.faultCode().equals("RECOVERY");
        return response(
                context,
                decision,
                ControlMode.EDGE_COORDINATION,
                recovery
                        ? List.of(action(FaultActionType.START_RATE_LIMITED_RECOVERY, context,
                                "按身份校验、时钟校准、状态快照、限速补传、去重、能力重估和斜坡恢复执行"))
                        : List.of(),
                false,
                recovery ? "恢复期间不立即交还中心控制权。" : "当前不处于恢复阶段，保留恢复编排待命。"
        );
    }
}

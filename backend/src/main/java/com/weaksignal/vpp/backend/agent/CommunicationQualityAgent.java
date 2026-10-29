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
 * 通信质量智能体，评估时延、抖动、丢包、心跳和数据年龄。
 */
@Component
public class CommunicationQualityAgent extends AbstractFaultAgent {

    /**
     * 创建通信质量智能体。
     *
     * @param providerRegistry Provider 注册表
     */
    public CommunicationQualityAgent(AiProviderRegistry providerRegistry) {
        super(AgentType.COMMUNICATION_QUALITY, "deepseek", providerRegistry);
    }

    @Override
    protected AgentStructuredResponse deterministicResponse(FaultContext context, FaultRuleDecision decision) {
        ControlMode mode = decision.suggestedLinkState().name().equals("S3")
                ? ControlMode.LOCAL_AUTONOMY
                : ControlMode.EDGE_COORDINATION;
        return response(context, decision, mode, decision.immediateProtectionActions(), false,
                "依据集中阈值评估链路退化，不使用未经验证的网络指标。");
    }
}

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
 * 安全审计智能体，区分弱信号、设备故障与攻击特征。
 */
@Component
public class SecurityAuditAgent extends AbstractFaultAgent {

    /**
     * 创建安全审计智能体。
     *
     * @param providerRegistry Provider 注册表
     */
    public SecurityAuditAgent(AiProviderRegistry providerRegistry) {
        super(AgentType.SECURITY_AUDIT, "deepseek", providerRegistry);
    }

    @Override
    protected AgentStructuredResponse deterministicResponse(FaultContext context, FaultRuleDecision decision) {
        if (context.suspectedAttack()) {
            return response(context, decision, ControlMode.LOCAL_AUTONOMY,
                    List.of(
                            action(FaultActionType.MARK_DATA_UNTRUSTED, context, "可疑数据停止参与控制"),
                            action(FaultActionType.ISOLATE_STATION, context, "隔离建议交由安全门自动裁决")
                    ),
                    false,
                    "疑似攻击不能按普通通信退化处置，需由安全门自动裁决并保留取证记录。");
        }
        return response(context, decision, ControlMode.EDGE_COORDINATION, List.of(), false,
                "未发现认证失败、重放或物理约束冲突的明确证据。");
    }
}

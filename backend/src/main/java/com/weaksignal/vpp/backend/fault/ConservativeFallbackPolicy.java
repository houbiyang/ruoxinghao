package com.weaksignal.vpp.backend.fault;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * AI 不可用或意见冲突时的规则型保守回退策略。
 */
@Component
public class ConservativeFallbackPolicy {

    /**
     * 根据确定性故障编码生成保守动作。
     *
     * @param context 故障上下文
     * @param decision 规则判定
     * @return 保守动作
     */
    public List<ProposedFaultAction> actions(FaultContext context, FaultRuleDecision decision) {
        return switch (decision.faultCode()) {
            case "C6" -> List.of(
                    action(FaultActionType.FREEZE_EXPIRED_COMMANDS, context, "失联时冻结远程指令"),
                    action(FaultActionType.ENTER_LOCAL_AUTONOMY, context, "失联时进入本地自治"),
                    action(FaultActionType.EXCLUDE_OFFLINE_CAPACITY, context, "失联资源退出聚合容量")
            );
            case "C8", "C9", "STALE_TELEMETRY" -> List.of(
                    action(FaultActionType.MARK_DATA_UNTRUSTED, context, "异常数据仅归档不参与实时控制"),
                    action(FaultActionType.ENABLE_CONSERVATIVE_ESTIMATE, context, "启用保守估计")
            );
            case "RECOVERY" -> List.of(
                    action(FaultActionType.START_RATE_LIMITED_RECOVERY, context, "限速恢复并显式交还控制权")
            );
            default -> decision.matched()
                    ? List.of(action(FaultActionType.LIMIT_CONTROL_RAMP, context, "退化期间限制控制步长"))
                    : List.of();
        };
    }

    private ProposedFaultAction action(FaultActionType type, FaultContext context, String reason) {
        return new ProposedFaultAction(type, context.stationId(), Map.of(), reason);
    }
}

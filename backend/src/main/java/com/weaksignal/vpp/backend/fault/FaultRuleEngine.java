package com.weaksignal.vpp.backend.fault;

import com.weaksignal.vpp.backend.config.StateRuleProperties;
import com.weaksignal.vpp.backend.domain.LinkState;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 依据项目集中阈值执行确定性故障预判。
 */
@Component
public class FaultRuleEngine {

    private final StateRuleProperties properties;

    /**
     * 注入状态阈值配置。
     *
     * @param properties S0-S4 状态规则配置
     */
    public FaultRuleEngine(StateRuleProperties properties) {
        this.properties = properties;
    }

    /**
     * 对不可变故障上下文执行规则判定。
     *
     * @param context 故障上下文
     * @return 规则判定
     */
    public FaultRuleDecision evaluate(FaultContext context) {
        List<EvidenceItem> evidence = new ArrayList<>();
        if (context.suspectedAttack()) {
            evidence.add(evidence("securitySignal", "true", "", "检测到认证、重放或物理约束冲突特征"));
            return decision("SECURITY", "SUSPECTED_ATTACK", FaultSeverity.CRITICAL, LinkState.S3, evidence, true,
                    List.of(action(FaultActionType.MARK_DATA_UNTRUSTED, context, "疑似攻击数据不得参与控制")));
        }
        if (context.duplicateMessage() || context.staleSequence()) {
            evidence.add(evidence("sequenceIntegrity", "invalid", "", "唯一标识重复或序列号回退"));
            return decision("C8", "OUT_OF_ORDER_DUPLICATE_EXPIRED", FaultSeverity.HIGH, context.linkState(), evidence, true,
                    List.of(action(FaultActionType.MARK_DATA_UNTRUSTED, context, "拒绝重复、乱序和过期数据")));
        }
        if (!context.online() || context.heartbeatAge() > properties.heartbeatTimeoutSeconds()) {
            evidence.add(evidence("heartbeatAge", String.valueOf(context.heartbeatAge()), "s", "心跳超过演示默认阈值"));
            return decision("C6", "FULL_DISCONNECTION", FaultSeverity.HIGH, LinkState.S3, evidence, true,
                    List.of(
                            action(FaultActionType.FREEZE_EXPIRED_COMMANDS, context, "失联期间冻结远程指令"),
                            action(FaultActionType.EXCLUDE_OFFLINE_CAPACITY, context, "剔除失联资源可调用容量"),
                            action(FaultActionType.ENTER_LOCAL_AUTONOMY, context, "场站进入本地安全计划")
                    ));
        }
        if (context.frozenTelemetry() || context.dataAge() > properties.severeDataAgeThreshold()) {
            evidence.add(evidence("dataAge", String.valueOf(context.dataAge()), "s", "遥测陈旧或连续冻结"));
            return decision("STALE_TELEMETRY", "STALE_OR_FROZEN_TELEMETRY", FaultSeverity.HIGH, LinkState.S2, evidence, true,
                    List.of(
                            action(FaultActionType.MARK_DATA_UNTRUSTED, context, "冻结值不能作为实时状态"),
                            action(FaultActionType.ENABLE_CONSERVATIVE_ESTIMATE, context, "启用保守状态估计")
                    ));
        }
        if (Math.abs(context.timeSkewSeconds()) > properties.severeDataAgeThreshold()) {
            evidence.add(evidence("timeSkew", String.valueOf(context.timeSkewSeconds()), "s", "多源时间戳不可比"));
            return decision("C9", "TIME_SYNCHRONIZATION_DEVIATION", FaultSeverity.HIGH, LinkState.S2, evidence, true,
                    List.of(action(FaultActionType.MARK_DATA_UNTRUSTED, context, "暂停依赖精确时序的数据使用")));
        }
        if (context.acknowledgementAge() > properties.heartbeatTimeoutSeconds()) {
            evidence.add(evidence("acknowledgementAge", String.valueOf(context.acknowledgementAge()), "s", "终端确认超时"));
            return decision("D1", "COMMAND_NOT_DELIVERED", FaultSeverity.HIGH, LinkState.S2, evidence, true,
                    List.of(action(FaultActionType.FREEZE_EXPIRED_COMMANDS, context, "确认不明时不重复执行高风险动作")));
        }
        if (context.packetLossRate() >= properties.degradedPacketLossThreshold()
                || context.consecutivePacketLoss() >= 5) {
            evidence.add(evidence("packetLossRate", String.valueOf(context.packetLossRate()), "%", "突发或连续丢包"));
            return decision("C4", "BURST_PACKET_LOSS", FaultSeverity.HIGH, LinkState.S2, evidence, true,
                    List.of(
                            action(FaultActionType.LIMIT_CONTROL_RAMP, context, "减少控制步长"),
                            action(FaultActionType.REDUCE_TELEMETRY_RATE, context, "优先保留关键消息")
                    ));
        }
        if (context.packetLossRate() >= properties.stablePacketLossThreshold()) {
            evidence.add(evidence("packetLossRate", String.valueOf(context.packetLossRate()), "%", "随机丢包超过稳定阈值"));
            return decision("C3", "RANDOM_PACKET_LOSS", FaultSeverity.MEDIUM, LinkState.S1, evidence, true,
                    List.of(action(FaultActionType.LIMIT_CONTROL_RAMP, context, "降低控制步长")));
        }
        if (context.latency() >= properties.degradedLatencyThreshold()
                || context.jitter() >= properties.stableLatencyThreshold()) {
            evidence.add(evidence("latency", String.valueOf(context.latency()), "ms", "时延或抖动达到严重退化区间"));
            return decision("C2", "LATENCY_JITTER_SEVERE", FaultSeverity.HIGH, LinkState.S2, evidence, true,
                    List.of(
                            action(FaultActionType.LIMIT_CONTROL_RAMP, context, "限制控制速率"),
                            action(FaultActionType.REDUCE_TELEMETRY_RATE, context, "降低非关键遥测频率")
                    ));
        }
        if (context.latency() >= properties.stableLatencyThreshold()
                || context.jitter() >= properties.stableLatencyThreshold() / 2) {
            evidence.add(evidence("latency", String.valueOf(context.latency()), "ms", "时延或抖动超过稳定阈值"));
            return decision("C1", "LATENCY_JITTER_DEGRADED", FaultSeverity.MEDIUM, LinkState.S1, evidence, true,
                    List.of(action(FaultActionType.LIMIT_CONTROL_RAMP, context, "提前限制控制速率")));
        }
        if (context.linkState() == LinkState.S4) {
            evidence.add(evidence("linkState", "S4", "", "链路处于恢复同步阶段"));
            return decision("RECOVERY", "RECOVERY_SYNCHRONIZATION", FaultSeverity.MEDIUM, LinkState.S4, evidence, true,
                    List.of(action(FaultActionType.START_RATE_LIMITED_RECOVERY, context, "按快照、去重和斜坡顺序恢复")));
        }
        return new FaultRuleDecision(false, "NONE", "NORMAL", FaultSeverity.LOW, LinkState.S0,
                List.of(evidence("linkState", context.linkState().name(), "", "未命中演示故障阈值")),
                false, List.of());
    }

    private FaultRuleDecision decision(
            String faultCode,
            String faultType,
            FaultSeverity severity,
            LinkState state,
            List<EvidenceItem> evidence,
            boolean requiresAi,
            List<ProposedFaultAction> actions
    ) {
        return new FaultRuleDecision(true, faultCode, faultType, severity, state, evidence, requiresAi, actions);
    }

    private ProposedFaultAction action(FaultActionType type, FaultContext context, String reason) {
        return new ProposedFaultAction(type, context.stationId(), Map.of(), reason);
    }

    private EvidenceItem evidence(String metric, String value, String unit, String description) {
        return new EvidenceItem(metric, value, unit, description);
    }
}

package com.weaksignal.vpp.backend.domain;

import com.weaksignal.vpp.backend.config.StateRuleProperties;
import java.time.Duration;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Component;

/**
 * 链路状态判定器。
 */
@Component
public class LinkStateEvaluator {

    private static final int EXIT_LATENCY_BUFFER = 20;
    private static final double EXIT_PACKET_LOSS_BUFFER = 0.5;
    private static final long EXIT_DATA_AGE_BUFFER = 1L;

    private final StateRuleProperties stateRuleProperties;

    /**
     * 注入状态规则配置。
     *
     * @param stateRuleProperties 状态规则配置
     */
    public LinkStateEvaluator(StateRuleProperties stateRuleProperties) {
        this.stateRuleProperties = stateRuleProperties;
    }

    /**
     * 根据指标和当前上下文判定下一状态。
     *
     * @param context 当前上下文
     * @param sample 指标样本
     * @param now 当前时间
     * @return 判定结果
     */
    public StateDecision evaluate(StateContext context, StationMetricSample sample, OffsetDateTime now) {
        LinkState desiredState = determineMetricState(sample);
        LinkState currentState = context.currentState();

        if (!sample.online()) {
            return switchTo(currentState, LinkState.S3, "设备主动上报离线");
        }

        if (currentState == LinkState.S3) {
            return switchTo(currentState, LinkState.S4, "收到失联后的首条有效遥测，进入恢复同步");
        }

        if (currentState == LinkState.S4) {
            long recoveringSeconds = Duration.between(context.lastChangedAt(), now).toSeconds();
            if (recoveringSeconds < stateRuleProperties.recoveringHoldSeconds()) {
                return stay(currentState, "恢复同步保持期内继续观测");
            }
            return switchTo(currentState, desiredState, "恢复同步保持期结束，按实时指标重新评估");
        }

        long holdSeconds = Duration.between(context.lastChangedAt(), now).toSeconds();
        if (holdSeconds < stateRuleProperties.minimumStateHoldSeconds() && desiredState != currentState) {
            return stay(currentState, "未达到最小状态保持时间");
        }

        return switch (currentState) {
            case S0 -> evaluateFromStable(desiredState, sample, currentState);
            case S1 -> evaluateFromDegraded(desiredState, sample, currentState);
            case S2 -> evaluateFromSevere(desiredState, sample, currentState);
            case S3, S4 -> stay(currentState, "状态保持");
        };
    }

    /**
     * 基于心跳超时判定是否进入失联自治。
     *
     * @param context 当前上下文
     * @param now 当前时间
     * @return 判定结果
     */
    public StateDecision evaluateHeartbeatTimeout(StateContext context, OffsetDateTime now) {
        long heartbeatAge = Duration.between(context.lastHeartbeatAt(), now).toSeconds();
        if (heartbeatAge > stateRuleProperties.heartbeatTimeoutSeconds() && context.currentState() != LinkState.S3) {
            return switchTo(context.currentState(), LinkState.S3, "心跳超时，进入失联自治");
        }
        return stay(context.currentState(), "心跳正常");
    }

    private StateDecision evaluateFromStable(LinkState desiredState, StationMetricSample sample, LinkState currentState) {
        if (desiredState == LinkState.S2) {
            return switchTo(currentState, LinkState.S2, "时延、丢包或数据时效达到严重退化阈值");
        }
        if (desiredState == LinkState.S1) {
            return switchTo(currentState, LinkState.S1, "时延、丢包或数据时效达到通信退化阈值");
        }
        return stay(currentState, "指标稳定，保持正常协同");
    }

    private StateDecision evaluateFromDegraded(LinkState desiredState, StationMetricSample sample, LinkState currentState) {
        if (desiredState == LinkState.S2) {
            return switchTo(currentState, LinkState.S2, "退化进一步恶化，进入严重退化");
        }
        if (canRecoverToStable(sample)) {
            return switchTo(currentState, LinkState.S0, "指标恢复到正常协同退出阈值");
        }
        return stay(currentState, "仍处于通信退化范围");
    }

    private StateDecision evaluateFromSevere(LinkState desiredState, StationMetricSample sample, LinkState currentState) {
        if (canRecoverToDegraded(sample)) {
            return switchTo(currentState, LinkState.S1, "指标回落到通信退化退出阈值");
        }
        return stay(currentState, "仍处于严重退化范围");
    }

    private LinkState determineMetricState(StationMetricSample sample) {
        if (sample.latency() >= stateRuleProperties.severeLatencyThreshold()
                || sample.packetLossRate() >= stateRuleProperties.severePacketLossThreshold()
                || sample.dataAge() >= stateRuleProperties.severeDataAgeThreshold()) {
            return LinkState.S2;
        }

        if (sample.latency() >= stateRuleProperties.stableLatencyThreshold()
                || sample.packetLossRate() >= stateRuleProperties.stablePacketLossThreshold()
                || sample.dataAge() >= stateRuleProperties.stableDataAgeThreshold()) {
            return LinkState.S1;
        }

        return LinkState.S0;
    }

    private boolean canRecoverToStable(StationMetricSample sample) {
        return sample.latency() < Math.max(1, stateRuleProperties.stableLatencyThreshold() - 10)
                && sample.packetLossRate() < Math.max(0.1, stateRuleProperties.stablePacketLossThreshold() - 0.2)
                && sample.dataAge() < Math.max(1, stateRuleProperties.stableDataAgeThreshold() - EXIT_DATA_AGE_BUFFER);
    }

    private boolean canRecoverToDegraded(StationMetricSample sample) {
        return sample.latency() < Math.max(1, stateRuleProperties.degradedLatencyThreshold() - EXIT_LATENCY_BUFFER)
                && sample.packetLossRate() < Math.max(0.1, stateRuleProperties.degradedPacketLossThreshold() - EXIT_PACKET_LOSS_BUFFER)
                && sample.dataAge() < Math.max(1, stateRuleProperties.degradedDataAgeThreshold() - EXIT_DATA_AGE_BUFFER);
    }

    private StateDecision stay(LinkState currentState, String reason) {
        return new StateDecision(currentState, false, reason);
    }

    private StateDecision switchTo(LinkState currentState, LinkState nextState, String reason) {
        if (currentState == nextState) {
            return stay(currentState, reason);
        }
        return new StateDecision(nextState, true, reason);
    }
}


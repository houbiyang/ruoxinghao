package com.weaksignal.vpp.backend.control.distributed;

import com.weaksignal.vpp.backend.domain.LinkState;
import java.time.Duration;
import java.time.OffsetDateTime;

/**
 * 按通信质量和链路状态计算一致性权重、步长及消息有效期的确定性策略。
 */
public class AdaptiveConsensusPolicy implements ConsensusPolicy {

    private final DistributedDispatchProperties properties;

    /**
     * 注入分布式控制配置。
     *
     * @param properties 分布式控制配置
     */
    public AdaptiveConsensusPolicy(DistributedDispatchProperties properties) {
        this.properties = properties;
        double weightSum = properties.latencyWeight() + properties.packetLossWeight() + properties.dataAgeWeight();
        if (weightSum <= 0 || weightSum > 1.000001) {
            throw new IllegalArgumentException("通信质量权重之和必须大于零且不超过一");
        }
    }

    /**
     * 计算与邻居消息对应的通信质量分数。
     *
     * @param message 邻居消息
     * @param receiverState 接收节点链路状态
     * @param now 当前时间
     * @return 范围为 0 至 1 的质量分数
     */
    @Override
    public double qualityScore(ConsensusMessage message, LinkState receiverState, OffsetDateTime now) {
        long validity = messageValidityMillis(receiverState);
        long age = Math.max(0, Duration.between(message.timestamp(), now).toMillis());
        double latencyQuality = 1.0 - Math.min(1.0, message.latencyMillis() / (double) validity);
        double lossQuality = 1.0 - message.packetLossRate() / 100.0;
        double ageQuality = 1.0 - Math.min(1.0, age / (double) validity);
        return clip(
                properties.latencyWeight() * latencyQuality
                        + properties.packetLossWeight() * lossQuality
                        + properties.dataAgeWeight() * ageQuality,
                0.0,
                1.0
        );
    }

    /**
     * 判断消息是否仍处于接收节点当前状态允许的有效期内。
     *
     * @param message 邻居消息
     * @param receiverState 接收节点链路状态
     * @param now 当前时间
     * @return 是否有效
     */
    @Override
    public boolean isMessageValid(ConsensusMessage message, LinkState receiverState, OffsetDateTime now) {
        if (receiverState == LinkState.S3 || message.linkState() == LinkState.S3) {
            return false;
        }
        long age = Duration.between(message.timestamp(), now).toMillis();
        return age >= 0 && age <= messageValidityMillis(receiverState);
    }

    /**
     * 获取指定链路状态的迭代步长。
     *
     * @param linkState 链路状态
     * @return 增量成本步长
     */
    @Override
    public double stepSize(LinkState linkState) {
        return switch (linkState) {
            case S0 -> properties.normalStepSize();
            case S1 -> properties.degradedStepSize();
            case S2 -> properties.severeStepSize();
            case S3 -> 0.0;
            case S4 -> properties.recoveryStepSize();
        };
    }

    /**
     * 获取指定链路状态的消息发送周期。
     *
     * @param linkState 链路状态
     * @return 消息周期，单位 ms
     */
    public long messagePeriodMillis(LinkState linkState) {
        return switch (linkState) {
            case S0 -> properties.normalMessagePeriodMillis();
            case S1 -> properties.degradedMessagePeriodMillis();
            case S2 -> properties.severeMessagePeriodMillis();
            case S3 -> Long.MAX_VALUE;
            case S4 -> properties.recoveryMessagePeriodMillis();
        };
    }

    /**
     * 获取指定链路状态的消息有效期。
     *
     * @param linkState 链路状态
     * @return 有效期，单位 ms
     */
    @Override
    public long messageValidityMillis(LinkState linkState) {
        return switch (linkState) {
            case S0 -> properties.normalMessageValidityMillis();
            case S1 -> properties.degradedMessageValidityMillis();
            case S2 -> properties.severeMessageValidityMillis();
            case S3 -> 0;
            case S4 -> properties.recoveryMessageValidityMillis();
        };
    }

    /**
     * 获取恢复同步状态的爬坡收紧系数。
     *
     * @return 爬坡收紧系数
     */
    @Override
    public double recoveryRampFactor() {
        return properties.recoveryRampFactor();
    }

    private double clip(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}

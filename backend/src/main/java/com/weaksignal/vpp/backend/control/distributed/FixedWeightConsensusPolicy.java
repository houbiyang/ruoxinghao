package com.weaksignal.vpp.backend.control.distributed;

import com.weaksignal.vpp.backend.domain.LinkState;
import java.time.OffsetDateTime;

/**
 * 固定权重一致性策略，用于与通信质量自适应策略进行可重复对照。
 */
public class FixedWeightConsensusPolicy implements ConsensusPolicy {

    private final AdaptiveConsensusPolicy delegate;

    /**
     * 创建固定权重策略。
     *
     * @param properties 分布式调度配置
     */
    public FixedWeightConsensusPolicy(DistributedDispatchProperties properties) {
        this.delegate = new AdaptiveConsensusPolicy(properties);
    }

    /** {@inheritDoc} */
    @Override
    public double qualityScore(ConsensusMessage message, LinkState receiverState, OffsetDateTime now) {
        return isMessageValid(message, receiverState, now) ? 1.0 : 0.0;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isMessageValid(ConsensusMessage message, LinkState receiverState, OffsetDateTime now) {
        return delegate.isMessageValid(message, receiverState, now);
    }

    /** {@inheritDoc} */
    @Override
    public double stepSize(LinkState linkState) {
        return delegate.stepSize(linkState);
    }

    /** {@inheritDoc} */
    @Override
    public long messageValidityMillis(LinkState linkState) {
        return delegate.messageValidityMillis(linkState);
    }

    /** {@inheritDoc} */
    @Override
    public double recoveryRampFactor() {
        return delegate.recoveryRampFactor();
    }
}

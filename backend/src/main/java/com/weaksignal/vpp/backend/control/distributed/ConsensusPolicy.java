package com.weaksignal.vpp.backend.control.distributed;

import com.weaksignal.vpp.backend.domain.LinkState;
import java.time.OffsetDateTime;

/**
 * 分布式一致性消息的权重、步长和有效期策略。
 */
public interface ConsensusPolicy {

    /**
     * 计算邻居消息的有效权重。
     *
     * @param message 邻居消息
     * @param receiverState 接收节点链路状态
     * @param now 当前时间
     * @return 范围为 0 至 1 的质量分数
     */
    double qualityScore(ConsensusMessage message, LinkState receiverState, OffsetDateTime now);

    /**
     * 判断消息是否可用于当前迭代。
     *
     * @param message 邻居消息
     * @param receiverState 接收节点链路状态
     * @param now 当前时间
     * @return 是否有效
     */
    boolean isMessageValid(ConsensusMessage message, LinkState receiverState, OffsetDateTime now);

    /**
     * 获取指定链路状态的迭代步长。
     *
     * @param linkState 链路状态
     * @return 增量成本步长
     */
    double stepSize(LinkState linkState);

    /**
     * 获取指定链路状态的消息有效期。
     *
     * @param linkState 链路状态
     * @return 有效期，单位 ms
     */
    long messageValidityMillis(LinkState linkState);

    /**
     * 获取恢复同步时的爬坡收紧系数。
     *
     * @return 范围为 0 至 1 的收紧系数
     */
    double recoveryRampFactor();
}

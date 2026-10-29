package com.weaksignal.vpp.backend.control.distributed;

import com.weaksignal.vpp.backend.domain.LinkState;
import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 单个资源智能体的隔离运行状态快照。
 *
 * @param nodeId 节点标识
 * @param linkState 链路状态
 * @param iteration 已完成迭代轮次
 * @param lambda 增量成本估计
 * @param mismatchEstimateKw 局部功率缺口估计，单位 kW
 * @param currentPowerKw 当前计划功率，单位 kW
 * @param soc 当前储能 SOC，非储能资源为 0
 * @param lastMessageSequenceByNeighbor 已接收的邻居消息序号
 * @param latestMessagesByNeighbor 最新有效邻居消息
 * @param messageSentCount 已发送消息数
 * @param messageLostCount 已拒绝消息数
 * @param expiredMessageCount 已拒绝的过期消息数
 */
public record DistributedAgentState(
        String nodeId,
        LinkState linkState,
        long iteration,
        double lambda,
        double mismatchEstimateKw,
        double currentPowerKw,
        double soc,
        Map<String, Long> lastMessageSequenceByNeighbor,
        Map<String, ConsensusMessage> latestMessagesByNeighbor,
        long messageSentCount,
        long messageLostCount,
        long expiredMessageCount
) {
}

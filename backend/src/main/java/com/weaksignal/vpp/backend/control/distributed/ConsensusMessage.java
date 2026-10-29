package com.weaksignal.vpp.backend.control.distributed;

import com.weaksignal.vpp.backend.domain.LinkState;
import java.time.OffsetDateTime;

/**
 * 邻居节点之间允许交换的一致性状态消息。
 *
 * @param schemaVersion 消息结构版本
 * @param messageId 消息唯一标识
 * @param experimentId 实验标识
 * @param senderNodeId 发送节点标识
 * @param receiverNodeId 接收节点标识
 * @param iteration 发送迭代轮次
 * @param sequence 消息序号
 * @param timestamp 发送时间
 * @param lambdaValue 增量成本估计
 * @param mismatchEstimateKw 局部功率缺口估计，单位 kW
 * @param actualPowerKw 当前实际功率，单位 kW
 * @param linkState 当前链路状态
 * @param latencyMillis 时延，单位 ms
 * @param packetLossRate 丢包率，范围 0 至 100
 */
public record ConsensusMessage(
        String schemaVersion,
        String messageId,
        String experimentId,
        String senderNodeId,
        String receiverNodeId,
        long iteration,
        long sequence,
        OffsetDateTime timestamp,
        double lambdaValue,
        double mismatchEstimateKw,
        double actualPowerKw,
        LinkState linkState,
        long latencyMillis,
        double packetLossRate
) {
    /**
     * 校验一致性消息的结构和数值范围。
     */
    public ConsensusMessage {
        if (isBlank(schemaVersion) || isBlank(messageId) || isBlank(experimentId) || isBlank(senderNodeId)
                || isBlank(receiverNodeId) || timestamp == null || iteration < 0 || sequence < 0
                || !Double.isFinite(lambdaValue) || !Double.isFinite(mismatchEstimateKw) || !Double.isFinite(actualPowerKw)
                || linkState == null || latencyMillis < 0 || packetLossRate < 0 || packetLossRate > 100) {
            throw new IllegalArgumentException("一致性消息字段不合法");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

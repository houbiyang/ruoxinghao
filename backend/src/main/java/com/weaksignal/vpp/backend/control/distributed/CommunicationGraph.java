package com.weaksignal.vpp.backend.control.distributed;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 分布式控制通信图，仅保存节点邻接白名单与基础权重。
 */
public final class CommunicationGraph {

    private final Map<String, Map<String, Double>> neighborWeights;

    /**
     * 创建通信图。
     *
     * @param neighborWeights 节点至邻居的基础权重
     */
    public CommunicationGraph(Map<String, Map<String, Double>> neighborWeights) {
        Map<String, Map<String, Double>> copiedWeights = new LinkedHashMap<>();
        neighborWeights.forEach((nodeId, weights) -> {
            if (nodeId == null || nodeId.isBlank() || weights == null) {
                throw new IllegalArgumentException("通信图节点配置不合法");
            }
            Map<String, Double> copiedNodeWeights = new LinkedHashMap<>();
            weights.forEach((neighborId, weight) -> {
                if (neighborId == null || neighborId.isBlank() || weight == null || !Double.isFinite(weight) || weight <= 0) {
                    throw new IllegalArgumentException("通信图权重不合法");
                }
                copiedNodeWeights.put(neighborId, weight);
            });
            copiedWeights.put(nodeId, Map.copyOf(copiedNodeWeights));
        });
        this.neighborWeights = Map.copyOf(copiedWeights);
    }

    /**
     * 获取节点邻居集合。
     *
     * @param nodeId 节点标识
     * @return 邻居集合
     */
    public Set<String> neighborsOf(String nodeId) {
        return neighborWeights.getOrDefault(nodeId, Map.of()).keySet();
    }

    /**
     * 判断接收节点是否允许接收发送节点消息。
     *
     * @param receiverNodeId 接收节点标识
     * @param senderNodeId 发送节点标识
     * @return 是否为合法邻居
     */
    public boolean isNeighbor(String receiverNodeId, String senderNodeId) {
        return neighborsOf(receiverNodeId).contains(senderNodeId);
    }

    /**
     * 获取基础邻接权重。
     *
     * @param receiverNodeId 接收节点标识
     * @param senderNodeId 发送节点标识
     * @return 基础权重
     */
    public double weightOf(String receiverNodeId, String senderNodeId) {
        return neighborWeights.getOrDefault(receiverNodeId, Map.of()).getOrDefault(senderNodeId, 0.0);
    }
}

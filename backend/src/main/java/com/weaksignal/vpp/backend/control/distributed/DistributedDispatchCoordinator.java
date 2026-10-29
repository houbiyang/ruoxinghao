package com.weaksignal.vpp.backend.control.distributed;

import com.weaksignal.vpp.backend.domain.LinkState;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

/**
 * 同进程仿真用的消息转发协调器。
 * 协调器只按通信图转发消息，不参与节点的本地功率和成本计算。
 */
public class DistributedDispatchCoordinator {

    private final Map<String, DistributedDispatchAgent> agentsByNodeId;
    private final ConvergenceEvaluator convergenceEvaluator;

    /**
     * 创建分布式调度协调器。
     *
     * @param agents 控制智能体集合
     * @param convergenceEvaluator 收敛判定器
     */
    public DistributedDispatchCoordinator(
            Collection<DistributedDispatchAgent> agents,
            ConvergenceEvaluator convergenceEvaluator
    ) {
        Map<String, DistributedDispatchAgent> indexedAgents = new LinkedHashMap<>();
        ArrayList<DistributedDispatchAgent> orderedAgents = new ArrayList<>(agents);
        orderedAgents.sort(Comparator.comparing(agent -> agent.snapshot().nodeId()));
        for (DistributedDispatchAgent agent : orderedAgents) {
            String nodeId = agent.snapshot().nodeId();
            if (indexedAgents.putIfAbsent(nodeId, agent) != null) {
                throw new IllegalArgumentException("存在重复的分布式控制节点: " + nodeId);
            }
        }
        this.agentsByNodeId = Collections.unmodifiableMap(indexedAgents);
        this.convergenceEvaluator = convergenceEvaluator;
    }

    /**
     * 执行一次消息交换和分布式一致性迭代。
     *
     * @param experimentId 实验标识
     * @param localDemandDeltaByNodeId 各节点本地需求变化，单位 kW
     * @param iterationHours 单次迭代对应小时数
     * @param now 当前时间
     * @param latencyMillis 时延，单位 ms
     * @param packetLossRate 丢包率，范围 0 至 100
     * @param photovoltaicPowerKw 光伏可用功率，单位 kW
     * @param windPowerKw 风电可用功率，单位 kW
     * @param loadPowerKw 总负荷功率，单位 kW
     * @return 本轮调度结果
     */
    public DispatchIterationResult iterate(
            String experimentId,
            Map<String, Double> localDemandDeltaByNodeId,
            double iterationHours,
            OffsetDateTime now,
            long latencyMillis,
            double packetLossRate,
            double photovoltaicPowerKw,
            double windPowerKw,
            double loadPowerKw
    ) {
        return iterate(
                experimentId, localDemandDeltaByNodeId, iterationHours, now, latencyMillis, packetLossRate,
                photovoltaicPowerKw, windPowerKw, loadPowerKw, new Random(0L)
        );
    }

    /**
     * 执行一次携带可重复通信丢失模型的消息交换和一致性迭代。
     *
     * @param experimentId 实验标识
     * @param localDemandDeltaByNodeId 各节点本地需求变化，单位 kW
     * @param iterationHours 单次迭代对应小时数
     * @param now 当前时间
     * @param latencyMillis 时延，单位 ms
     * @param packetLossRate 丢包率，范围 0 至 100
     * @param photovoltaicPowerKw 光伏可用功率，单位 kW
     * @param windPowerKw 风电可用功率，单位 kW
     * @param loadPowerKw 总负荷功率，单位 kW
     * @param random 随机源，用于复现实验中的丢包
     * @return 本轮调度结果
     */
    public DispatchIterationResult iterate(
            String experimentId,
            Map<String, Double> localDemandDeltaByNodeId,
            double iterationHours,
            OffsetDateTime now,
            long latencyMillis,
            double packetLossRate,
            double photovoltaicPowerKw,
            double windPowerKw,
            double loadPowerKw,
            Random random
    ) {
        if (random == null || packetLossRate < 0 || packetLossRate > 100) {
            throw new IllegalArgumentException("通信随机源或丢包率不合法");
        }
        for (DistributedDispatchAgent agent : agentsByNodeId.values()) {
            agent.createOutboundMessages(experimentId, now, latencyMillis, packetLossRate)
                    .forEach((receiverNodeId, message) -> {
                        DistributedDispatchAgent receiver = agentsByNodeId.get(receiverNodeId);
                        if (receiver != null) {
                            if (random.nextDouble() * 100.0 < packetLossRate) {
                                receiver.recordTransportLoss();
                            } else {
                                receiver.receive(message, now);
                            }
                        }
                    });
        }
        Map<String, DistributedDispatchAgent.DistributedDispatchDecision> decisions = new LinkedHashMap<>();
        for (Map.Entry<String, DistributedDispatchAgent> entry : agentsByNodeId.entrySet()) {
            double localDelta = localDemandDeltaByNodeId.getOrDefault(entry.getKey(), 0.0);
            decisions.put(entry.getKey(), entry.getValue().iterate(localDelta, iterationHours, now));
        }
        Map<String, DistributedAgentState> states = snapshots();
        ConvergenceEvaluator.ConvergenceMetrics metrics = convergenceEvaluator.evaluate(
                states.values(), photovoltaicPowerKw, windPowerKw, loadPowerKw
        );
        return new DispatchIterationResult(Map.copyOf(decisions), states, metrics);
    }

    /**
     * 切换指定节点的链路状态。
     *
     * @param nodeId 节点标识
     * @param linkState 新链路状态
     */
    public void changeLinkState(String nodeId, LinkState linkState) {
        DistributedDispatchAgent agent = agentsByNodeId.get(nodeId);
        if (agent == null) {
            throw new IllegalArgumentException("分布式控制节点不存在: " + nodeId);
        }
        agent.changeLinkState(linkState);
    }

    /**
     * 使用最新真实遥测同步各节点本地功率状态。
     *
     * @param actualPowerByNodeId 节点实际功率映射
     */
    public void synchronizeActualPower(Map<String, Double> actualPowerByNodeId) {
        if (actualPowerByNodeId == null) {
            throw new IllegalArgumentException("节点实际功率映射不能为空");
        }
        actualPowerByNodeId.forEach((nodeId, actualPowerKw) -> {
            DistributedDispatchAgent agent = agentsByNodeId.get(nodeId);
            if (agent != null) {
                agent.synchronizeActualPower(actualPowerKw);
            }
        });
    }

    /**
     * 获取所有节点状态快照。
     *
     * @return 节点状态快照
     */
    public Map<String, DistributedAgentState> snapshots() {
        Map<String, DistributedAgentState> states = new LinkedHashMap<>();
        agentsByNodeId.forEach((nodeId, agent) -> states.put(nodeId, agent.snapshot()));
        return Map.copyOf(states);
    }

    /**
     * 单轮分布式调度结果。
     *
     * @param decisions 节点候选决策
     * @param states 节点状态快照
     * @param convergenceMetrics 收敛指标
     */
    public record DispatchIterationResult(
            Map<String, DistributedDispatchAgent.DistributedDispatchDecision> decisions,
            Map<String, DistributedAgentState> states,
            ConvergenceEvaluator.ConvergenceMetrics convergenceMetrics
    ) {
    }
}

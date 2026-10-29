package com.weaksignal.vpp.backend.control.distributed;

import com.weaksignal.vpp.backend.domain.LinkState;
import com.weaksignal.vpp.backend.domain.ResourceType;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 可调资源的确定性分布式协同功率控制智能体。
 * 智能体仅保存本地状态和经通信图白名单接收的邻居一致性消息。
 */
public class DistributedDispatchAgent {

    private static final String SCHEMA_VERSION = "1.0";

    private final IncrementalCostModel costModel;
    private final CommunicationGraph communicationGraph;
    private final ConsensusPolicy policy;
    private final Map<String, ConsensusMessage> latestMessagesByNeighbor = new LinkedHashMap<>();
    private final Map<String, Long> lastMessageSequenceByNeighbor = new LinkedHashMap<>();
    private final Set<String> processedMessageIds = new LinkedHashSet<>();
    private LinkState linkState;
    private long iteration;
    private long nextMessageSequence;
    private double lambda;
    private double mismatchEstimateKw;
    private double currentPowerKw;
    private double soc;
    private long messageSentCount;
    private long messageLostCount;
    private long expiredMessageCount;

    /**
     * 创建资源控制智能体。
     *
     * @param costModel 节点本地成本与约束
     * @param communicationGraph 通信图
     * @param policy 通信质量自适应策略
     * @param initialPowerKw 初始功率，单位 kW
     * @param initialLocalDemandKw 本节点初始局部需求，单位 kW
     * @param initialSoc 初始 SOC，非储能资源填 0
     * @param initialLinkState 初始链路状态
     */
    public DistributedDispatchAgent(
            IncrementalCostModel costModel,
            CommunicationGraph communicationGraph,
            ConsensusPolicy policy,
            double initialPowerKw,
            double initialLocalDemandKw,
            double initialSoc,
            LinkState initialLinkState
    ) {
        if (!Double.isFinite(initialPowerKw) || !Double.isFinite(initialLocalDemandKw) || !Double.isFinite(initialSoc)
                || initialLinkState == null) {
            throw new IllegalArgumentException("智能体初始状态不合法");
        }
        this.costModel = costModel;
        this.communicationGraph = communicationGraph;
        this.policy = policy;
        this.currentPowerKw = projectPower(initialPowerKw, initialPowerKw, initialSoc, initialLinkState);
        this.mismatchEstimateKw = initialLocalDemandKw - this.currentPowerKw;
        this.lambda = 2.0 * costModel.quadraticCoefficient() * this.currentPowerKw + costModel.linearCoefficient();
        this.soc = initialSoc;
        this.linkState = initialLinkState;
    }

    /**
     * 接收邻居一致性消息，并执行邻居、重复、乱序、过期和数值校验。
     *
     * @param message 邻居消息
     * @param now 当前时间
     * @return 是否接受消息
     */
    public boolean receive(ConsensusMessage message, OffsetDateTime now) {
        if (!costModel.nodeId().equals(message.receiverNodeId())
                || !communicationGraph.isNeighbor(costModel.nodeId(), message.senderNodeId())
                || processedMessageIds.contains(message.messageId())) {
            messageLostCount++;
            return false;
        }
        long lastSequence = lastMessageSequenceByNeighbor.getOrDefault(message.senderNodeId(), -1L);
        if (message.sequence() <= lastSequence || message.iteration() < iteration) {
            messageLostCount++;
            return false;
        }
        if (!policy.isMessageValid(message, linkState, now)) {
            messageLostCount++;
            expiredMessageCount++;
            return false;
        }
        latestMessagesByNeighbor.put(message.senderNodeId(), message);
        lastMessageSequenceByNeighbor.put(message.senderNodeId(), message.sequence());
        processedMessageIds.add(message.messageId());
        return true;
    }

    /**
     * 切换节点链路状态。进入 S3 时清除邻居状态；进入 S4 时重置局部缺口估计。
     *
     * @param nextLinkState 新链路状态
     */
    public void changeLinkState(LinkState nextLinkState) {
        if (nextLinkState == null || nextLinkState == linkState) {
            return;
        }
        linkState = nextLinkState;
        if (nextLinkState == LinkState.S3) {
            latestMessagesByNeighbor.clear();
            lastMessageSequenceByNeighbor.clear();
        }
        if (nextLinkState == LinkState.S4) {
            mismatchEstimateKw = 0.0;
        }
    }

    /**
     * 基于本地状态和有效邻居消息执行一次确定性一致性迭代。
     *
     * @param localDemandDeltaKw 本节点局部需求变化，单位 kW
     * @param iterationHours 单次迭代对应的小时数，用于 SOC 更新
     * @param now 当前时间
     * @return 本次候选控制决策
     */
    public DistributedDispatchDecision iterate(double localDemandDeltaKw, double iterationHours, OffsetDateTime now) {
        if (!Double.isFinite(localDemandDeltaKw) || iterationHours <= 0 || !Double.isFinite(iterationHours)) {
            throw new IllegalArgumentException("本地迭代输入不合法");
        }
        if (linkState == LinkState.S3) {
            iteration++;
            return decision("LOCAL_AUTONOMY", currentPowerKw, currentPowerKw, false);
        }
        Map<String, ConsensusMessage> validMessages = validMessages(now);
        double selfWeight = 1.0;
        double weightedLambda = selfWeight * lambda;
        double weightedMismatch = selfWeight * mismatchEstimateKw;
        double denominator = selfWeight;
        for (ConsensusMessage message : validMessages.values()) {
            double weight = communicationGraph.weightOf(costModel.nodeId(), message.senderNodeId())
                    * policy.qualityScore(message, linkState, now);
            if (weight > 0) {
                weightedLambda += weight * message.lambdaValue();
                weightedMismatch += weight * message.mismatchEstimateKw();
                denominator += weight;
            }
        }
        double averageLambda = weightedLambda / denominator;
        double averageMismatch = weightedMismatch / denominator;
        double normalizedStepSize = policy.stepSize(linkState) * 2.0 * costModel.quadraticCoefficient();
        double nextLambda = averageLambda + normalizedStepSize * mismatchEstimateKw;
        double unconstrainedPowerKw = costModel.unconstrainedPower(nextLambda);
        double targetPowerKw = projectPower(unconstrainedPowerKw, currentPowerKw, soc, linkState);
        double powerDeltaKw = targetPowerKw - currentPowerKw;
        double nextMismatchEstimateKw = averageMismatch + localDemandDeltaKw - powerDeltaKw;
        double nextSoc = calculateNextSoc(targetPowerKw, iterationHours);

        lambda = nextLambda;
        currentPowerKw = targetPowerKw;
        mismatchEstimateKw = nextMismatchEstimateKw;
        soc = nextSoc;
        iteration++;
        return decision("CONSENSUS_ITERATION", unconstrainedPowerKw, targetPowerKw, targetPowerKw != unconstrainedPowerKw);
    }

    /**
     * 为每个合法邻居生成不含全局状态的当前一致性消息。
     *
     * @param experimentId 实验标识
     * @param now 当前时间
     * @param latencyMillis 当前测得时延，单位 ms
     * @param packetLossRate 当前测得丢包率，范围 0 至 100
     * @return 待发送消息集合
     */
    public Map<String, ConsensusMessage> createOutboundMessages(
            String experimentId,
            OffsetDateTime now,
            long latencyMillis,
            double packetLossRate
    ) {
        if (linkState == LinkState.S3) {
            return Map.of();
        }
        Map<String, ConsensusMessage> outboundMessages = new LinkedHashMap<>();
        for (String neighborId : communicationGraph.neighborsOf(costModel.nodeId())) {
            ConsensusMessage message = new ConsensusMessage(
                    SCHEMA_VERSION,
                    UUID.randomUUID().toString(),
                    experimentId,
                    costModel.nodeId(),
                    neighborId,
                    iteration,
                    nextMessageSequence++,
                    now,
                    lambda,
                    mismatchEstimateKw,
                    currentPowerKw,
                    linkState,
                    latencyMillis,
                    packetLossRate
            );
            outboundMessages.put(neighborId, message);
            messageSentCount++;
        }
        return Map.copyOf(outboundMessages);
    }

    /**
     * 记录通信层在发送前丢弃的一条一致性消息。
     */
    public void recordTransportLoss() {
        messageLostCount++;
    }

    /**
     * 使用最新真实遥测同步本地功率状态，避免连续迭代沿用已过时的计划功率。
     *
     * @param actualPowerKw 最新实际有功功率，单位 kW
     */
    public void synchronizeActualPower(double actualPowerKw) {
        if (!Double.isFinite(actualPowerKw)) {
            throw new IllegalArgumentException("实际有功功率不合法");
        }
        double previousPowerKw = currentPowerKw;
        currentPowerKw = Math.max(costModel.minimumPowerKw(), Math.min(costModel.maximumPowerKw(), actualPowerKw));
        mismatchEstimateKw += previousPowerKw - currentPowerKw;
        lambda = 2.0 * costModel.quadraticCoefficient() * currentPowerKw + costModel.linearCoefficient();
    }

    /**
     * 获取当前隔离状态快照。
     *
     * @return 智能体状态快照
     */
    public DistributedAgentState snapshot() {
        return new DistributedAgentState(
                costModel.nodeId(), linkState, iteration, lambda, mismatchEstimateKw, currentPowerKw, soc,
                Map.copyOf(lastMessageSequenceByNeighbor), Map.copyOf(latestMessagesByNeighbor),
                messageSentCount, messageLostCount, expiredMessageCount
        );
    }

    private Map<String, ConsensusMessage> validMessages(OffsetDateTime now) {
        Map<String, ConsensusMessage> validMessages = new LinkedHashMap<>();
        for (Map.Entry<String, ConsensusMessage> entry : latestMessagesByNeighbor.entrySet()) {
            if (policy.isMessageValid(entry.getValue(), linkState, now)) {
                validMessages.put(entry.getKey(), entry.getValue());
            } else {
                expiredMessageCount++;
            }
        }
        latestMessagesByNeighbor.keySet().retainAll(validMessages.keySet());
        return validMessages;
    }

    private double projectPower(double requestedPowerKw, double previousPowerKw, double currentSoc, LinkState currentLinkState) {
        double rampFactor = currentLinkState == LinkState.S4 ? policy.recoveryRampFactor() : 1.0;
        double lowerRampBound = previousPowerKw - costModel.maximumRampDownKw() * rampFactor;
        double upperRampBound = previousPowerKw + costModel.maximumRampUpKw() * rampFactor;
        double projectedPowerKw = Math.max(costModel.minimumPowerKw(), Math.min(costModel.maximumPowerKw(), requestedPowerKw));
        projectedPowerKw = Math.max(lowerRampBound, Math.min(upperRampBound, projectedPowerKw));
        if (costModel.resourceType() == ResourceType.STORAGE && costModel.energyCapacityKwh() > 0) {
            if (currentSoc <= costModel.minimumSoc()) {
                projectedPowerKw = Math.min(projectedPowerKw, 0.0);
            }
            if (currentSoc >= costModel.maximumSoc()) {
                projectedPowerKw = Math.max(projectedPowerKw, 0.0);
            }
        }
        return projectedPowerKw;
    }

    private double calculateNextSoc(double targetPowerKw, double iterationHours) {
        if (costModel.resourceType() != ResourceType.STORAGE || costModel.energyCapacityKwh() <= 0) {
            return soc;
        }
        double energyDeltaKwh = targetPowerKw >= 0
                ? -targetPowerKw / costModel.dischargeEfficiency() * iterationHours
                : -targetPowerKw * costModel.chargeEfficiency() * iterationHours;
        double calculatedSoc = soc + energyDeltaKwh / costModel.energyCapacityKwh() * 100.0;
        return Math.max(costModel.minimumSoc(), Math.min(costModel.maximumSoc(), calculatedSoc));
    }

    private DistributedDispatchDecision decision(
            String reasonCode,
            double unconstrainedPowerKw,
            double targetPowerKw,
            boolean projectionApplied
    ) {
        return new DistributedDispatchDecision(
                costModel.nodeId(), iteration, linkState, reasonCode, lambda, mismatchEstimateKw,
                unconstrainedPowerKw, targetPowerKw, projectionApplied, linkState != LinkState.S3
        );
    }

    /**
     * 智能体本地候选功率决策。
     *
     * @param nodeId 节点标识
     * @param iteration 迭代轮次
     * @param linkState 链路状态
     * @param reasonCode 决策原因码
     * @param lambdaValue 增量成本估计
     * @param mismatchEstimateKw 局部缺口估计，单位 kW
     * @param unconstrainedPowerKw 未约束目标功率，单位 kW
     * @param targetPowerKw 投影后的候选功率，单位 kW
     * @param projectionApplied 是否已投影
     * @param safeToExecute 是否可进入确定性安全门
     */
    public record DistributedDispatchDecision(
            String nodeId,
            long iteration,
            LinkState linkState,
            String reasonCode,
            double lambdaValue,
            double mismatchEstimateKw,
            double unconstrainedPowerKw,
            double targetPowerKw,
            boolean projectionApplied,
            boolean safeToExecute
    ) {
    }
}

package com.weaksignal.vpp.backend.control.distributed;

import com.weaksignal.vpp.backend.domain.LinkState;
import java.util.Collection;

/**
 * 基于功率平衡误差和增量成本一致性误差的收敛判定器。
 */
public class ConvergenceEvaluator {

    private final DistributedDispatchProperties properties;
    private int consecutiveSatisfiedIterations;

    /**
     * 注入分布式控制配置。
     *
     * @param properties 分布式控制配置
     */
    public ConvergenceEvaluator(DistributedDispatchProperties properties) {
        this.properties = properties;
    }

    /**
     * 评估当前迭代是否收敛。
     *
     * @param states 所有可调节点状态快照
     * @param photovoltaicPowerKw 光伏可用功率，单位 kW
     * @param windPowerKw 风电可用功率，单位 kW
     * @param loadPowerKw 负荷功率，单位 kW
     * @return 收敛指标
     */
    public ConvergenceMetrics evaluate(
            Collection<DistributedAgentState> states,
            double photovoltaicPowerKw,
            double windPowerKw,
            double loadPowerKw
    ) {
        if (states.isEmpty()) {
            return new ConvergenceMetrics(false, 0, 0, 0, 0);
        }
        double controllablePower = states.stream().mapToDouble(DistributedAgentState::currentPowerKw).sum();
        double balanceError = Math.abs(controllablePower + photovoltaicPowerKw + windPowerKw - loadPowerKw);
        double minimumLambda = states.stream()
                .filter(state -> state.linkState() != LinkState.S3)
                .mapToDouble(DistributedAgentState::lambda)
                .min()
                .orElse(0);
        double maximumLambda = states.stream()
                .filter(state -> state.linkState() != LinkState.S3)
                .mapToDouble(DistributedAgentState::lambda)
                .max()
                .orElse(0);
        double consensusError = maximumLambda - minimumLambda;
        boolean satisfied = balanceError < properties.balanceToleranceKw() && consensusError < properties.consensusTolerance();
        consecutiveSatisfiedIterations = satisfied ? consecutiveSatisfiedIterations + 1 : 0;
        return new ConvergenceMetrics(
                consecutiveSatisfiedIterations >= properties.requiredConsecutiveIterations(),
                balanceError,
                consensusError,
                consecutiveSatisfiedIterations,
                controllablePower
        );
    }

    /**
     * 收敛性量化指标。
     *
     * @param converged 是否收敛
     * @param balanceErrorKw 功率平衡误差，单位 kW
     * @param consensusError 增量成本一致性误差
     * @param consecutiveSatisfiedIterations 连续满足阈值的轮数
     * @param controllablePowerKw 可调资源总功率，单位 kW
     */
    public record ConvergenceMetrics(
            boolean converged,
            double balanceErrorKw,
            double consensusError,
            int consecutiveSatisfiedIterations,
            double controllablePowerKw
    ) {
    }
}

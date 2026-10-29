package com.weaksignal.vpp.backend.control.distributed;

import com.weaksignal.vpp.backend.domain.ResourceType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 在资源硬约束内将分布式候选目标投影为满足市场供需平衡的可执行目标。
 */
public class DistributedMarketBalanceProjector {

    /**
     * 闭合市场供需缺口。不可执行资源按其实际功率作为固定注入参与平衡。
     *
     * @param fixedGenerationKw 光伏、风电等不可调资源的实际供电功率
     * @param demandKw 负荷和充电负荷的实际需求功率
     * @param targets 可调资源候选目标及其安全边界
     * @param toleranceKw 市场平衡容差
     * @return 市场平衡投影结果
     */
    public BalanceProjection project(
            double fixedGenerationKw,
            double demandKw,
            List<ResourceTarget> targets,
            double toleranceKw
    ) {
        if (!Double.isFinite(fixedGenerationKw) || !Double.isFinite(demandKw) || demandKw < 0
                || targets == null || !Double.isFinite(toleranceKw) || toleranceKw < 0) {
            throw new IllegalArgumentException("市场平衡投影输入不合法");
        }
        Map<String, Double> projectedTargets = new LinkedHashMap<>();
        for (ResourceTarget target : targets) {
            projectedTargets.put(target.stationId(), clamp(target.candidatePowerKw(), target.lowerBoundKw(), target.upperBoundKw()));
        }
        double residualKw = demandKw - fixedGenerationKw - projectedTargets.values().stream().mapToDouble(Double::doubleValue).sum();
        double initialResidualKw = residualKw;
        List<ResourceTarget> adjustmentOrder = new ArrayList<>(targets);
        adjustmentOrder.sort(Comparator.comparingInt(target -> priority(target.resourceType(), initialResidualKw)));
        List<BalanceAdjustment> adjustments = new ArrayList<>();
        for (ResourceTarget target : adjustmentOrder) {
            if (!target.executable() || Math.abs(residualKw) <= toleranceKw) {
                continue;
            }
            double previousTarget = projectedTargets.get(target.stationId());
            double availableChange = residualKw > 0
                    ? target.upperBoundKw() - previousTarget
                    : previousTarget - target.lowerBoundKw();
            double appliedChange = Math.copySign(Math.min(Math.abs(residualKw), Math.max(0.0, availableChange)), residualKw);
            if (Math.abs(appliedChange) <= 0.000001) {
                continue;
            }
            double nextTarget = previousTarget + appliedChange;
            projectedTargets.put(target.stationId(), nextTarget);
            residualKw -= appliedChange;
            adjustments.add(new BalanceAdjustment(target.stationId(), previousTarget, nextTarget, appliedChange));
        }
        double plannedSupplyKw = fixedGenerationKw + projectedTargets.values().stream().mapToDouble(Double::doubleValue).sum();
        double plannedBalanceErrorKw = Math.abs(plannedSupplyKw - demandKw);
        return new BalanceProjection(
                Map.copyOf(projectedTargets),
                List.copyOf(adjustments),
                fixedGenerationKw,
                demandKw,
                plannedSupplyKw,
                plannedBalanceErrorKw,
                plannedBalanceErrorKw <= toleranceKw,
                plannedBalanceErrorKw <= toleranceKw ? "MARKET_BALANCED" : "INSUFFICIENT_ADJUSTABLE_CAPACITY"
        );
    }

    private int priority(ResourceType resourceType, double residualKw) {
        if (residualKw < 0) {
            return switch (resourceType) {
                case STORAGE -> 0;
                case GRID -> 1;
                case THERMAL -> 2;
                default -> 3;
            };
        }
        return switch (resourceType) {
            case GRID -> 0;
            case STORAGE -> 1;
            case THERMAL -> 2;
            default -> 3;
        };
    }

    private double clamp(double value, double lowerBound, double upperBound) {
        return Math.max(lowerBound, Math.min(upperBound, value));
    }

    /**
     * 可调资源的候选目标及其本轮安全边界。
     *
     * @param stationId 场站标识
     * @param resourceType 资源类型
     * @param executable 是否允许远程控制
     * @param candidatePowerKw 分布式候选功率
     * @param lowerBoundKw 含容量和爬坡约束的功率下界
     * @param upperBoundKw 含容量和爬坡约束的功率上界
     */
    public record ResourceTarget(
            String stationId,
            ResourceType resourceType,
            boolean executable,
            double candidatePowerKw,
            double lowerBoundKw,
            double upperBoundKw
    ) {
        /**
         * 校验资源目标边界。
         */
        public ResourceTarget {
            if (stationId == null || stationId.isBlank() || resourceType == null
                    || !Double.isFinite(candidatePowerKw) || !Double.isFinite(lowerBoundKw)
                    || !Double.isFinite(upperBoundKw) || lowerBoundKw > upperBoundKw) {
                throw new IllegalArgumentException("市场平衡资源目标不合法");
            }
        }
    }

    /**
     * 一次市场缺口修正记录。
     *
     * @param stationId 场站标识
     * @param previousTargetKw 修正前目标功率
     * @param projectedTargetKw 修正后目标功率
     * @param adjustmentKw 本次功率调整量
     */
    public record BalanceAdjustment(
            String stationId,
            double previousTargetKw,
            double projectedTargetKw,
            double adjustmentKw
    ) {
    }

    /**
     * 市场平衡投影结果。
     *
     * @param targetPowersKw 最终逐资源目标
     * @param adjustments 市场缺口修正记录
     * @param fixedGenerationKw 不可调供电功率
     * @param demandKw 市场需求功率
     * @param plannedSupplyKw 计划总供电功率
     * @param plannedBalanceErrorKw 计划供需平衡误差
     * @param feasible 是否在硬约束内满足市场需求
     * @param reasonCode 结果编码
     */
    public record BalanceProjection(
            Map<String, Double> targetPowersKw,
            List<BalanceAdjustment> adjustments,
            double fixedGenerationKw,
            double demandKw,
            double plannedSupplyKw,
            double plannedBalanceErrorKw,
            boolean feasible,
            String reasonCode
    ) {
    }
}

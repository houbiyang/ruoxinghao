package com.weaksignal.vpp.backend.marketdispatch;

import java.util.List;
import java.util.Map;

/**
 * 优化结果。
 *
 * @param items 计划时间片集合
 * @param solverName 求解器名称
 * @param solverVersion 求解器版本
 * @param status 求解状态
 * @param objectiveValue 目标函数值
 * @param solveTimeMs 求解耗时，单位毫秒
 * @param optimalityGap 相对最优间隙
 * @param optimizationRound MPC 轮次
 * @param snapshotId 调度上下文快照标识
 * @param plannedBalanceError 计划供需误差
 * @param constraintEvidence 约束证据
 * @param fallbackReason 规则回退原因
 * @param targetsByItemId 按计划项标识关联、按场站稳定排序的逐资源目标
 * @param targetSummaryHashesByItemId 按计划项标识关联的逐资源目标摘要哈希
 */
public record OptimizationResult(
        List<DispatchPlanItem> items, String solverName, String solverVersion, OptimizationStatus status,
        Double objectiveValue, long solveTimeMs, Double optimalityGap, int optimizationRound, String snapshotId,
        Double plannedBalanceError, Map<String, Object> constraintEvidence, String fallbackReason,
        Map<String, List<ResourceDispatchTarget>> targetsByItemId,
        Map<String, String> targetSummaryHashesByItemId
) {
    /**
     * 固化逐资源目标集合与摘要哈希，确保优化结果离开优化器后不可变。
     */
    public OptimizationResult {
        targetsByItemId = targetsByItemId == null ? Map.of() : targetsByItemId.entrySet().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().stream()
                                .sorted(java.util.Comparator.comparing(ResourceDispatchTarget::stationId))
                                .toList()
                ));
        targetSummaryHashesByItemId = Map.copyOf(targetSummaryHashesByItemId == null ? Map.of() : targetSummaryHashesByItemId);
    }

    /**
     * 兼容既有规则优化器结果。
     *
     * @param items 计划时间片集合
     */
    public OptimizationResult(List<DispatchPlanItem> items) {
        this(items, "RULE_BASED", "N/A", OptimizationStatus.RULE_FALLBACK, null, 0, null, 0, null, null, Map.of(), null, Map.of(), Map.of());
    }
}

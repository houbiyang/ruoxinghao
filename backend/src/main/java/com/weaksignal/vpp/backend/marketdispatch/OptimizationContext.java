package com.weaksignal.vpp.backend.marketdispatch;

import com.weaksignal.vpp.backend.planning.DispatchContextSnapshot;

/**
 * 优化输入上下文。
 *
 * @param dispatchContextSnapshot 调度上下文快照
 * @param conservativePlan 是否生成保守计划
 * @param experimentScenarioCode 实时注入实验场景编码，普通调度为空
 */
public record OptimizationContext(
        DispatchContextSnapshot dispatchContextSnapshot,
        boolean conservativePlan,
        String experimentScenarioCode
) {

    /**
     * 创建普通调度优化上下文。
     *
     * @param dispatchContextSnapshot 调度上下文快照
     * @param conservativePlan 是否生成保守计划
     */
    public OptimizationContext(DispatchContextSnapshot dispatchContextSnapshot, boolean conservativePlan) {
        this(dispatchContextSnapshot, conservativePlan, null);
    }
}

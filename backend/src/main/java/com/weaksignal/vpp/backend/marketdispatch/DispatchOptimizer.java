package com.weaksignal.vpp.backend.marketdispatch;

/**
 * 调度优化器接口。
 */
public interface DispatchOptimizer {

    /**
     * 执行确定性市场调度优化。
     *
     * @param optimizationContext 优化上下文
     * @return 优化结果
     */
    OptimizationResult optimize(OptimizationContext optimizationContext);
}

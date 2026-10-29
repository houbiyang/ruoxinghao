package com.weaksignal.vpp.backend.marketdispatch;

/** OR-Tools 或规则降级的优化结果状态。 */
public enum OptimizationStatus {
    OPTIMAL, FEASIBLE, TIME_LIMIT_WITH_FEASIBLE_SOLUTION, TIME_LIMIT_WITHOUT_SOLUTION,
    INFEASIBLE, NATIVE_LOAD_FAILED, SOLVER_FAILED, RULE_FALLBACK
}

package com.weaksignal.vpp.backend.marketdispatch;

import com.weaksignal.vpp.backend.config.OptimizationProperties;
import java.util.Map;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 统一选择 OR-Tools MILP 或规则降级优化器。规则结果仍由后续计划校验与安全门复核。
 */
@Primary
@Component
public class DispatchOptimizerSelector implements DispatchOptimizer {
    private final OrToolsDispatchOptimizer orToolsDispatchOptimizer;
    private final RuleBasedDispatchOptimizer ruleBasedDispatchOptimizer;
    private final OptimizationProperties properties;
    private final com.weaksignal.vpp.backend.application.SolverObservabilityService observabilityService;

    /** 注入可选优化器。 */
    public DispatchOptimizerSelector(OrToolsDispatchOptimizer orToolsDispatchOptimizer, RuleBasedDispatchOptimizer ruleBasedDispatchOptimizer, OptimizationProperties properties,
            com.weaksignal.vpp.backend.application.SolverObservabilityService observabilityService) {
        this.orToolsDispatchOptimizer = orToolsDispatchOptimizer;
        this.ruleBasedDispatchOptimizer = ruleBasedDispatchOptimizer;
        this.properties = properties;
        this.observabilityService = observabilityService;
    }

    /** 优先执行 OR-Tools；无可行解或原生库失败时按配置执行规则降级。 */
    @Override
    public OptimizationResult optimize(OptimizationContext context) {
        OptimizationResult result = orToolsDispatchOptimizer.optimize(context);
        if (result.status() == OptimizationStatus.OPTIMAL || result.status() == OptimizationStatus.FEASIBLE) return result;
        if (!properties.enableRuleFallback()) return result;
        OptimizationResult fallback = ruleBasedDispatchOptimizer.optimize(context);
        OptimizationResult fallbackResult = new OptimizationResult(fallback.items(), fallback.solverName(), fallback.solverVersion(), OptimizationStatus.RULE_FALLBACK,
                fallback.objectiveValue(), fallback.solveTimeMs(), fallback.optimalityGap(), fallback.optimizationRound(),
                context.dispatchContextSnapshot().snapshotId(), fallback.plannedBalanceError(),
                Map.of("originalStatus", result.status().name(), "originalEvidence", result.constraintEvidence()), result.fallbackReason(),
                fallback.targetsByItemId(), fallback.targetSummaryHashesByItemId());
        observabilityService.append(fallbackResult, context.dispatchContextSnapshot().resourceCapabilitySnapshots().size(),
                context.dispatchContextSnapshot().forecastSnapshot().points().size(), 0, 0, 0, true);
        return fallbackResult;
    }
}

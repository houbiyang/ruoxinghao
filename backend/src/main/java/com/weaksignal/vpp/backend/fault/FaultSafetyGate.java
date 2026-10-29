package com.weaksignal.vpp.backend.fault;

import com.weaksignal.vpp.backend.infrastructure.persistence.entity.StationEntity;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.StationRepository;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 多智能体候选方案的确定性安全门。
 */
@Component
public class FaultSafetyGate {

    private static final Set<AgentType> DECISION_CONSENSUS_AGENT_TYPES = EnumSet.of(
            AgentType.COMMUNICATION_QUALITY,
            AgentType.CONTROL_DECISION
    );

    private final StationRepository stationRepository;
    private final ConservativeFallbackPolicy fallbackPolicy;

    /**
     * 注入安全校验依赖。
     *
     * @param stationRepository 场站仓储
     * @param fallbackPolicy 保守回退策略
     */
    public FaultSafetyGate(
            StationRepository stationRepository,
            ConservativeFallbackPolicy fallbackPolicy
    ) {
        this.stationRepository = stationRepository;
        this.fallbackPolicy = fallbackPolicy;
    }

    /**
     * 汇总智能体意见并执行最终裁决。
     *
     * @param context 故障上下文
     * @param ruleDecision 规则结论
     * @param responses 智能体意见
     * @return 安全裁决
     */
    public FaultSafetyDecision decide(
            FaultContext context,
            FaultRuleDecision ruleDecision,
            List<AgentStructuredResponse> responses
    ) {
        boolean providerFallback = responses.stream().allMatch(response -> "rule".equals(response.provider()));
        boolean criticalConflict = hasCriticalDecisionConflict(responses);
        boolean coordinatableDifference = hasCoordinatableDifference(responses);
        boolean suspectedAttack = context.suspectedAttack()
                || responses.stream().anyMatch(response -> response.riskExplanation().contains("攻击"));

        List<ProposedFaultAction> sourceActions = criticalConflict
                ? fallbackPolicy.actions(context, ruleDecision)
                : responses.stream().flatMap(response -> response.proposedActions().stream()).toList();
        if (sourceActions.isEmpty() && ruleDecision.matched()) {
            sourceActions = fallbackPolicy.actions(context, ruleDecision);
        }

        Map<String, ProposedFaultAction> uniqueActions = new LinkedHashMap<>();
        for (ProposedFaultAction action : sourceActions) {
            StationEntity station = stationRepository.findByStationId(action.stationId()).orElse(null);
            if (station == null || !station.isEnabled() || action.actionType() == null) {
                continue;
            }
            uniqueActions.put(action.actionType().name() + ":" + action.stationId(), action);
        }

        List<ProposedFaultAction> approvedActions = new ArrayList<>(uniqueActions.values());
        boolean containsHighRiskAction = approvedActions.stream()
                .anyMatch(action -> !action.actionType().isAutomaticWhitelistAction());
        boolean highRiskObserved = suspectedAttack
                || criticalConflict
                || containsHighRiskAction
                || responses.stream().anyMatch(AgentStructuredResponse::requiresHumanApproval)
                || ruleDecision.severity() == FaultSeverity.CRITICAL
                || approvedActions.stream().map(ProposedFaultAction::stationId).distinct().count() > 1;
        String explanation = highRiskObserved
                ? "安全门已记录高风险证据，按确定性规则自动裁决并进入可观察执行链路。"
                : coordinatableDifference
                ? "检测到可协调差异，安全门保留证据并按主裁决链路执行。"
                : "动作均在低风险白名单内，可由独立执行服务自动下发。";
        return new FaultSafetyDecision(
                approvedActions,
                false,
                criticalConflict,
                providerFallback || highRiskObserved,
                explanation
        );
    }

    private boolean hasCriticalDecisionConflict(List<AgentStructuredResponse> responses) {
        List<AgentStructuredResponse> decisionResponses = responses.stream()
                .filter(response -> DECISION_CONSENSUS_AGENT_TYPES.contains(response.agentType()))
                .toList();
        if (decisionResponses.size() < 2) {
            return false;
        }
        long distinctFaultCodes = decisionResponses.stream().map(AgentStructuredResponse::faultCode).distinct().count();
        long distinctModes = decisionResponses.stream().map(AgentStructuredResponse::recommendedMode).distinct().count();
        return distinctFaultCodes > 1 || distinctModes > 1;
    }

    private boolean hasCoordinatableDifference(List<AgentStructuredResponse> responses) {
        long distinctModes = responses.stream().map(AgentStructuredResponse::recommendedMode).distinct().count();
        return distinctModes > 1 && !hasCriticalDecisionConflict(responses);
    }
}

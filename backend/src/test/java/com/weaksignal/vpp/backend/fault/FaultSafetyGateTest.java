package com.weaksignal.vpp.backend.fault;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.weaksignal.vpp.backend.domain.ControlMode;
import com.weaksignal.vpp.backend.domain.LinkState;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.StationEntity;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.StationRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 故障安全门测试。
 */
class FaultSafetyGateTest {

    private FaultSafetyGate safetyGate;

    @BeforeEach
    void setUp() {
        StationRepository repository = mock(StationRepository.class);
        StationEntity station = new StationEntity();
        station.setStationId("pv-gobi-01");
        station.setEnabled(true);
        when(repository.findByStationId("pv-gobi-01")).thenReturn(Optional.of(station));
        safetyGate = new FaultSafetyGate(repository, new ConservativeFallbackPolicy());
    }

    @Test
    void shouldAllowLowRiskWhitelistAction() {
        FaultSafetyDecision decision = safetyGate.decide(
                context(false),
                rule(FaultSeverity.MEDIUM),
                List.of(response(ControlMode.EDGE_COORDINATION, FaultActionType.LIMIT_CONTROL_RAMP, false))
        );
        assertFalse(decision.requiresHumanApproval());
    }

    @Test
    void shouldAutoDecideAttackAndIsolation() {
        FaultSafetyDecision decision = safetyGate.decide(
                context(true),
                rule(FaultSeverity.CRITICAL),
                List.of(response(ControlMode.LOCAL_AUTONOMY, FaultActionType.ISOLATE_STATION, true))
        );
        assertFalse(decision.requiresHumanApproval());
        assertTrue(decision.fallbackUsed());
    }

    @Test
    void shouldUseFallbackWhenAgentsConflict() {
        FaultSafetyDecision decision = safetyGate.decide(
                context(false),
                rule(FaultSeverity.HIGH),
                List.of(
                        response(AgentType.COMMUNICATION_QUALITY, ControlMode.EDGE_COORDINATION, FaultActionType.LIMIT_CONTROL_RAMP, false),
                        response(AgentType.CONTROL_DECISION, ControlMode.LOCAL_AUTONOMY, FaultActionType.ENTER_LOCAL_AUTONOMY, false)
                )
        );
        assertTrue(decision.conflictDetected());
        assertTrue(decision.fallbackUsed());
        assertFalse(decision.requiresHumanApproval());
    }

    @Test
    void shouldIgnoreSpecialistModeDifferences() {
        FaultSafetyDecision decision = safetyGate.decide(
                context(false),
                rule(FaultSeverity.HIGH),
                List.of(
                        response(AgentType.COMMUNICATION_QUALITY, ControlMode.LOCAL_AUTONOMY, FaultActionType.ENTER_LOCAL_AUTONOMY, false),
                        response(AgentType.CONTROL_DECISION, ControlMode.LOCAL_AUTONOMY, FaultActionType.ENTER_LOCAL_AUTONOMY, false),
                        response(AgentType.STATE_DIAGNOSIS, ControlMode.EDGE_COORDINATION, FaultActionType.ENABLE_CONSERVATIVE_ESTIMATE, false),
                        response(AgentType.RECOVERY_ORCHESTRATION, ControlMode.EDGE_COORDINATION, FaultActionType.START_RATE_LIMITED_RECOVERY, false)
                )
        );
        assertFalse(decision.conflictDetected());
        assertTrue(decision.explanation().contains("可协调差异"));
    }

    private FaultContext context(boolean attack) {
        return new FaultContext(null, "pv-gobi-01", LinkState.S1, 120, 50, 2,
                0, 4, 1, 0, false, false, 0, false, attack, 100, 168, true);
    }

    private FaultRuleDecision rule(FaultSeverity severity) {
        return new FaultRuleDecision(true, "C1", "LATENCY", severity, LinkState.S1,
                List.of(), true, List.of());
    }

    private AgentStructuredResponse response(
            ControlMode mode,
            FaultActionType actionType,
            boolean approval
    ) {
        return response(AgentType.CONTROL_DECISION, mode, actionType, approval);
    }

    private AgentStructuredResponse response(
            AgentType agentType,
            ControlMode mode,
            FaultActionType actionType,
            boolean approval
    ) {
        return new AgentStructuredResponse(
                agentType,
                "rule",
                "C1",
                "LATENCY",
                List.of("pv-gobi-01"),
                FaultSeverity.MEDIUM,
                0.9,
                List.of(),
                mode,
                List.of(new ProposedFaultAction(actionType, "pv-gobi-01", Map.of(), "测试动作")),
                approval,
                approval ? "疑似攻击风险" : "低风险保护动作"
        );
    }
}

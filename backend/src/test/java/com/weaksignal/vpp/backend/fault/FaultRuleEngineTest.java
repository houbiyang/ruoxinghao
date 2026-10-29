package com.weaksignal.vpp.backend.fault;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.weaksignal.vpp.backend.config.StateRuleProperties;
import com.weaksignal.vpp.backend.domain.LinkState;
import org.junit.jupiter.api.Test;

/**
 * 确定性故障规则引擎测试。
 */
class FaultRuleEngineTest {

    private final FaultRuleEngine ruleEngine = new FaultRuleEngine(new StateRuleProperties(
            80, 1.0, 3, 150, 3.0, 6, 500, 20.0, 15, 15, 5, 6
    ));

    @Test
    void shouldClassifyHeartbeatTimeoutAsFullDisconnection() {
        FaultRuleDecision decision = ruleEngine.evaluate(context(40, 0.2, 20, 1, false, false, false));
        assertEquals("C6", decision.faultCode());
        assertEquals(LinkState.S3, decision.suggestedLinkState());
    }

    @Test
    void shouldClassifyStaleTelemetry() {
        FaultRuleDecision decision = ruleEngine.evaluate(context(40, 0.2, 1, 18, false, false, true));
        assertEquals("STALE_TELEMETRY", decision.faultCode());
        assertTrue(decision.immediateProtectionActions().stream()
                .anyMatch(action -> action.actionType() == FaultActionType.MARK_DATA_UNTRUSTED));
    }

    @Test
    void shouldIdentifyDuplicateAndSequenceRollback() {
        FaultRuleDecision decision = ruleEngine.evaluate(context(40, 0.2, 1, 1, true, true, false));
        assertEquals("C8", decision.faultCode());
    }

    @Test
    void shouldClassifyRandomAndBurstPacketLoss() {
        assertEquals("C3", ruleEngine.evaluate(context(40, 1.5, 1, 1, false, false, false)).faultCode());
        assertEquals("C4", ruleEngine.evaluate(context(40, 8.0, 1, 1, false, false, false)).faultCode());
    }

    @Test
    void shouldClassifyLatencyThresholds() {
        assertEquals("C1", ruleEngine.evaluate(context(100, 0.2, 1, 1, false, false, false)).faultCode());
        assertEquals("C2", ruleEngine.evaluate(context(180, 0.2, 1, 1, false, false, false)).faultCode());
    }

    @Test
    void shouldForceSecurityReviewForSuspectedAttack() {
        FaultContext context = new FaultContext(null, "pv-gobi-01", LinkState.S1, 40, 0, 0.2,
                0, 1, 1, 0, false, false, 0, false, true, 100, 168, true);
        FaultRuleDecision decision = ruleEngine.evaluate(context);
        assertEquals("SECURITY", decision.faultCode());
        assertEquals(FaultSeverity.CRITICAL, decision.severity());
    }

    private FaultContext context(
            int latency,
            double packetLoss,
            long heartbeatAge,
            long dataAge,
            boolean duplicate,
            boolean staleSequence,
            boolean frozen
    ) {
        return new FaultContext(null, "pv-gobi-01", LinkState.S0, latency, 0, packetLoss,
                0, dataAge, heartbeatAge, 0, duplicate, staleSequence, 0, frozen, false,
                100, 168, true);
    }
}

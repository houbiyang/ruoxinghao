package com.weaksignal.vpp.backend.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.weaksignal.vpp.backend.config.StateRuleProperties;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

/**
 * 链路状态判定器测试。
 */
class LinkStateEvaluatorTest {

    private final LinkStateEvaluator evaluator = new LinkStateEvaluator(new StateRuleProperties(
            80,
            1.0,
            3,
            150,
            3.0,
            6,
            500,
            20.0,
            15,
            15,
            5,
            6
    ));

    @Test
    void shouldKeepStableStateForHealthySample() {
        OffsetDateTime now = OffsetDateTime.parse("2026-06-29T21:30:00+08:00");
        StateContext context = new StateContext(LinkState.S0, now.minusSeconds(20), now.minusSeconds(1), 8, now.minusSeconds(2));

        StateDecision decision = evaluator.evaluate(context, new StationMetricSample(35, 0.3, 1, true), now);

        assertEquals(LinkState.S0, decision.nextState());
        assertFalse(decision.changed());
    }

    @Test
    void shouldEnterDegradedStateWhenLatencyCrossesThreshold() {
        OffsetDateTime now = OffsetDateTime.parse("2026-06-29T21:30:00+08:00");
        StateContext context = new StateContext(LinkState.S0, now.minusSeconds(20), now.minusSeconds(1), 8, now.minusSeconds(2));

        StateDecision decision = evaluator.evaluate(context, new StationMetricSample(120, 0.8, 2, true), now);

        assertEquals(LinkState.S1, decision.nextState());
        assertTrue(decision.changed());
    }

    @Test
    void shouldEnterSevereStateWhenPacketLossCrossesThreshold() {
        OffsetDateTime now = OffsetDateTime.parse("2026-06-29T21:30:00+08:00");
        StateContext context = new StateContext(LinkState.S1, now.minusSeconds(20), now.minusSeconds(1), 8, now.minusSeconds(2));

        StateDecision decision = evaluator.evaluate(context, new StationMetricSample(180, 25.0, 8, true), now);

        assertEquals(LinkState.S2, decision.nextState());
        assertTrue(decision.changed());
    }

    @Test
    void shouldRespectMinimumHoldTime() {
        OffsetDateTime now = OffsetDateTime.parse("2026-06-29T21:30:00+08:00");
        StateContext context = new StateContext(LinkState.S1, now.minusSeconds(2), now.minusSeconds(1), 8, now.minusSeconds(2));

        StateDecision decision = evaluator.evaluate(context, new StationMetricSample(35, 0.2, 1, true), now);

        assertEquals(LinkState.S1, decision.nextState());
        assertFalse(decision.changed());
    }

    @Test
    void shouldMoveToLostStateWhenHeartbeatTimeout() {
        OffsetDateTime now = OffsetDateTime.parse("2026-06-29T21:30:20+08:00");
        StateContext context = new StateContext(LinkState.S1, now.minusSeconds(20), now.minusSeconds(16), 8, now.minusSeconds(18));

        StateDecision decision = evaluator.evaluateHeartbeatTimeout(context, now);

        assertEquals(LinkState.S3, decision.nextState());
        assertTrue(decision.changed());
    }

    @Test
    void shouldEnterRecoveringAfterLostReceivesValidSample() {
        OffsetDateTime now = OffsetDateTime.parse("2026-06-29T21:30:20+08:00");
        StateContext context = new StateContext(LinkState.S3, now.minusSeconds(20), now.minusSeconds(1), 8, now.minusSeconds(18));

        StateDecision decision = evaluator.evaluate(context, new StationMetricSample(60, 0.4, 1, true), now);

        assertEquals(LinkState.S4, decision.nextState());
        assertTrue(decision.changed());
    }
}


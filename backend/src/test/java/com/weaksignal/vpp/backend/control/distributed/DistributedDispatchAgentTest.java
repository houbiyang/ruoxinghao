package com.weaksignal.vpp.backend.control.distributed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.weaksignal.vpp.backend.domain.LinkState;
import com.weaksignal.vpp.backend.domain.ResourceType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 分布式协同功率控制智能体测试。
 */
class DistributedDispatchAgentTest {

    private final DistributedDispatchProperties properties = new DistributedDispatchProperties(
            "distributed-control-test-v1", 0.05, 0.03, 0.01, 0.02,
            200, 500, 1000, 500, 1000, 3000, 1500, 2000,
            0.34, 0.33, 0.33, 1.0, 0.01, 2, 0.5
    );
    private final AdaptiveConsensusPolicy policy = new AdaptiveConsensusPolicy(properties);
    private final CommunicationGraph graph = new CommunicationGraph(Map.of(
            "thermal-01", Map.of("grid-01", 1.0),
            "grid-01", Map.of("thermal-01", 1.0)
    ));
    private final OffsetDateTime now = OffsetDateTime.parse("2026-07-20T10:00:00+08:00");

    @Test
    void shouldRespectPowerAndRampConstraints() {
        DistributedDispatchAgent agent = thermalAgent(40.0, 1040.0, LinkState.S0);

        DistributedDispatchAgent.DistributedDispatchDecision decision = agent.iterate(0.0, 1.0 / 3600.0, now);

        assertTrue(decision.targetPowerKw() <= 50.0);
        assertTrue(decision.targetPowerKw() >= 30.0);
        assertTrue(decision.projectionApplied());
    }

    @Test
    void shouldRejectExpiredAndDuplicateNeighborMessages() {
        DistributedDispatchAgent agent = thermalAgent(40.0, 40.0, LinkState.S0);
        ConsensusMessage expiredMessage = message("expired", 1, now.minusSeconds(2));
        ConsensusMessage validMessage = message("valid", 2, now);

        assertFalse(agent.receive(expiredMessage, now));
        assertTrue(agent.receive(validMessage, now));
        assertFalse(agent.receive(validMessage, now));

        DistributedAgentState state = agent.snapshot();
        assertEquals(2, state.messageLostCount());
        assertEquals(1, state.expiredMessageCount());
    }

    @Test
    void shouldUseLowerStepsForDegradedStatesAndEnterLocalAutonomy() {
        DistributedDispatchAgent agent = thermalAgent(40.0, 40.0, LinkState.S1);

        assertTrue(policy.stepSize(LinkState.S1) < policy.stepSize(LinkState.S0));
        assertTrue(policy.stepSize(LinkState.S2) < policy.stepSize(LinkState.S1));
        agent.changeLinkState(LinkState.S3);
        DistributedDispatchAgent.DistributedDispatchDecision decision = agent.iterate(0, 1.0 / 3600.0, now);

        assertEquals("LOCAL_AUTONOMY", decision.reasonCode());
        assertFalse(decision.safeToExecute());
        assertTrue(agent.createOutboundMessages("experiment-1", now, 0, 0).isEmpty());
    }

    @Test
    void shouldConstrainRecoveryRampAndConvergeForBalancedNormalCommunication() {
        DistributedDispatchAgent thermalAgent = thermalAgent(40.0, 40.0, LinkState.S4);
        DistributedDispatchAgent gridAgent = gridAgent(40.0, 40.0, LinkState.S0);
        DistributedDispatchCoordinator coordinator = new DistributedDispatchCoordinator(
                List.of(thermalAgent, gridAgent), new ConvergenceEvaluator(properties)
        );

        DistributedDispatchCoordinator.DispatchIterationResult firstResult = coordinator.iterate(
                "experiment-1", Map.of(), 1.0 / 3600.0, now, 20, 0, 0, 0, 80
        );
        DistributedDispatchCoordinator.DispatchIterationResult secondResult = coordinator.iterate(
                "experiment-1", Map.of(), 1.0 / 3600.0, now.plusNanos(200_000_000), 20, 0, 0, 0, 80
        );

        assertTrue(firstResult.states().get("thermal-01").currentPowerKw() <= 45.0);
        assertTrue(secondResult.convergenceMetrics().converged());
        assertEquals(firstResult.states().get("thermal-01").currentPowerKw(), secondResult.states().get("thermal-01").currentPowerKw());
    }

    @Test
    void shouldKeepStorageSocWithinBounds() {
        IncrementalCostModel storageModel = new IncrementalCostModel(
                "storage-01", ResourceType.STORAGE, 0.01, 0, 0,
                -30, 30, 30, 30, 10, 90, 100, 0.95, 0.95
        );
        DistributedDispatchAgent storageAgent = new DistributedDispatchAgent(
                storageModel, new CommunicationGraph(Map.of("storage-01", Map.of())), policy,
                0, 100, 90, LinkState.S0
        );

        storageAgent.iterate(100, 1.0, now);

        assertTrue(storageAgent.snapshot().soc() >= 10.0);
        assertTrue(storageAgent.snapshot().soc() <= 90.0);
    }

    @Test
    void shouldPreservePowerMismatchWhenSynchronizingActualPower() {
        DistributedDispatchAgent agent = thermalAgent(40.0, 60.0, LinkState.S0);

        agent.synchronizeActualPower(30.0);

        assertEquals(30.0, agent.snapshot().currentPowerKw(), 0.000001);
        assertEquals(30.0, agent.snapshot().mismatchEstimateKw(), 0.000001);
    }

    @Test
    void shouldProduceSameResultRegardlessOfAgentInputOrder() {
        DistributedDispatchCoordinator forwardCoordinator = new DistributedDispatchCoordinator(
                List.of(thermalAgent(45.0, 35.0, LinkState.S0), gridAgent(35.0, 35.0, LinkState.S0)),
                new ConvergenceEvaluator(properties)
        );
        DistributedDispatchCoordinator reverseCoordinator = new DistributedDispatchCoordinator(
                List.of(gridAgent(35.0, 35.0, LinkState.S0), thermalAgent(45.0, 35.0, LinkState.S0)),
                new ConvergenceEvaluator(properties)
        );

        DistributedDispatchCoordinator.DispatchIterationResult forward = forwardCoordinator.iterate(
                "experiment-1", Map.of(), 1.0 / 3600.0, now, 80, 25, 20, 10, 100,
                new java.util.Random(101L)
        );
        DistributedDispatchCoordinator.DispatchIterationResult reverse = reverseCoordinator.iterate(
                "experiment-1", Map.of(), 1.0 / 3600.0, now, 80, 25, 20, 10, 100,
                new java.util.Random(101L)
        );

        assertEquals(forward.states().get("thermal-01").currentPowerKw(),
                reverse.states().get("thermal-01").currentPowerKw(), 0.000001);
        assertEquals(forward.states().get("grid-01").currentPowerKw(),
                reverse.states().get("grid-01").currentPowerKw(), 0.000001);
        assertEquals(forward.convergenceMetrics().balanceErrorKw(),
                reverse.convergenceMetrics().balanceErrorKw(), 0.000001);
    }

    private DistributedDispatchAgent thermalAgent(double initialPower, double localDemand, LinkState linkState) {
        return new DistributedDispatchAgent(
                new IncrementalCostModel("thermal-01", ResourceType.THERMAL, 0.01, 0, 0,
                        0, 100, 10, 10, 0, 100, 0, 1, 1),
                graph, policy, initialPower, localDemand, 0, linkState
        );
    }

    private DistributedDispatchAgent gridAgent(double initialPower, double localDemand, LinkState linkState) {
        return new DistributedDispatchAgent(
                new IncrementalCostModel("grid-01", ResourceType.GRID, 0.01, 0, 0,
                        0, 100, 10, 10, 0, 100, 0, 1, 1),
                graph, policy, initialPower, localDemand, 0, linkState
        );
    }

    private ConsensusMessage message(String messageId, long sequence, OffsetDateTime timestamp) {
        return new ConsensusMessage(
                "1.0", messageId, "experiment-1", "grid-01", "thermal-01", 0, sequence,
                timestamp, 0.8, 0, 40, LinkState.S0, 20, 0
        );
    }
}

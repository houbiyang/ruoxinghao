package com.weaksignal.vpp.backend.control.distributed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.weaksignal.vpp.backend.domain.ResourceType;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 分布式候选目标的市场平衡投影测试。
 */
class DistributedMarketBalanceProjectorTest {

    private final DistributedMarketBalanceProjector projector = new DistributedMarketBalanceProjector();

    /**
     * 供电不足时应优先使用电网联络线闭合缺口。
     */
    @Test
    void shouldUseGridToClosePositiveMarketGap() {
        DistributedMarketBalanceProjector.BalanceProjection result = projector.project(
                80.0,
                150.0,
                List.of(
                        target("thermal-01", ResourceType.THERMAL, 20.0, 10.0, 30.0),
                        target("grid-01", ResourceType.GRID, 10.0, 0.0, 80.0),
                        target("storage-01", ResourceType.STORAGE, 0.0, -20.0, 20.0)
                ),
                1.0
        );

        assertTrue(result.feasible());
        assertEquals(50.0, result.targetPowersKw().get("grid-01"));
        assertEquals(0.0, result.plannedBalanceErrorKw());
    }

    /**
     * 新能源过剩时应优先让储能吸收功率。
     */
    @Test
    void shouldUseStorageToAbsorbSurplusPower() {
        DistributedMarketBalanceProjector.BalanceProjection result = projector.project(
                180.0,
                120.0,
                List.of(
                        target("grid-01", ResourceType.GRID, 0.0, 0.0, 60.0),
                        target("storage-01", ResourceType.STORAGE, 0.0, -80.0, 30.0)
                ),
                1.0
        );

        assertTrue(result.feasible());
        assertEquals(-60.0, result.targetPowersKw().get("storage-01"));
        assertEquals(0.0, result.plannedBalanceErrorKw());
    }

    /**
     * 可调容量不足时必须明确报告不可行，不能伪造市场平衡。
     */
    @Test
    void shouldReportInsufficientAdjustableCapacity() {
        DistributedMarketBalanceProjector.BalanceProjection result = projector.project(
                20.0,
                200.0,
                List.of(target("grid-01", ResourceType.GRID, 20.0, 0.0, 80.0)),
                1.0
        );

        assertFalse(result.feasible());
        assertEquals(100.0, result.plannedBalanceErrorKw());
        assertEquals("INSUFFICIENT_ADJUSTABLE_CAPACITY", result.reasonCode());
    }

    private DistributedMarketBalanceProjector.ResourceTarget target(
            String stationId,
            ResourceType resourceType,
            double candidatePower,
            double lowerBound,
            double upperBound
    ) {
        return new DistributedMarketBalanceProjector.ResourceTarget(
                stationId, resourceType, true, candidatePower, lowerBound, upperBound
        );
    }
}

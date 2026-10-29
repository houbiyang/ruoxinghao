package com.weaksignal.vpp.backend.marketdispatch;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** OR-Tools Windows 原生库加载与 SCIP MILP 冒烟测试。 */
class OrToolsRuntimeTest {

    /** 应加载原生库并创建 SCIP 求解器。 */
    @Test
    void shouldLoadNativeLibraryAndCreateScipSolver() {
        OrToolsAvailability availability = new OrToolsRuntime().availability();
        assertTrue(availability.solverAvailable(), availability.failureReason());
    }
}

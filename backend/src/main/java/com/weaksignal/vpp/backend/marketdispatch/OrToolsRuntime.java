package com.weaksignal.vpp.backend.marketdispatch;

import com.google.ortools.Loader;
import com.google.ortools.linearsolver.MPSolver;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Component;

/**
 * OR-Tools 原生库延迟加载器。加载失败仅记录状态，不阻止 Spring Boot 启动。
 */
@Component
public class OrToolsRuntime {
    private volatile OrToolsAvailability availability;
    private volatile OffsetDateTime lastCheckAt;

    /** 获取求解器可用性，并在首次调用时尝试加载原生库。 */
    public OrToolsAvailability availability() {
        OrToolsAvailability current = availability;
        if (current != null) return current;
        synchronized (this) {
            if (availability != null) return availability;
            try {
                Loader.loadNativeLibraries();
                MPSolver solver = MPSolver.createSolver("SCIP");
                availability = solver == null
                        ? new OrToolsAvailability(false, "SCIP", null, "SCIP_MIP_SOLVER_UNAVAILABLE")
                        : new OrToolsAvailability(true, "OR_TOOLS_SCIP", solver.solverVersion(), null);
            } catch (Throwable exception) {
                availability = new OrToolsAvailability(false, "OR_TOOLS_SCIP", null,
                        exception.getClass().getSimpleName() + ": " + exception.getMessage());
            }
            lastCheckAt = OffsetDateTime.now();
            return availability;
        }
    }

    /**
     * 返回最近一次原生库探测时间。
     *
     * @return 探测时间；尚未探测时为空
     */
    public OffsetDateTime lastCheckAt() {
        return lastCheckAt;
    }
}

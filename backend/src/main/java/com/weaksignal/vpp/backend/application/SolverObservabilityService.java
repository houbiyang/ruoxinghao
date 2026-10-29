package com.weaksignal.vpp.backend.application;

import com.weaksignal.vpp.backend.api.dto.SolverPerformanceAuditPageResponse;
import com.weaksignal.vpp.backend.api.dto.SolverPerformanceAuditResponse;
import com.weaksignal.vpp.backend.api.dto.SolverStatusResponse;
import com.weaksignal.vpp.backend.config.OptimizationProperties;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.SolverPerformanceAuditEntity;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.SolverPerformanceAuditRepository;
import com.weaksignal.vpp.backend.marketdispatch.OptimizationResult;
import com.weaksignal.vpp.backend.marketdispatch.OrToolsAvailability;
import com.weaksignal.vpp.backend.marketdispatch.OrToolsRuntime;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/** OR-Tools 可用性与求解性能审计查询服务。 */
@Service
public class SolverObservabilityService {
    private static final int MAX_PAGE_SIZE = 100;
    private final OrToolsRuntime runtime; private final OptimizationProperties properties; private final SolverPerformanceAuditRepository repository; private final Clock clock;
    private volatile String lastSolveStatus; private volatile Long lastSolveTimeMs; private volatile boolean fallbackActive;
    public SolverObservabilityService(OrToolsRuntime runtime, OptimizationProperties properties, SolverPerformanceAuditRepository repository, Clock clock) { this.runtime = runtime; this.properties = properties; this.repository = repository; this.clock = clock; }
    public SolverStatusResponse status() {
        OrToolsAvailability availability = runtime.availability();
        return new SolverStatusResponse("OR_TOOLS_MILP", availability.solverName(), availability.solverVersion(), "OR_TOOLS_MILP".equals(properties.optimizerType()), availability.solverAvailable(), availability.solverAvailable(), runtime.lastCheckAt(), lastSolveStatus, lastSolveTimeMs, properties.enableRuleFallback(), fallbackActive, failureCategory(availability.failureReason()), safeReason(availability.failureReason()));
    }
    public void append(OptimizationResult result, int resourceCount, int periods, int continuousVariables, int integerVariables, int constraints, boolean fallback) {
        SolverPerformanceAuditEntity entity = new SolverPerformanceAuditEntity(); entity.setExperimentRunId(null); entity.setOptimizationRound(result.optimizationRound()); entity.setSnapshotId(result.snapshotId()); entity.setPlanId(null); entity.setResourceCount(resourceCount); entity.setTimePeriodCount(periods); entity.setContinuousVariableCount(continuousVariables); entity.setIntegerVariableCount(integerVariables); entity.setConstraintCount(constraints); entity.setStatus(result.status().name()); entity.setSolveTimeMs(result.solveTimeMs()); entity.setObjectiveValue(result.objectiveValue()); entity.setOptimalityGap(result.optimalityGap()); entity.setTimedOut(result.status().name().startsWith("TIME_LIMIT")); entity.setFallback(fallback); entity.setCreatedAt(OffsetDateTime.now(clock)); repository.save(entity); lastSolveStatus = entity.getStatus(); lastSolveTimeMs = entity.getSolveTimeMs(); fallbackActive = fallback;
    }

    /**
     * 在计划或实验标识产生后补齐最近一次快照求解审计的业务关联。
     *
     * @param snapshotId 调度上下文快照标识
     * @param planId 调度计划标识
     * @param experimentRunId 实验运行标识，可为空
     * @param optimizationRound MPC 优化轮次
     */
    public void bindBusinessContext(String snapshotId, String planId, String experimentRunId, int optimizationRound) {
        if (snapshotId == null || snapshotId.isBlank()) return;
        repository.findFirstBySnapshotIdOrderByCreatedAtDesc(snapshotId).ifPresent(entity -> {
            if (planId != null && !planId.isBlank()) entity.setPlanId(planId);
            if (experimentRunId != null && !experimentRunId.isBlank()) entity.setExperimentRunId(experimentRunId);
            entity.setOptimizationRound(Math.max(0, optimizationRound));
            repository.save(entity);
        });
    }
    public SolverPerformanceAuditPageResponse audits(int page, int size) {
        int effectivePage = Math.max(0, page); int effectiveSize = Math.min(MAX_PAGE_SIZE, Math.max(1, size));
        return new SolverPerformanceAuditPageResponse(repository.findAllByOrderByCreatedAtDesc(PageRequest.of(effectivePage, effectiveSize)).stream().map(this::response).toList(), effectivePage, effectiveSize);
    }
    private SolverPerformanceAuditResponse response(SolverPerformanceAuditEntity value) { return new SolverPerformanceAuditResponse(value.getExperimentRunId(), value.getOptimizationRound(), value.getSnapshotId(), value.getPlanId(), value.getResourceCount(), value.getTimePeriodCount(), value.getContinuousVariableCount(), value.getIntegerVariableCount(), value.getConstraintCount(), value.getStatus(), value.getSolveTimeMs(), value.getObjectiveValue(), value.getOptimalityGap(), value.isTimedOut(), value.isFallback(), value.getCreatedAt()); }
    private String failureCategory(String reason) { if (reason == null || reason.isBlank()) return null; if (reason.contains("Native") || reason.contains("UnsatisfiedLink")) return "NATIVE_LIBRARY_LOAD_FAILED"; if (reason.contains("SCIP")) return "SOLVER_UNAVAILABLE"; return "SOLVER_INITIALIZATION_FAILED"; }
    private String safeReason(String reason) { return failureCategory(reason); }
}

package com.weaksignal.vpp.backend.api.dto;

import java.time.OffsetDateTime;

/** 单次求解性能审计记录。 */
public record SolverPerformanceAuditResponse(String experimentRunId, int round, String snapshotId, String planId,
        int resourceCount, int timePeriodCount, int continuousVariableCount, int integerVariableCount, int constraintCount,
        String status, long solveTimeMs, Double objectiveValue, Double optimalityGap, boolean timedOut, boolean fallback, OffsetDateTime createdAt) { }

package com.weaksignal.vpp.backend.infrastructure.persistence.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/** 仅追加的 OR-Tools 求解性能审计实体。 */
@Entity
@Table(name = "solver_performance_audit")
public class SolverPerformanceAuditEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private String experimentRunId; private int optimizationRound; private String snapshotId; private String planId;
    private int resourceCount; private int timePeriodCount; private int continuousVariableCount; private int integerVariableCount; private int constraintCount;
    private String status; private long solveTimeMs; private Double objectiveValue; private Double optimalityGap; private boolean timedOut; private boolean fallback; private OffsetDateTime createdAt;
    public String getExperimentRunId() { return experimentRunId; } public void setExperimentRunId(String value) { experimentRunId = value; }
    public int getOptimizationRound() { return optimizationRound; } public void setOptimizationRound(int value) { optimizationRound = value; }
    public String getSnapshotId() { return snapshotId; } public void setSnapshotId(String value) { snapshotId = value; }
    public String getPlanId() { return planId; } public void setPlanId(String value) { planId = value; }
    public int getResourceCount() { return resourceCount; } public void setResourceCount(int value) { resourceCount = value; }
    public int getTimePeriodCount() { return timePeriodCount; } public void setTimePeriodCount(int value) { timePeriodCount = value; }
    public int getContinuousVariableCount() { return continuousVariableCount; } public void setContinuousVariableCount(int value) { continuousVariableCount = value; }
    public int getIntegerVariableCount() { return integerVariableCount; } public void setIntegerVariableCount(int value) { integerVariableCount = value; }
    public int getConstraintCount() { return constraintCount; } public void setConstraintCount(int value) { constraintCount = value; }
    public String getStatus() { return status; } public void setStatus(String value) { status = value; }
    public long getSolveTimeMs() { return solveTimeMs; } public void setSolveTimeMs(long value) { solveTimeMs = value; }
    public Double getObjectiveValue() { return objectiveValue; } public void setObjectiveValue(Double value) { objectiveValue = value; }
    public Double getOptimalityGap() { return optimalityGap; } public void setOptimalityGap(Double value) { optimalityGap = value; }
    public boolean isTimedOut() { return timedOut; } public void setTimedOut(boolean value) { timedOut = value; }
    public boolean isFallback() { return fallback; } public void setFallback(boolean value) { fallback = value; }
    public OffsetDateTime getCreatedAt() { return createdAt; } public void setCreatedAt(OffsetDateTime value) { createdAt = value; }
}

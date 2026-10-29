package com.weaksignal.vpp.backend.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;

/**
 * 实验调度的单轮遥测收敛审计实体。
 */
@Entity
@Table(name = "dispatch_convergence_round", uniqueConstraints = @UniqueConstraint(
        name = "uk_dispatch_convergence_round_experiment_round", columnNames = {"experiment_run_id", "round_number"}))
public class DispatchConvergenceRoundEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "round_id", nullable = false, unique = true, length = 64)
    private String roundId;

    @Column(name = "experiment_run_id", nullable = false, length = 64)
    private String experimentRunId;

    @Column(name = "round_number", nullable = false)
    private int roundNumber;

    @Column(name = "source_plan_id", length = 64)
    private String sourcePlanId;

    @Column(name = "snapshot_id", length = 64)
    private String snapshotId;

    @Column(name = "agent_run_id", length = 64)
    private String agentRunId;

    @Column(name = "replacement_plan_id", length = 64)
    private String replacementPlanId;

    @Column(name = "execution_batch_id", length = 64)
    private String executionBatchId;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "trigger_reason", nullable = false, length = 64)
    private String triggerReason;

    @Column(name = "consecutive_success_count", nullable = false)
    private int consecutiveSuccessCount;

    @Column(name = "audit_json", nullable = false, columnDefinition = "TEXT")
    private String auditJson;

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;

    @Column(name = "ended_at")
    private OffsetDateTime endedAt;

    public Long getId() { return id; }
    public String getRoundId() { return roundId; }
    public void setRoundId(String roundId) { this.roundId = roundId; }
    public String getExperimentRunId() { return experimentRunId; }
    public void setExperimentRunId(String experimentRunId) { this.experimentRunId = experimentRunId; }
    public int getRoundNumber() { return roundNumber; }
    public void setRoundNumber(int roundNumber) { this.roundNumber = roundNumber; }
    public String getSourcePlanId() { return sourcePlanId; }
    public void setSourcePlanId(String sourcePlanId) { this.sourcePlanId = sourcePlanId; }
    public String getSnapshotId() { return snapshotId; }
    public void setSnapshotId(String snapshotId) { this.snapshotId = snapshotId; }
    public String getAgentRunId() { return agentRunId; }
    public void setAgentRunId(String agentRunId) { this.agentRunId = agentRunId; }
    public String getReplacementPlanId() { return replacementPlanId; }
    public void setReplacementPlanId(String replacementPlanId) { this.replacementPlanId = replacementPlanId; }
    public String getExecutionBatchId() { return executionBatchId; }
    public void setExecutionBatchId(String executionBatchId) { this.executionBatchId = executionBatchId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getTriggerReason() { return triggerReason; }
    public void setTriggerReason(String triggerReason) { this.triggerReason = triggerReason; }
    public int getConsecutiveSuccessCount() { return consecutiveSuccessCount; }
    public void setConsecutiveSuccessCount(int consecutiveSuccessCount) { this.consecutiveSuccessCount = consecutiveSuccessCount; }
    public String getAuditJson() { return auditJson; }
    public void setAuditJson(String auditJson) { this.auditJson = auditJson; }
    public OffsetDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(OffsetDateTime startedAt) { this.startedAt = startedAt; }
    public OffsetDateTime getEndedAt() { return endedAt; }
    public void setEndedAt(OffsetDateTime endedAt) { this.endedAt = endedAt; }
}

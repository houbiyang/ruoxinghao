package com.weaksignal.vpp.backend.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/**
 * 故障诊断运行审计实体。
 */
@Entity
@Table(name = "fault_diagnosis_run")
public class FaultDiagnosisRunEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "run_id", nullable = false, unique = true, length = 64)
    private String runId;
    @Column(name = "event_id", length = 64)
    private String eventId;
    @Column(name = "station_id", nullable = false, length = 64)
    private String stationId;
    @Column(name = "rule_result_json", nullable = false, columnDefinition = "TEXT")
    private String ruleResultJson;
    @Column(name = "final_decision_json", nullable = false, columnDefinition = "TEXT")
    private String finalDecisionJson;
    @Column(nullable = false, length = 32)
    private String status;
    @Column(name = "approval_status", nullable = false, length = 32)
    private String approvalStatus;
    @Column(name = "conflict_detected", nullable = false)
    private boolean conflictDetected;
    @Column(name = "fallback_used", nullable = false)
    private boolean fallbackUsed;
    @Column(name = "review_reason", length = 512)
    private String reviewReason;
    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public Long getId() { return id; }
    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }
    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public String getStationId() { return stationId; }
    public void setStationId(String stationId) { this.stationId = stationId; }
    public String getRuleResultJson() { return ruleResultJson; }
    public void setRuleResultJson(String ruleResultJson) { this.ruleResultJson = ruleResultJson; }
    public String getFinalDecisionJson() { return finalDecisionJson; }
    public void setFinalDecisionJson(String finalDecisionJson) { this.finalDecisionJson = finalDecisionJson; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getApprovalStatus() { return approvalStatus; }
    public void setApprovalStatus(String approvalStatus) { this.approvalStatus = approvalStatus; }
    public boolean isConflictDetected() { return conflictDetected; }
    public void setConflictDetected(boolean conflictDetected) { this.conflictDetected = conflictDetected; }
    public boolean isFallbackUsed() { return fallbackUsed; }
    public void setFallbackUsed(boolean fallbackUsed) { this.fallbackUsed = fallbackUsed; }
    public String getReviewReason() { return reviewReason; }
    public void setReviewReason(String reviewReason) { this.reviewReason = reviewReason; }
    public OffsetDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(OffsetDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}

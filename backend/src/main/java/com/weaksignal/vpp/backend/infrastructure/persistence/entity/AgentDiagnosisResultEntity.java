package com.weaksignal.vpp.backend.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/**
 * 单智能体结构化诊断结果实体。
 */
@Entity
@Table(name = "agent_diagnosis_result")
public class AgentDiagnosisResultEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "result_id", nullable = false, unique = true, length = 64)
    private String resultId;
    @Column(name = "run_id", nullable = false, length = 64)
    private String runId;
    @Column(name = "agent_type", nullable = false, length = 64)
    private String agentType;
    @Column(nullable = false, length = 32)
    private String provider;
    @Column(name = "fault_code", nullable = false, length = 64)
    private String faultCode;
    @Column(nullable = false, length = 16)
    private String severity;
    @Column(nullable = false)
    private double confidence;
    @Column(name = "result_json", nullable = false, columnDefinition = "TEXT")
    private String resultJson;
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    public Long getId() { return id; }
    public String getResultId() { return resultId; }
    public void setResultId(String resultId) { this.resultId = resultId; }
    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }
    public String getAgentType() { return agentType; }
    public void setAgentType(String agentType) { this.agentType = agentType; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getFaultCode() { return faultCode; }
    public void setFaultCode(String faultCode) { this.faultCode = faultCode; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }
    public String getResultJson() { return resultJson; }
    public void setResultJson(String resultJson) { this.resultJson = resultJson; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}

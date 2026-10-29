package com.weaksignal.vpp.backend.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/**
 * 故障处置动作计划实体。
 */
@Entity
@Table(name = "fault_action_plan")
public class FaultActionPlanEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "action_id", nullable = false, unique = true, length = 64)
    private String actionId;
    @Column(name = "run_id", nullable = false, length = 64)
    private String runId;
    @Column(name = "station_id", nullable = false, length = 64)
    private String stationId;
    @Column(name = "action_type", nullable = false, length = 64)
    private String actionType;
    @Column(name = "parameters_json", nullable = false, columnDefinition = "TEXT")
    private String parametersJson;
    @Column(name = "risk_level", nullable = false, length = 16)
    private String riskLevel;
    @Column(nullable = false, length = 32)
    private String status;
    @Column(name = "requires_approval", nullable = false)
    private boolean requiresApproval;
    @Column(name = "mqtt_command_id", length = 64)
    private String mqttCommandId;
    @Column(name = "issued_at")
    private OffsetDateTime issuedAt;
    @Column(name = "expire_at")
    private OffsetDateTime expireAt;
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public Long getId() { return id; }
    public String getActionId() { return actionId; }
    public void setActionId(String actionId) { this.actionId = actionId; }
    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }
    public String getStationId() { return stationId; }
    public void setStationId(String stationId) { this.stationId = stationId; }
    public String getActionType() { return actionType; }
    public void setActionType(String actionType) { this.actionType = actionType; }
    public String getParametersJson() { return parametersJson; }
    public void setParametersJson(String parametersJson) { this.parametersJson = parametersJson; }
    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public boolean isRequiresApproval() { return requiresApproval; }
    public void setRequiresApproval(boolean requiresApproval) { this.requiresApproval = requiresApproval; }
    public String getMqttCommandId() { return mqttCommandId; }
    public void setMqttCommandId(String mqttCommandId) { this.mqttCommandId = mqttCommandId; }
    public OffsetDateTime getIssuedAt() { return issuedAt; }
    public void setIssuedAt(OffsetDateTime issuedAt) { this.issuedAt = issuedAt; }
    public OffsetDateTime getExpireAt() { return expireAt; }
    public void setExpireAt(OffsetDateTime expireAt) { this.expireAt = expireAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}

package com.weaksignal.vpp.backend.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/**
 * 控制命令实体。
 */
@Entity
@Table(name = "control_command")
public class ControlCommandEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "command_id", nullable = false, unique = true, length = 64)
    private String commandId;

    @Column(name = "station_id", nullable = false, length = 64)
    private String stationId;

    @Column(name = "command_type", nullable = false, length = 64)
    private String commandType;

    @Column(name = "batch_id", length = 64)
    private String batchId;

    @Column(name = "dispatch_plan_id", length = 64)
    private String dispatchPlanId;

    @Column(name = "plan_version", length = 64)
    private String planVersion;

    @Column(name = "dispatch_plan_item_id", length = 64)
    private String dispatchPlanItemId;

    @Column(name = "input_snapshot_id", length = 64)
    private String inputSnapshotId;

    @Column(name = "command_payload", nullable = false, columnDefinition = "TEXT")
    private String commandPayload;

    @Column(name = "send_time", nullable = false)
    private OffsetDateTime sendTime;

    @Column(name = "expire_at", nullable = false)
    private OffsetDateTime expireAt;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    public Long getId() {
        return id;
    }

    public String getCommandId() {
        return commandId;
    }

    public void setCommandId(String commandId) {
        this.commandId = commandId;
    }

    public String getStationId() {
        return stationId;
    }

    public void setStationId(String stationId) {
        this.stationId = stationId;
    }

    public String getCommandType() {
        return commandType;
    }

    public void setCommandType(String commandType) {
        this.commandType = commandType;
    }

    public String getBatchId() {
        return batchId;
    }

    public void setBatchId(String batchId) {
        this.batchId = batchId;
    }

    public String getDispatchPlanId() {
        return dispatchPlanId;
    }

    public void setDispatchPlanId(String dispatchPlanId) {
        this.dispatchPlanId = dispatchPlanId;
    }

    public String getPlanVersion() {
        return planVersion;
    }

    public void setPlanVersion(String planVersion) {
        this.planVersion = planVersion;
    }

    public String getDispatchPlanItemId() {
        return dispatchPlanItemId;
    }

    public void setDispatchPlanItemId(String dispatchPlanItemId) {
        this.dispatchPlanItemId = dispatchPlanItemId;
    }

    public String getInputSnapshotId() {
        return inputSnapshotId;
    }

    public void setInputSnapshotId(String inputSnapshotId) {
        this.inputSnapshotId = inputSnapshotId;
    }

    public String getCommandPayload() {
        return commandPayload;
    }

    public void setCommandPayload(String commandPayload) {
        this.commandPayload = commandPayload;
    }

    public OffsetDateTime getSendTime() {
        return sendTime;
    }

    public void setSendTime(OffsetDateTime sendTime) {
        this.sendTime = sendTime;
    }

    public OffsetDateTime getExpireAt() {
        return expireAt;
    }

    public void setExpireAt(OffsetDateTime expireAt) {
        this.expireAt = expireAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}

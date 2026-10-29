package com.weaksignal.vpp.backend.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/**
 * 命令确认实体。
 */
@Entity
@Table(name = "command_ack")
public class CommandAckEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "command_id", nullable = false, length = 64)
    private String commandId;

    @Column(name = "station_id", nullable = false, length = 64)
    private String stationId;

    @Column(name = "ack_stage", nullable = false, length = 32)
    private String ackStage;

    @Column(nullable = false)
    private boolean success;

    @Column(length = 256)
    private String message;

    @Column(name = "ack_time", nullable = false)
    private OffsetDateTime ackTime;

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

    public String getAckStage() {
        return ackStage;
    }

    public void setAckStage(String ackStage) {
        this.ackStage = ackStage;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public OffsetDateTime getAckTime() {
        return ackTime;
    }

    public void setAckTime(OffsetDateTime ackTime) {
        this.ackTime = ackTime;
    }
}


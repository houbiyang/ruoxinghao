package com.weaksignal.vpp.backend.infrastructure.persistence.entity;

import com.weaksignal.vpp.backend.domain.LinkState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/**
 * 链路状态变更日志实体。
 */
@Entity
@Table(name = "link_status_log")
public class LinkStatusLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "station_id", nullable = false, length = 64)
    private String stationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_state", length = 8)
    private LinkState previousState;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_state", nullable = false, length = 8)
    private LinkState currentState;

    @Column(nullable = false, length = 256)
    private String reason;

    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt;

    public Long getId() {
        return id;
    }

    public String getStationId() {
        return stationId;
    }

    public void setStationId(String stationId) {
        this.stationId = stationId;
    }

    public LinkState getPreviousState() {
        return previousState;
    }

    public void setPreviousState(LinkState previousState) {
        this.previousState = previousState;
    }

    public LinkState getCurrentState() {
        return currentState;
    }

    public void setCurrentState(LinkState currentState) {
        this.currentState = currentState;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public OffsetDateTime getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(OffsetDateTime occurredAt) {
        this.occurredAt = occurredAt;
    }
}


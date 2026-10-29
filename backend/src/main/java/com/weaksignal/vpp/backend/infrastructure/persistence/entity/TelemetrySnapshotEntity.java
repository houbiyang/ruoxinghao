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
 * 最新遥测快照实体。
 */
@Entity
@Table(name = "telemetry_snapshot")
public class TelemetrySnapshotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "station_id", nullable = false, unique = true, length = 64)
    private String stationId;

    @Column(name = "message_id", nullable = false, unique = true, length = 64)
    private String messageId;

    @Column(name = "sequence_number", nullable = false)
    private long sequenceNumber;

    @Column(name = "device_timestamp", nullable = false)
    private OffsetDateTime deviceTimestamp;

    @Column(name = "received_timestamp", nullable = false)
    private OffsetDateTime receivedTimestamp;

    @Column(name = "active_power", nullable = false)
    private double activePower;

    @Column(name = "available_capacity", nullable = false)
    private double availableCapacity;

    @Column(nullable = false)
    private int latency;

    @Column(name = "packet_loss_rate", nullable = false)
    private double packetLossRate;

    @Column(name = "data_age", nullable = false)
    private long dataAge;

    @Enumerated(EnumType.STRING)
    @Column(name = "link_state", nullable = false, length = 8)
    private LinkState linkState;

    @Column(name = "quality_code", nullable = false, length = 32)
    private String qualityCode;

    @Column(nullable = false)
    private boolean online;

    @Column(name = "experiment_run_id", length = 64)
    private String experimentRunId;

    @Column(name = "active_fault_code", length = 64)
    private String activeFaultCode;

    @Column(name = "payload_json", nullable = false, columnDefinition = "TEXT")
    private String payloadJson;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public String getStationId() {
        return stationId;
    }

    public void setStationId(String stationId) {
        this.stationId = stationId;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public long getSequenceNumber() {
        return sequenceNumber;
    }

    public void setSequenceNumber(long sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    public OffsetDateTime getDeviceTimestamp() {
        return deviceTimestamp;
    }

    public void setDeviceTimestamp(OffsetDateTime deviceTimestamp) {
        this.deviceTimestamp = deviceTimestamp;
    }

    public OffsetDateTime getReceivedTimestamp() {
        return receivedTimestamp;
    }

    public void setReceivedTimestamp(OffsetDateTime receivedTimestamp) {
        this.receivedTimestamp = receivedTimestamp;
    }

    public double getActivePower() {
        return activePower;
    }

    public void setActivePower(double activePower) {
        this.activePower = activePower;
    }

    public double getAvailableCapacity() {
        return availableCapacity;
    }

    public void setAvailableCapacity(double availableCapacity) {
        this.availableCapacity = availableCapacity;
    }

    public int getLatency() {
        return latency;
    }

    public void setLatency(int latency) {
        this.latency = latency;
    }

    public double getPacketLossRate() {
        return packetLossRate;
    }

    public void setPacketLossRate(double packetLossRate) {
        this.packetLossRate = packetLossRate;
    }

    public long getDataAge() {
        return dataAge;
    }

    public void setDataAge(long dataAge) {
        this.dataAge = dataAge;
    }

    public LinkState getLinkState() {
        return linkState;
    }

    public void setLinkState(LinkState linkState) {
        this.linkState = linkState;
    }

    public String getQualityCode() {
        return qualityCode;
    }

    public void setQualityCode(String qualityCode) {
        this.qualityCode = qualityCode;
    }

    public boolean isOnline() {
        return online;
    }

    public void setOnline(boolean online) {
        this.online = online;
    }

    public String getExperimentRunId() {
        return experimentRunId;
    }

    public void setExperimentRunId(String experimentRunId) {
        this.experimentRunId = experimentRunId;
    }

    public String getActiveFaultCode() {
        return activeFaultCode;
    }

    public void setActiveFaultCode(String activeFaultCode) {
        this.activeFaultCode = activeFaultCode;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public void setPayloadJson(String payloadJson) {
        this.payloadJson = payloadJson;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}

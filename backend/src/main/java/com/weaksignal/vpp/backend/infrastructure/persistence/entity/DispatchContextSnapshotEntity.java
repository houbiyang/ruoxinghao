package com.weaksignal.vpp.backend.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/**
 * 调度上下文快照实体。
 */
@Entity
@Table(name = "dispatch_context_snapshot")
public class DispatchContextSnapshotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "snapshot_id", nullable = false, unique = true, length = 64)
    private String snapshotId;

    @Column(name = "market_signal_snapshot_id", nullable = false, length = 64)
    private String marketSignalSnapshotId;

    @Column(name = "forecast_snapshot_id", nullable = false, length = 64)
    private String forecastSnapshotId;

    @Column(name = "capability_snapshot_group_id", nullable = false, length = 64)
    private String capabilitySnapshotGroupId;

    @Column(name = "interval_minutes", nullable = false)
    private int intervalMinutes;

    @Column(nullable = false, length = 64)
    private String source;

    @Column(name = "quality_code", nullable = false, length = 32)
    private String qualityCode;

    @Column(nullable = false, length = 64)
    private String version;

    @Column(name = "generated_at", nullable = false)
    private OffsetDateTime generatedAt;

    @Column(name = "valid_from", nullable = false)
    private OffsetDateTime validFrom;

    @Column(name = "valid_until", nullable = false)
    private OffsetDateTime validUntil;

    @Column(name = "received_at", nullable = false)
    private OffsetDateTime receivedAt;

    @Column(name = "payload_json", nullable = false, columnDefinition = "TEXT")
    private String payloadJson;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    public Long getId() {
        return id;
    }

    public String getSnapshotId() {
        return snapshotId;
    }

    public void setSnapshotId(String snapshotId) {
        this.snapshotId = snapshotId;
    }

    public String getMarketSignalSnapshotId() {
        return marketSignalSnapshotId;
    }

    public void setMarketSignalSnapshotId(String marketSignalSnapshotId) {
        this.marketSignalSnapshotId = marketSignalSnapshotId;
    }

    public String getForecastSnapshotId() {
        return forecastSnapshotId;
    }

    public void setForecastSnapshotId(String forecastSnapshotId) {
        this.forecastSnapshotId = forecastSnapshotId;
    }

    public String getCapabilitySnapshotGroupId() {
        return capabilitySnapshotGroupId;
    }

    public void setCapabilitySnapshotGroupId(String capabilitySnapshotGroupId) {
        this.capabilitySnapshotGroupId = capabilitySnapshotGroupId;
    }

    public int getIntervalMinutes() {
        return intervalMinutes;
    }

    public void setIntervalMinutes(int intervalMinutes) {
        this.intervalMinutes = intervalMinutes;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getQualityCode() {
        return qualityCode;
    }

    public void setQualityCode(String qualityCode) {
        this.qualityCode = qualityCode;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public OffsetDateTime getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(OffsetDateTime generatedAt) {
        this.generatedAt = generatedAt;
    }

    public OffsetDateTime getValidFrom() {
        return validFrom;
    }

    public void setValidFrom(OffsetDateTime validFrom) {
        this.validFrom = validFrom;
    }

    public OffsetDateTime getValidUntil() {
        return validUntil;
    }

    public void setValidUntil(OffsetDateTime validUntil) {
        this.validUntil = validUntil;
    }

    public OffsetDateTime getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(OffsetDateTime receivedAt) {
        this.receivedAt = receivedAt;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public void setPayloadJson(String payloadJson) {
        this.payloadJson = payloadJson;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}

package com.weaksignal.vpp.backend.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 市场信号快照实体。
 */
@Entity
@Table(name = "market_signal_snapshot")
public class MarketSignalSnapshotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "snapshot_id", nullable = false, unique = true, length = 64)
    private String snapshotId;

    @Column(name = "market_type", nullable = false, length = 32)
    private String marketType;

    @Column(name = "interval_start", nullable = false)
    private OffsetDateTime intervalStart;

    @Column(name = "interval_end", nullable = false)
    private OffsetDateTime intervalEnd;

    @Column(name = "contract_power", nullable = false)
    private double contractPower;

    @Column(name = "contract_price", nullable = false, precision = 18, scale = 6)
    private BigDecimal contractPrice;

    @Column(name = "day_ahead_price", nullable = false, precision = 18, scale = 6)
    private BigDecimal dayAheadPrice;

    @Column(name = "real_time_price", nullable = false, precision = 18, scale = 6)
    private BigDecimal realTimePrice;

    @Column(name = "grid_import_limit", nullable = false)
    private double gridImportLimit;

    @Column(name = "grid_export_limit", nullable = false)
    private double gridExportLimit;

    @Column(name = "reserve_up_requirement", nullable = false)
    private double reserveUpRequirement;

    @Column(name = "reserve_down_requirement", nullable = false)
    private double reserveDownRequirement;

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

    public String getMarketType() {
        return marketType;
    }

    public void setMarketType(String marketType) {
        this.marketType = marketType;
    }

    public OffsetDateTime getIntervalStart() {
        return intervalStart;
    }

    public void setIntervalStart(OffsetDateTime intervalStart) {
        this.intervalStart = intervalStart;
    }

    public OffsetDateTime getIntervalEnd() {
        return intervalEnd;
    }

    public void setIntervalEnd(OffsetDateTime intervalEnd) {
        this.intervalEnd = intervalEnd;
    }

    public double getContractPower() {
        return contractPower;
    }

    public void setContractPower(double contractPower) {
        this.contractPower = contractPower;
    }

    public BigDecimal getContractPrice() {
        return contractPrice;
    }

    public void setContractPrice(BigDecimal contractPrice) {
        this.contractPrice = contractPrice;
    }

    public BigDecimal getDayAheadPrice() {
        return dayAheadPrice;
    }

    public void setDayAheadPrice(BigDecimal dayAheadPrice) {
        this.dayAheadPrice = dayAheadPrice;
    }

    public BigDecimal getRealTimePrice() {
        return realTimePrice;
    }

    public void setRealTimePrice(BigDecimal realTimePrice) {
        this.realTimePrice = realTimePrice;
    }

    public double getGridImportLimit() {
        return gridImportLimit;
    }

    public void setGridImportLimit(double gridImportLimit) {
        this.gridImportLimit = gridImportLimit;
    }

    public double getGridExportLimit() {
        return gridExportLimit;
    }

    public void setGridExportLimit(double gridExportLimit) {
        this.gridExportLimit = gridExportLimit;
    }

    public double getReserveUpRequirement() {
        return reserveUpRequirement;
    }

    public void setReserveUpRequirement(double reserveUpRequirement) {
        this.reserveUpRequirement = reserveUpRequirement;
    }

    public double getReserveDownRequirement() {
        return reserveDownRequirement;
    }

    public void setReserveDownRequirement(double reserveDownRequirement) {
        this.reserveDownRequirement = reserveDownRequirement;
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

package com.weaksignal.vpp.backend.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/**
 * 市场报价实体。
 */
@Entity
@Table(name = "market_bid")
public class MarketBidEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bid_id", nullable = false, unique = true, length = 64)
    private String bidId;

    @Column(name = "market_type", nullable = false, length = 32)
    private String marketType;

    @Column(name = "aggregate_object", nullable = false, length = 64)
    private String aggregateObject;

    @Column(name = "interval_start", nullable = false)
    private OffsetDateTime intervalStart;

    @Column(name = "interval_end", nullable = false)
    private OffsetDateTime intervalEnd;

    @Column(name = "available_capacity", nullable = false)
    private double availableCapacity;

    @Column(name = "total_quantity", nullable = false)
    private double totalQuantity;

    @Column(name = "input_snapshot_id", nullable = false, length = 64)
    private String inputSnapshotId;

    @Column(name = "bid_version", nullable = false, length = 64)
    private String bidVersion;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "valid_until", nullable = false)
    private OffsetDateTime validUntil;

    @Column(name = "test_data", nullable = false)
    private boolean testData;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    public Long getId() {
        return id;
    }

    public String getBidId() {
        return bidId;
    }

    public void setBidId(String bidId) {
        this.bidId = bidId;
    }

    public String getMarketType() {
        return marketType;
    }

    public void setMarketType(String marketType) {
        this.marketType = marketType;
    }

    public String getAggregateObject() {
        return aggregateObject;
    }

    public void setAggregateObject(String aggregateObject) {
        this.aggregateObject = aggregateObject;
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

    public double getAvailableCapacity() {
        return availableCapacity;
    }

    public void setAvailableCapacity(double availableCapacity) {
        this.availableCapacity = availableCapacity;
    }

    public double getTotalQuantity() {
        return totalQuantity;
    }

    public void setTotalQuantity(double totalQuantity) {
        this.totalQuantity = totalQuantity;
    }

    public String getInputSnapshotId() {
        return inputSnapshotId;
    }

    public void setInputSnapshotId(String inputSnapshotId) {
        this.inputSnapshotId = inputSnapshotId;
    }

    public String getBidVersion() {
        return bidVersion;
    }

    public void setBidVersion(String bidVersion) {
        this.bidVersion = bidVersion;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public OffsetDateTime getValidUntil() {
        return validUntil;
    }

    public void setValidUntil(OffsetDateTime validUntil) {
        this.validUntil = validUntil;
    }

    public boolean isTestData() {
        return testData;
    }

    public void setTestData(boolean testData) {
        this.testData = testData;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}

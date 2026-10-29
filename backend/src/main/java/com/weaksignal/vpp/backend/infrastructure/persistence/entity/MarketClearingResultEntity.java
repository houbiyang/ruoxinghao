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
 * 测试市场出清结果实体。
 */
@Entity
@Table(name = "market_clearing_result")
public class MarketClearingResultEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "clearing_id", nullable = false, unique = true, length = 64)
    private String clearingId;

    @Column(name = "bid_id", nullable = false, length = 64)
    private String bidId;

    @Column(name = "market_type", nullable = false, length = 32)
    private String marketType;

    @Column(name = "interval_start", nullable = false)
    private OffsetDateTime intervalStart;

    @Column(name = "interval_end", nullable = false)
    private OffsetDateTime intervalEnd;

    @Column(name = "cleared_power", nullable = false)
    private double clearedPower;

    @Column(name = "cleared_price", nullable = false, precision = 18, scale = 6)
    private BigDecimal clearedPrice;

    @Column(name = "ancillary_service_power", nullable = false)
    private double ancillaryServicePower;

    @Column(name = "external_trade_id", nullable = false, length = 64)
    private String externalTradeId;

    @Column(name = "test_data", nullable = false)
    private boolean testData;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    public Long getId() {
        return id;
    }

    public String getClearingId() {
        return clearingId;
    }

    public void setClearingId(String clearingId) {
        this.clearingId = clearingId;
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

    public double getClearedPower() {
        return clearedPower;
    }

    public void setClearedPower(double clearedPower) {
        this.clearedPower = clearedPower;
    }

    public BigDecimal getClearedPrice() {
        return clearedPrice;
    }

    public void setClearedPrice(BigDecimal clearedPrice) {
        this.clearedPrice = clearedPrice;
    }

    public double getAncillaryServicePower() {
        return ancillaryServicePower;
    }

    public void setAncillaryServicePower(double ancillaryServicePower) {
        this.ancillaryServicePower = ancillaryServicePower;
    }

    public String getExternalTradeId() {
        return externalTradeId;
    }

    public void setExternalTradeId(String externalTradeId) {
        this.externalTradeId = externalTradeId;
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

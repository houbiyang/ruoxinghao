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
 * 市场结算结果实体。
 */
@Entity
@Table(name = "settlement_result")
public class SettlementResultEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "settlement_id", nullable = false, unique = true, length = 64)
    private String settlementId;

    @Column(name = "plan_id", nullable = false, length = 64)
    private String planId;

    @Column(name = "clearing_id", nullable = false, length = 64)
    private String clearingId;

    @Column(name = "total_energy_revenue", nullable = false, precision = 18, scale = 6)
    private BigDecimal totalEnergyRevenue;

    @Column(name = "grid_import_cost", nullable = false, precision = 18, scale = 6)
    private BigDecimal gridImportCost;

    @Column(name = "grid_export_revenue", nullable = false, precision = 18, scale = 6)
    private BigDecimal gridExportRevenue;

    @Column(name = "deviation_cost", nullable = false, precision = 18, scale = 6)
    private BigDecimal deviationCost;

    @Column(name = "storage_cycle_cost", nullable = false, precision = 18, scale = 6)
    private BigDecimal storageCycleCost;

    @Column(name = "demand_response_compensation", nullable = false, precision = 18, scale = 6)
    private BigDecimal demandResponseCompensation;

    @Column(name = "renewable_curtailment_cost", nullable = false, precision = 18, scale = 6)
    private BigDecimal renewableCurtailmentCost;

    @Column(name = "ancillary_service_revenue", nullable = false, precision = 18, scale = 6)
    private BigDecimal ancillaryServiceRevenue;

    @Column(name = "resource_revenue_allocation_json", nullable = false, columnDefinition = "TEXT")
    private String resourceRevenueAllocationJson;

    @Column(name = "net_revenue", nullable = false, precision = 18, scale = 6)
    private BigDecimal netRevenue;

    @Column(name = "test_data", nullable = false)
    private boolean testData;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    public Long getId() {
        return id;
    }

    public String getSettlementId() {
        return settlementId;
    }

    public void setSettlementId(String settlementId) {
        this.settlementId = settlementId;
    }

    public String getPlanId() {
        return planId;
    }

    public void setPlanId(String planId) {
        this.planId = planId;
    }

    public String getClearingId() {
        return clearingId;
    }

    public void setClearingId(String clearingId) {
        this.clearingId = clearingId;
    }

    public BigDecimal getTotalEnergyRevenue() {
        return totalEnergyRevenue;
    }

    public void setTotalEnergyRevenue(BigDecimal totalEnergyRevenue) {
        this.totalEnergyRevenue = totalEnergyRevenue;
    }

    public BigDecimal getGridImportCost() {
        return gridImportCost;
    }

    public void setGridImportCost(BigDecimal gridImportCost) {
        this.gridImportCost = gridImportCost;
    }

    public BigDecimal getGridExportRevenue() {
        return gridExportRevenue;
    }

    public void setGridExportRevenue(BigDecimal gridExportRevenue) {
        this.gridExportRevenue = gridExportRevenue;
    }

    public BigDecimal getDeviationCost() {
        return deviationCost;
    }

    public void setDeviationCost(BigDecimal deviationCost) {
        this.deviationCost = deviationCost;
    }

    public BigDecimal getStorageCycleCost() {
        return storageCycleCost;
    }

    public void setStorageCycleCost(BigDecimal storageCycleCost) {
        this.storageCycleCost = storageCycleCost;
    }

    public BigDecimal getDemandResponseCompensation() {
        return demandResponseCompensation;
    }

    public void setDemandResponseCompensation(BigDecimal demandResponseCompensation) {
        this.demandResponseCompensation = demandResponseCompensation;
    }

    public BigDecimal getRenewableCurtailmentCost() {
        return renewableCurtailmentCost;
    }

    public void setRenewableCurtailmentCost(BigDecimal renewableCurtailmentCost) {
        this.renewableCurtailmentCost = renewableCurtailmentCost;
    }

    public BigDecimal getAncillaryServiceRevenue() {
        return ancillaryServiceRevenue;
    }

    public void setAncillaryServiceRevenue(BigDecimal ancillaryServiceRevenue) {
        this.ancillaryServiceRevenue = ancillaryServiceRevenue;
    }

    public String getResourceRevenueAllocationJson() {
        return resourceRevenueAllocationJson;
    }

    public void setResourceRevenueAllocationJson(String resourceRevenueAllocationJson) {
        this.resourceRevenueAllocationJson = resourceRevenueAllocationJson;
    }

    public BigDecimal getNetRevenue() {
        return netRevenue;
    }

    public void setNetRevenue(BigDecimal netRevenue) {
        this.netRevenue = netRevenue;
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

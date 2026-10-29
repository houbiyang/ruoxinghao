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
 * 调度计划时间片实体。
 */
@Entity
@Table(name = "dispatch_plan_item")
public class DispatchPlanItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "item_id", nullable = false, unique = true, length = 64)
    private String itemId;

    @Column(name = "plan_id", nullable = false, length = 64)
    private String planId;

    @Column(name = "input_snapshot_id", nullable = false, length = 64)
    private String inputSnapshotId;

    @Column(name = "interval_start", nullable = false)
    private OffsetDateTime intervalStart;

    @Column(name = "interval_end", nullable = false)
    private OffsetDateTime intervalEnd;

    @Column(name = "net_power_target", nullable = false)
    private double netPowerTarget;

    @Column(name = "grid_import_power", nullable = false)
    private double gridImportPower;

    @Column(name = "grid_export_power", nullable = false)
    private double gridExportPower;

    @Column(name = "storage_charge_power", nullable = false)
    private double storageChargePower;

    @Column(name = "storage_discharge_power", nullable = false)
    private double storageDischargePower;

    @Column(name = "flexible_load_adjustment", nullable = false)
    private double flexibleLoadAdjustment;

    @Column(name = "upward_reserve", nullable = false)
    private double upwardReserve;

    @Column(name = "downward_reserve", nullable = false)
    private double downwardReserve;

    @Column(name = "renewable_curtailment", nullable = false)
    private double renewableCurtailment;

    @Column(name = "expected_cost", nullable = false, precision = 18, scale = 6)
    private BigDecimal expectedCost;

    @Column(name = "expected_revenue", nullable = false, precision = 18, scale = 6)
    private BigDecimal expectedRevenue;

    @Column(name = "risk_cost", nullable = false, precision = 18, scale = 6)
    private BigDecimal riskCost;

    @Column(name = "target_powers_json", nullable = false, columnDefinition = "TEXT")
    private String targetPowersJson;

    @Column(name = "reason_code", nullable = false, length = 64)
    private String reasonCode;

    @Column(name = "item_order", nullable = false)
    private int itemOrder;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    public Long getId() {
        return id;
    }

    public String getItemId() {
        return itemId;
    }

    public void setItemId(String itemId) {
        this.itemId = itemId;
    }

    public String getPlanId() {
        return planId;
    }

    public void setPlanId(String planId) {
        this.planId = planId;
    }

    public String getInputSnapshotId() {
        return inputSnapshotId;
    }

    public void setInputSnapshotId(String inputSnapshotId) {
        this.inputSnapshotId = inputSnapshotId;
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

    public double getNetPowerTarget() {
        return netPowerTarget;
    }

    public void setNetPowerTarget(double netPowerTarget) {
        this.netPowerTarget = netPowerTarget;
    }

    public double getGridImportPower() {
        return gridImportPower;
    }

    public void setGridImportPower(double gridImportPower) {
        this.gridImportPower = gridImportPower;
    }

    public double getGridExportPower() {
        return gridExportPower;
    }

    public void setGridExportPower(double gridExportPower) {
        this.gridExportPower = gridExportPower;
    }

    public double getStorageChargePower() {
        return storageChargePower;
    }

    public void setStorageChargePower(double storageChargePower) {
        this.storageChargePower = storageChargePower;
    }

    public double getStorageDischargePower() {
        return storageDischargePower;
    }

    public void setStorageDischargePower(double storageDischargePower) {
        this.storageDischargePower = storageDischargePower;
    }

    public double getFlexibleLoadAdjustment() {
        return flexibleLoadAdjustment;
    }

    public void setFlexibleLoadAdjustment(double flexibleLoadAdjustment) {
        this.flexibleLoadAdjustment = flexibleLoadAdjustment;
    }

    public double getUpwardReserve() {
        return upwardReserve;
    }

    public void setUpwardReserve(double upwardReserve) {
        this.upwardReserve = upwardReserve;
    }

    public double getDownwardReserve() {
        return downwardReserve;
    }

    public void setDownwardReserve(double downwardReserve) {
        this.downwardReserve = downwardReserve;
    }

    public double getRenewableCurtailment() {
        return renewableCurtailment;
    }

    public void setRenewableCurtailment(double renewableCurtailment) {
        this.renewableCurtailment = renewableCurtailment;
    }

    public BigDecimal getExpectedCost() {
        return expectedCost;
    }

    public void setExpectedCost(BigDecimal expectedCost) {
        this.expectedCost = expectedCost;
    }

    public BigDecimal getExpectedRevenue() {
        return expectedRevenue;
    }

    public void setExpectedRevenue(BigDecimal expectedRevenue) {
        this.expectedRevenue = expectedRevenue;
    }

    public BigDecimal getRiskCost() {
        return riskCost;
    }

    public void setRiskCost(BigDecimal riskCost) {
        this.riskCost = riskCost;
    }

    /**
     * 获取经安全校验的逐资源目标功率 JSON。
     *
     * @return 场站标识到目标功率的 JSON 映射
     */
    public String getTargetPowersJson() {
        return targetPowersJson;
    }

    /**
     * 设置经安全校验的逐资源目标功率 JSON。
     *
     * @param targetPowersJson 场站标识到目标功率的 JSON 映射
     */
    public void setTargetPowersJson(String targetPowersJson) {
        this.targetPowersJson = targetPowersJson;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public void setReasonCode(String reasonCode) {
        this.reasonCode = reasonCode;
    }

    public int getItemOrder() {
        return itemOrder;
    }

    public void setItemOrder(int itemOrder) {
        this.itemOrder = itemOrder;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}

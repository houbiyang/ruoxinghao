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
 * 资源能力快照实体。
 */
@Entity
@Table(name = "resource_capability_snapshot")
public class ResourceCapabilitySnapshotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "capability_id", nullable = false, unique = true, length = 64)
    private String capabilityId;

    @Column(name = "snapshot_group_id", nullable = false, length = 64)
    private String snapshotGroupId;

    @Column(name = "station_id", nullable = false, length = 64)
    private String stationId;

    @Column(name = "resource_type", nullable = false, length = 32)
    private String resourceType;

    @Column(name = "current_power", nullable = false)
    private double currentPower;

    @Column(name = "available_capacity", nullable = false)
    private double availableCapacity;

    @Column(name = "minimum_power", nullable = false)
    private double minimumPower;

    @Column(name = "maximum_power", nullable = false)
    private double maximumPower;

    @Column(name = "maximum_ramp_up", nullable = false)
    private double maximumRampUp;

    @Column(name = "maximum_ramp_down", nullable = false)
    private double maximumRampDown;

    @Column(name = "available_energy_kwh", nullable = false)
    private double availableEnergyKwh;

    @Column(name = "soc")
    private Double soc;

    @Column(name = "minimum_soc")
    private Double minimumSoc;

    @Column(name = "maximum_soc")
    private Double maximumSoc;

    @Column(name = "energy_capacity_kwh")
    private Double energyCapacityKwh;

    @Column(name = "maximum_charge_power_kw")
    private Double maximumChargePowerKw;

    @Column(name = "maximum_discharge_power_kw")
    private Double maximumDischargePowerKw;

    @Column(name = "charge_efficiency")
    private Double chargeEfficiency;

    @Column(name = "discharge_efficiency")
    private Double dischargeEfficiency;

    @Column(name = "cycle_cost", precision = 18, scale = 6)
    private BigDecimal cycleCost;

    @Column(name = "state_of_health")
    private Double stateOfHealth;

    @Column(name = "reserved_up_power", nullable = false)
    private double reservedUpPower;

    @Column(name = "reserved_down_power", nullable = false)
    private double reservedDownPower;

    @Column(name = "link_state", nullable = false, length = 8)
    private String linkState;

    @Column(name = "data_age", nullable = false)
    private long dataAge;

    @Column(name = "capability_confidence", nullable = false)
    private double capabilityConfidence;

    @Column(name = "data_trusted", nullable = false)
    private boolean dataTrusted;

    @Column(name = "unavailable_reason", length = 256)
    private String unavailableReason;

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

    public String getCapabilityId() {
        return capabilityId;
    }

    public void setCapabilityId(String capabilityId) {
        this.capabilityId = capabilityId;
    }

    public String getSnapshotGroupId() {
        return snapshotGroupId;
    }

    public void setSnapshotGroupId(String snapshotGroupId) {
        this.snapshotGroupId = snapshotGroupId;
    }

    public String getStationId() {
        return stationId;
    }

    public void setStationId(String stationId) {
        this.stationId = stationId;
    }

    public String getResourceType() {
        return resourceType;
    }

    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }

    public double getCurrentPower() {
        return currentPower;
    }

    public void setCurrentPower(double currentPower) {
        this.currentPower = currentPower;
    }

    public double getAvailableCapacity() {
        return availableCapacity;
    }

    public void setAvailableCapacity(double availableCapacity) {
        this.availableCapacity = availableCapacity;
    }

    public double getMinimumPower() {
        return minimumPower;
    }

    public void setMinimumPower(double minimumPower) {
        this.minimumPower = minimumPower;
    }

    public double getMaximumPower() {
        return maximumPower;
    }

    public void setMaximumPower(double maximumPower) {
        this.maximumPower = maximumPower;
    }

    public double getMaximumRampUp() {
        return maximumRampUp;
    }

    public void setMaximumRampUp(double maximumRampUp) {
        this.maximumRampUp = maximumRampUp;
    }

    public double getMaximumRampDown() {
        return maximumRampDown;
    }

    public void setMaximumRampDown(double maximumRampDown) {
        this.maximumRampDown = maximumRampDown;
    }

    public double getAvailableEnergyKwh() {
        return availableEnergyKwh;
    }

    public void setAvailableEnergyKwh(double availableEnergyKwh) {
        this.availableEnergyKwh = availableEnergyKwh;
    }

    public Double getSoc() {
        return soc;
    }

    public void setSoc(Double soc) {
        this.soc = soc;
    }

    public Double getMinimumSoc() {
        return minimumSoc;
    }

    public void setMinimumSoc(Double minimumSoc) {
        this.minimumSoc = minimumSoc;
    }

    public Double getMaximumSoc() {
        return maximumSoc;
    }

    public void setMaximumSoc(Double maximumSoc) {
        this.maximumSoc = maximumSoc;
    }

    public Double getEnergyCapacityKwh() {
        return energyCapacityKwh;
    }

    public void setEnergyCapacityKwh(Double energyCapacityKwh) {
        this.energyCapacityKwh = energyCapacityKwh;
    }

    public Double getMaximumChargePowerKw() {
        return maximumChargePowerKw;
    }

    public void setMaximumChargePowerKw(Double maximumChargePowerKw) {
        this.maximumChargePowerKw = maximumChargePowerKw;
    }

    public Double getMaximumDischargePowerKw() {
        return maximumDischargePowerKw;
    }

    public void setMaximumDischargePowerKw(Double maximumDischargePowerKw) {
        this.maximumDischargePowerKw = maximumDischargePowerKw;
    }

    public Double getChargeEfficiency() {
        return chargeEfficiency;
    }

    public void setChargeEfficiency(Double chargeEfficiency) {
        this.chargeEfficiency = chargeEfficiency;
    }

    public Double getDischargeEfficiency() {
        return dischargeEfficiency;
    }

    public void setDischargeEfficiency(Double dischargeEfficiency) {
        this.dischargeEfficiency = dischargeEfficiency;
    }

    public BigDecimal getCycleCost() {
        return cycleCost;
    }

    public void setCycleCost(BigDecimal cycleCost) {
        this.cycleCost = cycleCost;
    }

    public Double getStateOfHealth() {
        return stateOfHealth;
    }

    public void setStateOfHealth(Double stateOfHealth) {
        this.stateOfHealth = stateOfHealth;
    }

    public double getReservedUpPower() {
        return reservedUpPower;
    }

    public void setReservedUpPower(double reservedUpPower) {
        this.reservedUpPower = reservedUpPower;
    }

    public double getReservedDownPower() {
        return reservedDownPower;
    }

    public void setReservedDownPower(double reservedDownPower) {
        this.reservedDownPower = reservedDownPower;
    }

    public String getLinkState() {
        return linkState;
    }

    public void setLinkState(String linkState) {
        this.linkState = linkState;
    }

    public long getDataAge() {
        return dataAge;
    }

    public void setDataAge(long dataAge) {
        this.dataAge = dataAge;
    }

    public double getCapabilityConfidence() {
        return capabilityConfidence;
    }

    public void setCapabilityConfidence(double capabilityConfidence) {
        this.capabilityConfidence = capabilityConfidence;
    }

    public boolean isDataTrusted() {
        return dataTrusted;
    }

    public void setDataTrusted(boolean dataTrusted) {
        this.dataTrusted = dataTrusted;
    }

    public String getUnavailableReason() {
        return unavailableReason;
    }

    public void setUnavailableReason(String unavailableReason) {
        this.unavailableReason = unavailableReason;
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

package com.weaksignal.vpp.backend.capability;

import java.math.BigDecimal;

/**
 * 储能约束快照。
 *
 * @param soc 当前 SOC
 * @param minimumSoc 最低 SOC
 * @param maximumSoc 最高 SOC
 * @param energyCapacityKWh 额定能量容量，单位 kWh
 * @param maximumChargePowerKW 最大充电功率，单位 kW
 * @param maximumDischargePowerKW 最大放电功率，单位 kW
 * @param chargeEfficiency 充电效率
 * @param dischargeEfficiency 放电效率
 * @param cycleCost 单位循环成本
 * @param stateOfHealth 健康状态
 * @param reservedUpPower 已预留向上备用，单位 kW
 * @param reservedDownPower 已预留向下备用，单位 kW
 */
public record StorageConstraint(
        double soc,
        double minimumSoc,
        double maximumSoc,
        double energyCapacityKWh,
        double maximumChargePowerKW,
        double maximumDischargePowerKW,
        double chargeEfficiency,
        double dischargeEfficiency,
        BigDecimal cycleCost,
        double stateOfHealth,
        double reservedUpPower,
        double reservedDownPower
) {
}

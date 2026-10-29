package com.weaksignal.vpp.backend.control.distributed;

import com.weaksignal.vpp.backend.domain.ResourceType;

/**
 * 单个可调资源的本地增量成本与安全约束模型。
 *
 * @param nodeId 节点标识
 * @param resourceType 资源类型
 * @param quadraticCoefficient 二次成本系数 a
 * @param linearCoefficient 一次成本系数 b
 * @param constantCoefficient 常数成本系数 c
 * @param minimumPowerKw 最小功率，单位 kW
 * @param maximumPowerKw 最大功率，单位 kW
 * @param maximumRampUpKw 最大上升爬坡，单位 kW/迭代
 * @param maximumRampDownKw 最大下降爬坡，单位 kW/迭代
 * @param minimumSoc 最低 SOC，非储能资源填 0
 * @param maximumSoc 最高 SOC，非储能资源填 100
 * @param energyCapacityKwh 储能额定能量，非储能资源填 0
 * @param chargeEfficiency 储能充电效率
 * @param dischargeEfficiency 储能放电效率
 */
public record IncrementalCostModel(
        String nodeId,
        ResourceType resourceType,
        double quadraticCoefficient,
        double linearCoefficient,
        double constantCoefficient,
        double minimumPowerKw,
        double maximumPowerKw,
        double maximumRampUpKw,
        double maximumRampDownKw,
        double minimumSoc,
        double maximumSoc,
        double energyCapacityKwh,
        double chargeEfficiency,
        double dischargeEfficiency
) {
    /**
     * 校验本地成本与约束参数。
     */
    public IncrementalCostModel {
        if (nodeId == null || nodeId.isBlank() || quadraticCoefficient <= 0
                || minimumPowerKw > maximumPowerKw || maximumRampUpKw < 0 || maximumRampDownKw < 0) {
            throw new IllegalArgumentException("资源本地成本或安全约束参数不合法");
        }
        if (resourceType == ResourceType.STORAGE
                && (energyCapacityKwh <= 0 || minimumSoc > maximumSoc || chargeEfficiency <= 0 || dischargeEfficiency <= 0)) {
            throw new IllegalArgumentException("储能 SOC 或效率参数不合法");
        }
    }

    /**
     * 根据增量成本计算未投影目标功率。
     *
     * @param lambda 增量成本
     * @return 未约束目标功率，单位 kW
     */
    public double unconstrainedPower(double lambda) {
        return (lambda - linearCoefficient) / (2.0 * quadraticCoefficient);
    }
}

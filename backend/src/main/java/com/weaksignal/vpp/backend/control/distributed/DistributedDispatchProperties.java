package com.weaksignal.vpp.backend.control.distributed;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 分布式协同功率控制算法配置。
 *
 * @param configurationVersion 配置版本
 * @param normalStepSize S0 增量成本步长
 * @param degradedStepSize S1 增量成本步长
 * @param severeStepSize S2 增量成本步长
 * @param recoveryStepSize S4 增量成本步长
 * @param normalMessagePeriodMillis S0 消息周期
 * @param degradedMessagePeriodMillis S1 消息周期
 * @param severeMessagePeriodMillis S2 消息周期
 * @param recoveryMessagePeriodMillis S4 消息周期
 * @param normalMessageValidityMillis S0 消息有效期
 * @param degradedMessageValidityMillis S1 消息有效期
 * @param severeMessageValidityMillis S2 消息有效期
 * @param recoveryMessageValidityMillis S4 消息有效期
 * @param latencyWeight 时延质量权重
 * @param packetLossWeight 丢包质量权重
 * @param dataAgeWeight 数据时效质量权重
 * @param balanceToleranceKw 功率平衡误差阈值，单位 kW
 * @param consensusTolerance 增量成本一致性误差阈值
 * @param requiredConsecutiveIterations 连续收敛轮数
 * @param recoveryRampFactor S4 爬坡约束收紧系数
 */
@Validated
@ConfigurationProperties(prefix = "app.distributed-dispatch")
public record DistributedDispatchProperties(
        String configurationVersion,
        @DecimalMin("0.000001") double normalStepSize,
        @DecimalMin("0.000001") double degradedStepSize,
        @DecimalMin("0.000001") double severeStepSize,
        @DecimalMin("0.000001") double recoveryStepSize,
        @Min(1) long normalMessagePeriodMillis,
        @Min(1) long degradedMessagePeriodMillis,
        @Min(1) long severeMessagePeriodMillis,
        @Min(1) long recoveryMessagePeriodMillis,
        @Min(1) long normalMessageValidityMillis,
        @Min(1) long degradedMessageValidityMillis,
        @Min(1) long severeMessageValidityMillis,
        @Min(1) long recoveryMessageValidityMillis,
        @DecimalMin("0.0") @DecimalMax("1.0") double latencyWeight,
        @DecimalMin("0.0") @DecimalMax("1.0") double packetLossWeight,
        @DecimalMin("0.0") @DecimalMax("1.0") double dataAgeWeight,
        @DecimalMin("0.0") double balanceToleranceKw,
        @DecimalMin("0.0") double consensusTolerance,
        @Min(1) int requiredConsecutiveIterations,
        @DecimalMin("0.0") @DecimalMax("1.0") double recoveryRampFactor
) {
}

package com.weaksignal.vpp.backend.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 市场调度 MPC 收敛、安全门与滚动重调度参数。
 *
 * @param plannedBalanceToleranceKw 计划供需平衡容差，单位千瓦
 * @param actualBalanceToleranceKw 实际供需平衡容差，单位千瓦
 * @param defaultResourceTrackingToleranceKw 默认逐资源跟踪容差，单位千瓦
 * @param requiredSatisfiedSamples 连续达标采样次数
 * @param telemetryMaximumAge 遥测最大时效
 * @param executionStabilizationWait 执行稳定等待时间
 * @param roundConvergenceTimeout 单轮收敛超时
 * @param minimumReoptimizationInterval 最小重调度间隔
 * @param maximumRollingRounds 最大滚动轮次
 * @param overallClosedLoopTimeout 整体闭环超时
 * @param optimizationComputationTimeout 优化计算超时
 * @param planMinimumRemainingValidity 计划最小剩余有效期
 */
@Validated
@ConfigurationProperties(prefix = "app.planning.mpc")
public record MpcConvergenceProperties(
        @DecimalMin("0.0") double plannedBalanceToleranceKw,
        @DecimalMin("0.0") double actualBalanceToleranceKw,
        @DecimalMin("0.0") double defaultResourceTrackingToleranceKw,
        @Min(1) int requiredSatisfiedSamples,
        Duration telemetryMaximumAge,
        Duration executionStabilizationWait,
        Duration roundConvergenceTimeout,
        Duration minimumReoptimizationInterval,
        @Min(1) int maximumRollingRounds,
        Duration overallClosedLoopTimeout,
        Duration optimizationComputationTimeout,
        Duration planMinimumRemainingValidity
) {
    /** 验证重调度间隔不超过整体闭环超时。 */
    @AssertTrue(message = "最小重调度间隔不能大于整体闭环超时")
    public boolean isReoptimizationIntervalValid() { return !minimumReoptimizationInterval.minus(overallClosedLoopTimeout).isPositive(); }

    /** 验证遥测和计划有效期满足完整闭环使用。 */
    @AssertTrue(message = "遥测时效和计划最小剩余有效期必须为正且覆盖执行稳定等待时间")
    public boolean isClosedLoopTimingValid() {
        return !telemetryMaximumAge.isNegative() && !telemetryMaximumAge.isZero()
                && planMinimumRemainingValidity.compareTo(executionStabilizationWait) >= 0
                && !roundConvergenceTimeout.isNegative() && !optimizationComputationTimeout.isNegative();
    }
}

package com.weaksignal.vpp.backend.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 状态判定阈值配置。
 *
 * @param stableLatencyThreshold S0 时延阈值
 * @param stablePacketLossThreshold S0 丢包率阈值
 * @param stableDataAgeThreshold S0 数据时效阈值
 * @param degradedLatencyThreshold S1 时延阈值
 * @param degradedPacketLossThreshold S1 丢包率阈值
 * @param degradedDataAgeThreshold S1 数据时效阈值
 * @param severeLatencyThreshold S2 时延阈值
 * @param severePacketLossThreshold S2 丢包率阈值
 * @param severeDataAgeThreshold S2 数据时效阈值
 * @param heartbeatTimeoutSeconds 心跳超时阈值
 * @param minimumStateHoldSeconds 最小状态保持时间
 * @param recoveringHoldSeconds 恢复同步保持时间
 */
@Validated
@ConfigurationProperties(prefix = "app.state-rule")
public record StateRuleProperties(
        @Min(1) int stableLatencyThreshold,
        double stablePacketLossThreshold,
        @Min(1) int stableDataAgeThreshold,
        @Min(1) int degradedLatencyThreshold,
        double degradedPacketLossThreshold,
        @Min(1) int degradedDataAgeThreshold,
        @Min(1) int severeLatencyThreshold,
        double severePacketLossThreshold,
        @Min(1) int severeDataAgeThreshold,
        @Min(1) int heartbeatTimeoutSeconds,
        @Min(1) int minimumStateHoldSeconds,
        @Min(1) int recoveringHoldSeconds
) {
}


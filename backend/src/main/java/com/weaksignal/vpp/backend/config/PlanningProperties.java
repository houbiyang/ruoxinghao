package com.weaksignal.vpp.backend.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 市场调度阶段基础参数配置。
 *
 * @param intervalMinutes 计划时间粒度，单位分钟
 * @param maximumDataAgeSeconds 参与能力池的最大数据时效，单位秒
 */
@Validated
@ConfigurationProperties(prefix = "app.planning")
public record PlanningProperties(
        @Min(1) int intervalMinutes,
        @Min(1) int maximumDataAgeSeconds
) {
}

package com.weaksignal.vpp.backend.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Redis 缓存配置属性。
 *
 * @param latestStateTtlSeconds 最新状态过期时间
 * @param heartbeatTtlSeconds 心跳过期时间
 */
@Validated
@ConfigurationProperties(prefix = "app.redis")
public record RedisCacheProperties(
        @Min(10) long latestStateTtlSeconds,
        @Min(5) long heartbeatTtlSeconds
) {
}


package com.weaksignal.vpp.backend.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 跨域配置属性。
 *
 * @param allowedOrigin 允许访问的前端来源
 */
@Validated
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(
        @NotBlank String allowedOrigin
) {
}


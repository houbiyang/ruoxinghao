package com.weaksignal.vpp.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 配置。
 */
@Configuration
public class WebConfiguration implements WebMvcConfigurer {

    private final CorsProperties corsProperties;

    /**
     * 注入跨域配置。
     *
     * @param corsProperties 跨域属性
     */
    public WebConfiguration(CorsProperties corsProperties) {
        this.corsProperties = corsProperties;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/v1/**")
                .allowedOrigins(corsProperties.allowedOrigin())
                .allowedMethods("GET", "POST", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }
}

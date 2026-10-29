package com.weaksignal.vpp.backend.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * MQTT 配置属性。
 *
 * @param host MQTT 主机
 * @param port MQTT 端口
 * @param username MQTT 用户名
 * @param password MQTT 密码
 * @param clientId 客户端标识
 * @param reconnectDelayMillis 重连退避时间
 */
@Validated
@ConfigurationProperties(prefix = "app.mqtt")
public record MqttProperties(
        @NotBlank String host,
        @Min(1) int port,
        String username,
        String password,
        @NotBlank String clientId,
        @Min(1000) long reconnectDelayMillis
) {
}


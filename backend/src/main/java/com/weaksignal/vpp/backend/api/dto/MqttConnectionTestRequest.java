package com.weaksignal.vpp.backend.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * MQTT 连接测试请求。
 *
 * @param host Broker 主机
 * @param port Broker 端口
 * @param username 用户名
 * @param password 测试密码，留空表示使用已保存密码
 */
public record MqttConnectionTestRequest(
        @NotBlank @Size(max = 255) String host,
        @Min(1) @Max(65535) int port,
        @Size(max = 255) String username,
        @Size(max = 4096) String password
) {
}

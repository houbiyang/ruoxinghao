package com.weaksignal.vpp.backend.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * AI Provider 连接测试请求。
 *
 * @param baseUrl API 基础地址
 * @param apiKey 测试 API Key，留空表示使用已保存密钥
 * @param model 模型名称
 * @param timeoutSeconds 请求超时秒数
 */
public record AiConnectionTestRequest(
        @NotBlank @Size(max = 2048) String baseUrl,
        @Size(max = 4096) String apiKey,
        @NotBlank @Size(max = 255) String model,
        @Min(1) @Max(300) int timeoutSeconds
) {
}

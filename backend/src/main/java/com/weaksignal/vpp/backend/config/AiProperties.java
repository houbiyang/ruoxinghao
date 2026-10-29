package com.weaksignal.vpp.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 多智能体模型配置。
 *
 * @param enabled 是否启用 AI 分析
 * @param maximumContextCharacters 发送给模型的最大上下文字符数
 * @param deepseek DeepSeek OpenAI 兼容配置
 */
@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(
        boolean enabled,
        int maximumContextCharacters,
        ProviderProperties deepseek
) {

    /**
     * OpenAI 兼容 Provider 配置。
     *
     * @param enabled 是否启用
     * @param baseUrl Chat Completions 地址
     * @param apiKey API 密钥
     * @param model 模型名称
     * @param timeoutSeconds 请求超时秒数
     */
    public record ProviderProperties(
            boolean enabled,
            String baseUrl,
            String apiKey,
            String model,
            int timeoutSeconds
    ) {
    }
}

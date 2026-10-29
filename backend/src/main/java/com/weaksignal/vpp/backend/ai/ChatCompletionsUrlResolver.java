package com.weaksignal.vpp.backend.ai;

import java.net.URI;

/**
 * OpenAI 兼容 Chat Completions 地址解析器。
 */
public final class ChatCompletionsUrlResolver {

    private static final String CHAT_COMPLETIONS_PATH = "/chat/completions";

    private ChatCompletionsUrlResolver() {
    }

    /**
     * 将 Provider 根地址或完整 Chat Completions 地址解析为唯一请求地址。
     *
     * @param baseUrl Provider 配置地址
     * @return 完整 Chat Completions 请求地址
     * @throws IllegalArgumentException 地址为空或不是合法的 HTTP(S) 地址时抛出
     */
    public static String resolve(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("模型服务地址不能为空");
        }
        String normalizedUrl = removeTrailingSlashes(baseUrl.trim());
        URI uri = URI.create(normalizedUrl);
        if (uri.getScheme() == null || uri.getHost() == null
                || !("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))) {
            throw new IllegalArgumentException("模型服务地址必须是合法的 HTTP(S) 地址");
        }
        return normalizedUrl.endsWith(CHAT_COMPLETIONS_PATH)
                ? normalizedUrl
                : normalizedUrl + CHAT_COMPLETIONS_PATH;
    }

    /**
     * 判断 Provider 地址是否可解析为 Chat Completions 请求地址。
     *
     * @param baseUrl Provider 配置地址
     * @return 地址是否有效
     */
    public static boolean isValid(String baseUrl) {
        try {
            resolve(baseUrl);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static String removeTrailingSlashes(String value) {
        String normalizedValue = value;
        while (normalizedValue.endsWith("/")) {
            normalizedValue = normalizedValue.substring(0, normalizedValue.length() - 1);
        }
        return normalizedValue;
    }
}

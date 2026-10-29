package com.weaksignal.vpp.backend.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Chat Completions 地址解析器测试。
 */
class ChatCompletionsUrlResolverTest {

    @Test
    void shouldAppendPathForProviderRootUrl() {
        assertEquals(
                "https://api.deepseek.com/chat/completions",
                ChatCompletionsUrlResolver.resolve("https://api.deepseek.com")
        );
    }

    @Test
    void shouldAppendPathForProviderRootUrlWithTrailingSlash() {
        assertEquals(
                "https://api.deepseek.com/chat/completions",
                ChatCompletionsUrlResolver.resolve("https://api.deepseek.com/")
        );
    }

    @Test
    void shouldKeepCompleteChatCompletionsUrl() {
        assertEquals(
                "https://api.deepseek.com/chat/completions",
                ChatCompletionsUrlResolver.resolve("https://api.deepseek.com/chat/completions")
        );
    }

    @Test
    void shouldRejectInvalidUrl() {
        assertFalse(ChatCompletionsUrlResolver.isValid("not-a-url"));
        assertTrue(ChatCompletionsUrlResolver.isValid("https://api.deepseek.com"));
    }
}

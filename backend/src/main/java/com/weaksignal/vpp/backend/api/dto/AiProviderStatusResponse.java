package com.weaksignal.vpp.backend.api.dto;

import java.time.OffsetDateTime;

/**
 * AI Provider 脱敏状态。
 *
 * @param providerCode Provider 编码
 * @param enabled 是否可调用
 * @param available 是否可用
 * @param lastErrorAt 最近错误时间
 */
public record AiProviderStatusResponse(
        String providerCode,
        boolean enabled,
        boolean available,
        OffsetDateTime lastErrorAt
) {
}

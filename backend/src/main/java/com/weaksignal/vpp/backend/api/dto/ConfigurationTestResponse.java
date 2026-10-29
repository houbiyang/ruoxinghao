package com.weaksignal.vpp.backend.api.dto;

import java.time.OffsetDateTime;

/**
 * 配置连通性测试响应。
 *
 * @param success 是否测试成功
 * @param target 测试目标
 * @param message 中文测试结果
 * @param statusCode 上游 HTTP 状态码，非 HTTP 测试时为空
 * @param checkedAt 测试时间
 */
public record ConfigurationTestResponse(
        boolean success,
        String target,
        String message,
        Integer statusCode,
        OffsetDateTime checkedAt
) {
}

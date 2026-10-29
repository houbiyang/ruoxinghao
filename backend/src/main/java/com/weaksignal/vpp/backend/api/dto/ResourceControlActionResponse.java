package com.weaksignal.vpp.backend.api.dto;

/**
 * 控制动作响应。
 *
 * @param actionId 动作标识
 * @param actionName 动作名称
 * @param status 动作状态
 * @param description 动作说明
 */
public record ResourceControlActionResponse(
        String actionId,
        String actionName,
        String status,
        String description
) {
}


package com.weaksignal.vpp.backend.api.dto;

/**
 * 事件响应。
 *
 * @param eventId 事件标识
 * @param title 事件标题
 * @param description 事件描述
 * @param level 事件级别
 * @param timestamp 事件时间
 * @param relatedStationId 关联场站
 */
public record EventResponse(
        String eventId,
        String title,
        String description,
        String level,
        String timestamp,
        String relatedStationId
) {
}


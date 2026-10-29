package com.weaksignal.vpp.backend.api.dto;

/**
 * 场景坐标响应。
 *
 * @param x 水平坐标
 * @param y 垂直坐标
 */
public record ScenePositionResponse(
        double x,
        double y
) {
}


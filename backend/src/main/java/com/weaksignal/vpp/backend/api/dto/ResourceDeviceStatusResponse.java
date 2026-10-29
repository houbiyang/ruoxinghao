package com.weaksignal.vpp.backend.api.dto;

/**
 * 设备状态响应。
 *
 * @param deviceId 设备标识
 * @param deviceName 设备名称
 * @param status 设备状态
 * @param currentValue 当前值
 * @param description 状态描述
 */
public record ResourceDeviceStatusResponse(
        String deviceId,
        String deviceName,
        String status,
        String currentValue,
        String description
) {
}


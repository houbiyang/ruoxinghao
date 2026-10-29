package com.weaksignal.vpp.backend.api;

import java.time.OffsetDateTime;

/**
 * 统一接口响应。
 *
 * @param code 响应代码
 * @param message 响应说明
 * @param data 响应数据
 * @param timestamp 响应时间
 * @param <T> 数据类型
 */
public record ApiResponse<T>(
        String code,
        String message,
        T data,
        OffsetDateTime timestamp
) {

    /**
     * 创建成功响应。
     *
     * @param data 数据
     * @param timestamp 时间
     * @param <T> 数据类型
     * @return 统一响应对象
     */
    public static <T> ApiResponse<T> success(T data, OffsetDateTime timestamp) {
        return new ApiResponse<>("SUCCESS", "操作成功", data, timestamp);
    }

    /**
     * 创建失败响应。
     *
     * @param code 错误码
     * @param message 错误说明
     * @param data 错误数据
     * @param timestamp 时间
     * @param <T> 数据类型
     * @return 统一响应对象
     */
    public static <T> ApiResponse<T> failure(String code, String message, T data, OffsetDateTime timestamp) {
        return new ApiResponse<>(code, message, data, timestamp);
    }
}


package com.weaksignal.vpp.backend.api;

import org.springframework.http.HttpStatus;

/**
 * 统一业务异常。
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final Object data;

    /**
     * 创建业务异常。
     *
     * @param status HTTP 状态
     * @param code 错误码
     * @param message 错误说明
     * @param data 错误数据
     */
    public ApiException(HttpStatus status, String code, String message, Object data) {
        super(message);
        this.status = status;
        this.code = code;
        this.data = data;
    }

    /**
     * 获取 HTTP 状态。
     *
     * @return HTTP 状态
     */
    public HttpStatus getStatus() {
        return status;
    }

    /**
     * 获取错误码。
     *
     * @return 错误码
     */
    public String getCode() {
        return code;
    }

    /**
     * 获取错误数据。
     *
     * @return 错误数据
     */
    public Object getData() {
        return data;
    }
}


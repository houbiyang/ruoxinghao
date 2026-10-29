package com.weaksignal.vpp.backend.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 控制模式枚举。
 */
public enum ControlMode {
    CENTRAL_OPTIMIZATION("central-optimization"),
    EDGE_COORDINATION("edge-coordination"),
    LOCAL_AUTONOMY("local-autonomy");

    private final String value;

    /**
     * 创建控制模式枚举。
     *
     * @param value 前端兼容值
     */
    ControlMode(String value) {
        this.value = value;
    }

    /**
     * 获取前端兼容值。
     *
     * @return 字符串值
     */
    @JsonValue
    public String getValue() {
        return value;
    }

    /**
     * 按前端字符串解析控制模式。
     *
     * @param value 字符串值
     * @return 控制模式
     */
    @JsonCreator
    public static ControlMode fromValue(String value) {
        for (ControlMode mode : values()) {
            if (mode.value.equals(value)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("不支持的控制模式: " + value);
    }
}

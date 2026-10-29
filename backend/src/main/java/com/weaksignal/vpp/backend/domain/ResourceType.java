package com.weaksignal.vpp.backend.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 资源类型枚举。
 */
public enum ResourceType {
    PHOTOVOLTAIC("photovoltaic"),
    WIND("wind"),
    THERMAL("thermal"),
    GRID("grid"),
    STORAGE("storage"),
    LOAD("load"),
    CHARGING("charging");

    private final String value;

    /**
     * 创建资源类型枚举。
     *
     * @param value 前端兼容值
     */
    ResourceType(String value) {
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
     * 由字符串解析资源类型。
     *
     * @param value 字符串值
     * @return 资源类型
     */
    @JsonCreator
    public static ResourceType fromValue(String value) {
        for (ResourceType resourceType : values()) {
            if (resourceType.value.equals(value)) {
                return resourceType;
            }
        }
        throw new IllegalArgumentException("不支持的资源类型: " + value);
    }
}

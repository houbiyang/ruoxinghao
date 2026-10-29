package com.weaksignal.vpp.backend.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 演示场景枚举。
 */
public enum DemoScenario {
    STABLE("stable", ControlMode.CENTRAL_OPTIMIZATION),
    DEGRADED("degraded", ControlMode.EDGE_COORDINATION),
    LOST("lost", ControlMode.LOCAL_AUTONOMY),
    RECOVERING("recovering", ControlMode.EDGE_COORDINATION);

    private final String value;
    private final ControlMode controlMode;

    /**
     * 创建演示场景枚举。
     *
     * @param value 前端兼容值
     * @param controlMode 场景控制模式
     */
    DemoScenario(String value, ControlMode controlMode) {
        this.value = value;
        this.controlMode = controlMode;
    }

    /**
     * 获取前端兼容值。
     *
     * @return 场景值
     */
    @JsonValue
    public String getValue() {
        return value;
    }

    /**
     * 获取控制模式。
     *
     * @return 控制模式
     */
    public ControlMode getControlMode() {
        return controlMode;
    }

    /**
     * 按前端字符串解析场景。
     *
     * @param value 字符串值
     * @return 演示场景
     */
    @JsonCreator
    public static DemoScenario fromValue(String value) {
        for (DemoScenario scenario : values()) {
            if (scenario.value.equals(value)) {
                return scenario;
            }
        }
        throw new IllegalArgumentException("不支持的演示场景: " + value);
    }
}

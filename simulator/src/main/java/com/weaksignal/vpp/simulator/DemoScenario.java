package com.weaksignal.vpp.simulator;

/**
 * 演示场景枚举。
 */
public enum DemoScenario {
    STABLE("stable"),
    DEGRADED("degraded"),
    LOST("lost"),
    RECOVERING("recovering");

    private final String value;

    DemoScenario(String value) {
        this.value = value;
    }

    /**
     * 获取前端兼容值。
     *
     * @return 场景值
     */
    public String getValue() {
        return value;
    }

    /**
     * 从字符串解析场景。
     *
     * @param value 场景值
     * @return 场景枚举
     */
    public static DemoScenario fromValue(String value) {
        for (DemoScenario scenario : values()) {
            if (scenario.value.equals(value)) {
                return scenario;
            }
        }
        throw new IllegalArgumentException("不支持的演示场景: " + value);
    }
}


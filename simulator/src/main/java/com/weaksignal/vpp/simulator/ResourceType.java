package com.weaksignal.vpp.simulator;

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

    ResourceType(String value) {
        this.value = value;
    }

    /**
     * 获取前端兼容值。
     *
     * @return 资源类型值
     */
    public String getValue() {
        return value;
    }
}

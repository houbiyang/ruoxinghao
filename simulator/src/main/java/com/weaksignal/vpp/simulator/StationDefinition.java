package com.weaksignal.vpp.simulator;

import java.util.List;

/**
 * 场站定义。
 *
 * @param stationId 场站标识
 * @param stationName 场站名称
 * @param resourceType 资源类型
 * @param capacity 容量上限
 */
public record StationDefinition(
        String stationId,
        String stationName,
        ResourceType resourceType,
        double capacity
) {

    /**
     * 默认场站列表。
     *
     * @return 场站定义列表
     */
    public static List<StationDefinition> defaults() {
        return List.of(
                new StationDefinition("pv-gobi-01", "光伏", ResourceType.PHOTOVOLTAIC, 168.0),
                new StationDefinition("wind-ridge-02", "风电", ResourceType.WIND, 220.0),
                new StationDefinition("thermal-plant-06", "火电", ResourceType.THERMAL, 320.0),
                new StationDefinition("grid-link-00", "市电", ResourceType.GRID, 300.0),
                new StationDefinition("storage-hub-03", "储能", ResourceType.STORAGE, 160.0),
                new StationDefinition("load-oasis-04", "负荷", ResourceType.LOAD, 210.0),
                new StationDefinition("charge-corridor-05", "充电设施", ResourceType.CHARGING, 130.0)
        );
    }

    /**
     * 默认场站标识集合。
     *
     * @return 场站标识列表
     */
    public static List<String> defaultStationIds() {
        return defaults().stream().map(StationDefinition::stationId).toList();
    }
}

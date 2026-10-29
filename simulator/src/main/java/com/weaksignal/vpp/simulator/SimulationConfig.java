package com.weaksignal.vpp.simulator;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 模拟器配置。
 *
 * @param mqttHost MQTT 主机
 * @param mqttPort MQTT 端口
 * @param mqttUsername MQTT 用户名
 * @param mqttPassword MQTT 密码
 * @param simulationIntervalMillis 发布周期
 * @param randomSeed 随机种子
 * @param initialScenario 初始场景
 * @param stationIds 启用场站
 */
public record SimulationConfig(
        String mqttHost,
        int mqttPort,
        String mqttUsername,
        String mqttPassword,
        long simulationIntervalMillis,
        long randomSeed,
        DemoScenario initialScenario,
        List<String> stationIds
) {

    /**
     * 从环境变量创建配置。
     *
     * @return 模拟器配置
     */
    public static SimulationConfig fromEnvironment() {
        String stationIdsRaw = read("SIMULATION_STATION_IDS", "");
        List<String> stationIds = stationIdsRaw.isBlank()
                ? StationDefinition.defaultStationIds()
                : Arrays.stream(stationIdsRaw.split(","))
                        .map(String::trim)
                        .filter(value -> !value.isBlank())
                        .collect(Collectors.toList());

        return new SimulationConfig(
                read("MQTT_HOST", "127.0.0.1"),
                Integer.parseInt(read("MQTT_PORT", "1883")),
                read("MQTT_USERNAME", read("EMQX_USERNAME", "vpp_client")),
                read("MQTT_PASSWORD", read("EMQX_PASSWORD", "replace_me")),
                Long.parseLong(read("SIMULATION_INTERVAL_MS", "1000")),
                Long.parseLong(read("SIMULATION_RANDOM_SEED", "20260629")),
                DemoScenario.fromValue(read("SIMULATION_INITIAL_SCENARIO", "stable")),
                stationIds
        );
    }

    private static String read(String key, String defaultValue) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}


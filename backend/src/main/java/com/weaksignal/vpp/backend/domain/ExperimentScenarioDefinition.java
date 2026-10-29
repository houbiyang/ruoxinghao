package com.weaksignal.vpp.backend.domain;

import java.util.List;
import java.util.Map;

/**
 * 故障注入实验场景定义。
 */
public enum ExperimentScenarioDefinition {

    /** 风速低于切入风速。 */
    ENV_WIND_CALM("ENV_WIND_CALM", "无风 / 低风速", "按风机功率曲线降低目标风电场可用容量与有功功率。", List.of("wind-ridge-02"), LinkState.S0, DemoScenario.STABLE, 120, Map.of()),
    /** 低辐照与云量遮挡。 */
    ENV_OVERCAST("ENV_OVERCAST", "阴天 / 低辐照", "按真实辐照度和云量降低目标光伏场站功率。", List.of("pv-gobi-01"), LinkState.S0, DemoScenario.STABLE, 120, Map.of()),
    /** 高温降额。 */
    ENV_HEAT("ENV_HEAT", "持续高温", "按组件或储能温度实施能力降额。", List.of("pv-gobi-01"), LinkState.S0, DemoScenario.STABLE, 120, Map.of()),
    /** 沙尘转换效率下降。 */
    ENV_DUST("ENV_DUST", "沙尘遮蔽", "按转换效率降低目标光伏场站的可用容量。", List.of("pv-gobi-01"), LinkState.S0, DemoScenario.STABLE, 120, Map.of()),
    /** 通信丢包。 */
    COM_PACKET_LOSS("COM_PACKET_LOSS", "突发丢包", "仅丢弃目标场站遥测，其他场站保持正常上报。", List.of("pv-gobi-01"), LinkState.S2, DemoScenario.STABLE, 120, Map.of()),
    /** 通信时延。 */
    COM_LATENCY("COM_LATENCY", "时延与抖动", "仅增加目标场站遥测和回执时延。", List.of("wind-ridge-02"), LinkState.S1, DemoScenario.STABLE, 120, Map.of()),
    /** 通信离线。 */
    COM_OFFLINE("COM_OFFLINE", "通信中断", "停止目标场站心跳与遥测，恢复时经 S4 同步。", List.of("storage-hub-03"), LinkState.S3, DemoScenario.STABLE, 120, Map.of()),
    /** 时延与丢包叠加。 */
    COM_COMBINED("COM_COMBINED", "时延与丢包组合", "同时对目标场站注入时延、抖动和丢包。", List.of("wind-ridge-02"), LinkState.S2, DemoScenario.STABLE, 120, Map.of()),
    /** 逆变器降额。 */
    DEV_INVERTER("DEV_INVERTER", "逆变器降额", "仅降低逆变器健康状态和功率能力，不改变通信状态。", List.of("pv-gobi-01"), LinkState.S0, DemoScenario.STABLE, 120, Map.of()),
    /** 储能簇退化。 */
    DEV_STORAGE("DEV_STORAGE", "储能簇退化", "降低在线簇数量和储能可用容量，不等同于通信离线。", List.of("storage-hub-03"), LinkState.S0, DemoScenario.STABLE, 120, Map.of()),

    /** 稳定基线，无故障。 */
    T0_STABLE_BASELINE(
            "T0_STABLE_BASELINE",
            "稳定基线",
            "无故障注入，记录正常协同状态下的基线数据。",
            List.of("pv-gobi-01", "wind-ridge-02", "storage-hub-03"),
            LinkState.S0,
            DemoScenario.STABLE,
            90,
            Map.of("mode", "baseline")
    ),

    /** 通信时延抖动。 */
    T1_LATENCY_JITTER(
            "T1_LATENCY_JITTER",
            "通信时延抖动",
            "对目标场站注入稳定可重复的时延抬升和抖动。",
            List.of("pv-gobi-01"),
            LinkState.S1,
            DemoScenario.DEGRADED,
            120,
            Map.of("baseLatencyMs", "120", "jitterMs", "160")
    ),

    /** 随机丢包。 */
    T2_RANDOM_PACKET_LOSS(
            "T2_RANDOM_PACKET_LOSS",
            "随机丢包",
            "按固定节奏丢弃目标场站部分遥测消息。",
            List.of("wind-ridge-02"),
            LinkState.S1,
            DemoScenario.DEGRADED,
            120,
            Map.of("dropEveryTicks", "4")
    ),

    /** 突发丢包。 */
    T3_BURST_PACKET_LOSS(
            "T3_BURST_PACKET_LOSS",
            "突发丢包",
            "周期性连续丢弃目标场站遥测，形成短时突发丢包。",
            List.of("pv-gobi-01", "wind-ridge-02"),
            LinkState.S2,
            DemoScenario.DEGRADED,
            120,
            Map.of("burstWindowTicks", "3", "periodTicks", "10")
    ),

    /** 间歇中断。 */
    T4_INTERMITTENT_DISCONNECT(
            "T4_INTERMITTENT_DISCONNECT",
            "间歇中断",
            "目标场站在线状态按固定周期切换，模拟弱覆盖边缘中断。",
            List.of("storage-hub-03"),
            LinkState.S2,
            DemoScenario.DEGRADED,
            120,
            Map.of("offlineWindowTicks", "2", "periodTicks", "8")
    ),

    /** 完全失联，本地自治。 */
    T5_FULL_LOSS_AUTONOMY(
            "T5_FULL_LOSS_AUTONOMY",
            "完全失联自治",
            "目标场站停止心跳和遥测，触发后端心跳超时与本地自治判断。",
            List.of("pv-gobi-01"),
            LinkState.S3,
            DemoScenario.LOST,
            120,
            Map.of("heartbeat", "stopped", "telemetry", "stopped")
    ),

    /** 带宽受限，遥测降频。 */
    T6_BANDWIDTH_LIMIT(
            "T6_BANDWIDTH_LIMIT",
            "带宽受限",
            "目标场站保持心跳，但遥测上报频率降低。",
            List.of("charge-corridor-05"),
            LinkState.S1,
            DemoScenario.DEGRADED,
            120,
            Map.of("telemetryEveryTicks", "3")
    ),

    /** 乱序与重复消息。 */
    T7_OUT_OF_ORDER_DUPLICATE(
            "T7_OUT_OF_ORDER_DUPLICATE",
            "乱序与重复消息",
            "目标场站周期性发送重复序号或旧时间戳遥测，验证后端幂等和过期拒绝。",
            List.of("wind-ridge-02"),
            LinkState.S1,
            DemoScenario.DEGRADED,
            120,
            Map.of("duplicateEveryTicks", "5")
    ),

    /** 遥测陈旧或冻结。 */
    T8_STALE_TELEMETRY(
            "T8_STALE_TELEMETRY",
            "遥测陈旧冻结",
            "目标场站保持功率冻结并持续发送陈旧采样时间。",
            List.of("load-oasis-04"),
            LinkState.S2,
            DemoScenario.DEGRADED,
            120,
            Map.of("staleSeconds", "18")
    ),

    /** 恢复同步。 */
    T9_RECOVERY_SYNC(
            "T9_RECOVERY_SYNC",
            "恢复同步",
            "目标场站从失联状态逐步恢复，先进入恢复同步再回到正常协同。",
            List.of("pv-gobi-01"),
            LinkState.S4,
            DemoScenario.RECOVERING,
            120,
            Map.of("rampRatio", "0.35")
    ),

    /** 控制确认丢失或延迟。 */
    T10_CONTROL_ACK_LOSS(
            "T10_CONTROL_ACK_LOSS",
            "控制确认丢失",
            "预留控制回执丢失实验，当前模拟器仅记录命令并保持遥测退化。",
            List.of("storage-hub-03"),
            LinkState.S1,
            DemoScenario.DEGRADED,
            90,
            Map.of("ackBehavior", "reserved")
    );

    private final String scenarioCode;
    private final String scenarioName;
    private final String description;
    private final List<String> targetStationIds;
    private final LinkState expectedLinkState;
    private final DemoScenario demoScenario;
    private final int durationSeconds;
    private final Map<String, String> faultParameters;

    /**
     * 创建实验场景定义。
     *
     * @param scenarioCode 场景编码
     * @param scenarioName 场景名称
     * @param description 场景说明
     * @param targetStationIds 默认目标场站
     * @param expectedLinkState 预期链路状态
     * @param demoScenario 关联演示场景
     * @param durationSeconds 默认持续时间
     * @param faultParameters 故障参数
     */
    ExperimentScenarioDefinition(
            String scenarioCode,
            String scenarioName,
            String description,
            List<String> targetStationIds,
            LinkState expectedLinkState,
            DemoScenario demoScenario,
            int durationSeconds,
            Map<String, String> faultParameters
    ) {
        this.scenarioCode = scenarioCode;
        this.scenarioName = scenarioName;
        this.description = description;
        this.targetStationIds = targetStationIds;
        this.expectedLinkState = expectedLinkState;
        this.demoScenario = demoScenario;
        this.durationSeconds = durationSeconds;
        this.faultParameters = faultParameters;
    }

    /**
     * 获取场景编码。
     *
     * @return 场景编码
     */
    public String getScenarioCode() {
        return scenarioCode;
    }

    /**
     * 获取场景名称。
     *
     * @return 场景名称
     */
    public String getScenarioName() {
        return scenarioName;
    }

    /**
     * 获取场景说明。
     *
     * @return 场景说明
     */
    public String getDescription() {
        return description;
    }

    /**
     * 获取默认目标场站。
     *
     * @return 默认目标场站
     */
    public List<String> getTargetStationIds() {
        return targetStationIds;
    }

    /**
     * 获取预期链路状态。
     *
     * @return 预期链路状态
     */
    public LinkState getExpectedLinkState() {
        return expectedLinkState;
    }

    /**
     * 获取关联演示场景。
     *
     * @return 关联演示场景
     */
    public DemoScenario getDemoScenario() {
        return demoScenario;
    }

    /**
     * 获取默认持续时间。
     *
     * @return 默认持续时间
     */
    public int getDurationSeconds() {
        return durationSeconds;
    }

    /**
     * 获取故障参数。
     *
     * @return 故障参数
     */
    public Map<String, String> getFaultParameters() {
        return faultParameters;
    }

    /**
     * 获取故障类别。
     *
     * @return 环境、通信或设备类别
     */
    public String getCategory() {
        return scenarioCode.startsWith("ENV_") ? "ENVIRONMENT"
                : scenarioCode.startsWith("COM_") ? "COMMUNICATION"
                : scenarioCode.startsWith("DEV_") ? "DEVICE" : "LEGACY";
    }

    /**
     * 获取支持的资源类型。
     *
     * @return 允许作为目标的资源类型
     */
    public List<ResourceType> getSupportedResourceTypes() {
        return switch (scenarioCode) {
            case "ENV_WIND_CALM" -> List.of(ResourceType.WIND);
            case "ENV_OVERCAST", "ENV_DUST", "DEV_INVERTER" -> List.of(ResourceType.PHOTOVOLTAIC);
            case "ENV_HEAT" -> List.of(ResourceType.PHOTOVOLTAIC, ResourceType.STORAGE);
            case "DEV_STORAGE" -> List.of(ResourceType.STORAGE);
            default -> List.of(ResourceType.values());
        };
    }

    /**
     * 获取可由客户端渲染的参数定义。
     *
     * @return 参数名到范围定义的映射
     */
    public Map<String, ExperimentParameterDefinition> getParameterDefinitions() {
        return switch (scenarioCode) {
            case "ENV_WIND_CALM" -> Map.of("windSpeed", parameter("风速", "m/s", 0.8, 0, 25, 0.1), "turbulence", parameter("湍流", "ratio", 0.06, 0, 1, 0.01));
            case "ENV_OVERCAST" -> Map.of("irradiance", parameter("辐照度", "W/m2", 160, 0, 1200, 10), "cloudCover", parameter("云量", "ratio", 0.85, 0, 1, 0.01));
            case "ENV_HEAT" -> Map.of("ambientTemperature", parameter("环境温度", "C", 46, -20, 70, 1), "moduleTemperature", parameter("组件温度", "C", 65, -20, 100, 1));
            case "ENV_DUST" -> Map.of("dustDensity", parameter("沙尘密度", "g/m2", 18, 0, 30, 1), "visibility", parameter("能见度", "km", 2, 0.5, 20, 0.5), "conversionEfficiency", parameter("转换效率", "ratio", 0.62, 0, 1, 0.01));
            case "COM_PACKET_LOSS" -> Map.of("packetLossRate", parameter("丢包率", "%", 35, 0, 100, 1), "burstLength", parameter("突发长度", "tick", 6, 1, 20, 1));
            case "COM_LATENCY" -> Map.of("latency", parameter("基础时延", "ms", 800, 0, 2000, 10), "jitter", parameter("时延抖动", "ms", 120, 0, 500, 10));
            case "COM_COMBINED" -> Map.of("latency", parameter("基础时延", "ms", 800, 0, 2000, 10),
                    "jitter", parameter("时延抖动", "ms", 120, 0, 500, 10),
                    "packetLossRate", parameter("丢包率", "%", 35, 0, 100, 1),
                    "burstLength", parameter("突发长度", "tick", 6, 1, 20, 1));
            case "COM_OFFLINE" -> Map.of();
            case "DEV_INVERTER" -> Map.of("deratingRate", parameter("降额比例", "ratio", 0.45, 0, 1, 0.05));
            case "DEV_STORAGE" -> Map.of("onlineClusterCount", parameter("在线簇数量", "cluster", 6, 0, 8, 1), "remainingSoc", parameter("剩余 SOC", "%", 42, 5, 100, 1));
            default -> Map.of();
        };
    }

    /**
     * 获取预期证据类型。
     *
     * @return 用于闭环观测的证据类型
     */
    public String getExpectedEvidenceType() {
        return switch (getCategory()) {
            case "COMMUNICATION" -> "LINK_STATE_OR_COMMUNICATION_WINDOW";
            case "ENVIRONMENT" -> "ENVIRONMENT_TELEMETRY";
            case "DEVICE" -> "DEVICE_CAPABILITY_CHANGE";
            default -> "TELEMETRY";
        };
    }

    /**
     * 判断场景是否支持自动恢复。
     *
     * @return 当前所有标准场景均支持自动恢复
     */
    public boolean isAutomaticRecoverySupported() {
        return true;
    }

    /**
     * 获取故障观测的默认超时时间。
     *
     * @return 故障注入后等待实际证据的秒数
     */
    public int getInjectionObservationTimeoutSeconds() {
        return "COM_OFFLINE".equals(scenarioCode) ? 30 : 45;
    }

    private static ExperimentParameterDefinition parameter(String name, String unit, double defaultValue, double minimum, double maximum, double step) {
        return new ExperimentParameterDefinition(name, unit, defaultValue, minimum, maximum, step);
    }

    /**
     * 按编码解析实验场景。
     *
     * @param scenarioCode 场景编码
     * @return 实验场景定义
     */
    public static ExperimentScenarioDefinition fromCode(String scenarioCode) {
        for (ExperimentScenarioDefinition definition : values()) {
            if (definition.scenarioCode.equals(scenarioCode)) {
                return definition;
            }
        }
        throw new IllegalArgumentException("不支持的实验场景: " + scenarioCode);
    }
}

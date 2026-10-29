package com.weaksignal.vpp.backend.environment;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weaksignal.vpp.backend.domain.LinkState;
import com.weaksignal.vpp.backend.domain.ResourceType;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 环境感知智能体服务，负责解释光伏和风电环境因素并输出能力修正因子。
 */
@Service
public class EnvironmentAssessmentService {

    private static final double DEFAULT_FACTOR = 1.0;

    private final ObjectMapper objectMapper;

    /**
     * 注入环境感知智能体依赖。
     *
     * @param objectMapper JSON 处理器
     */
    public EnvironmentAssessmentService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 评估场站环境影响。
     *
     * @param stationId 场站标识
     * @param resourceType 资源类型
     * @param activePower 实时功率
     * @param availableCapacity 原始可用容量
     * @param linkState 通信链路状态
     * @param payloadJson 遥测载荷
     * @return 环境感知评估结果
     */
    public EnvironmentAssessmentResult assess(
            String stationId,
            ResourceType resourceType,
            double activePower,
            double availableCapacity,
            LinkState linkState,
            String payloadJson
    ) {
        JsonNode metrics = readMetrics(payloadJson);
        return switch (resourceType) {
            case PHOTOVOLTAIC -> assessPhotovoltaic(stationId, activePower, availableCapacity, linkState, metrics);
            case WIND -> assessWind(stationId, activePower, availableCapacity, linkState, metrics);
            default -> neutralResult(stationId, resourceType);
        };
    }

    /**
     * 评估光伏环境影响。
     *
     * @param stationId 场站标识
     * @param activePower 实时功率
     * @param availableCapacity 原始可用容量
     * @param linkState 通信链路状态
     * @param metrics 遥测指标
     * @return 光伏环境评估结果
     */
    private EnvironmentAssessmentResult assessPhotovoltaic(
            String stationId,
            double activePower,
            double availableCapacity,
            LinkState linkState,
            JsonNode metrics
    ) {
        List<String> missingFields = missingFields(metrics, "irradiance", "cloudCover", "moduleTemperature", "conversionEfficiency");
        if (!missingFields.isEmpty()) {
            return unavailableResult(stationId, ResourceType.PHOTOVOLTAIC, missingFields);
        }
        double irradiance = metric(metrics, "irradiance");
        double cloudCover = metric(metrics, "cloudCover");
        double moduleTemperature = metric(metrics, "moduleTemperature");
        double conversionEfficiency = metric(metrics, "conversionEfficiency");
        double soilingLossRate = optionalMetric(metrics, "soilingLossRate", 0.0);
        double snowCover = optionalMetric(metrics, "snowCover", 0.0);
        double shadingLossRate = optionalMetric(metrics, "shadingLossRate", 0.0);
        double irradianceFactor = clamp(irradiance / 800.0, 0.0, 1.10);
        double cloudFactor = clamp(1.0 - cloudCover * 0.35, 0.45, 1.0);
        double temperatureFactor = clamp(1.0 - Math.max(0.0, moduleTemperature - 25.0) * 0.004, 0.70, 1.0);
        double lossFactor = clamp(conversionEfficiency * (1.0 - soilingLossRate - shadingLossRate - snowCover * 0.70), 0.15, 1.0);
        double factor = applyCommunicationConservativeLimit(
                clamp(irradianceFactor * cloudFactor * temperatureFactor * lossFactor, 0.0, 1.05),
                linkState
        );
        List<String> reasons = new ArrayList<>();
        reasons.add("辐照度 " + format(irradiance) + " W/m2");
        reasons.add("云量 " + format(cloudCover * 100.0) + "%");
        reasons.add("组件温度 " + format(moduleTemperature) + "°C");
        reasons.add("转换效率 " + format(conversionEfficiency * 100.0) + "%");
        reasons.add("积灰损失 " + format(soilingLossRate * 100.0) + "%");
        if (snowCover > 0.0) {
            reasons.add("积雪覆盖 " + format(snowCover * 100.0) + "%");
        }
        if (shadingLossRate > 0.0) {
            reasons.add("遮挡损失 " + format(shadingLossRate * 100.0) + "%");
        }
        List<String> constraints = new ArrayList<>();
        if (moduleTemperature >= 75.0) {
            constraints.add("组件高温，限制光伏上调能力");
        }
        if (snowCover >= 0.50) {
            constraints.add("积雪遮挡，光伏能力降额");
        }
        String riskLevel = constraints.isEmpty() && cloudCover < 0.70 && soilingLossRate < 0.15 && shadingLossRate < 0.18
                ? "NORMAL"
                : moduleTemperature >= 75.0 || snowCover >= 0.50
                ? "HIGH"
                : "MEDIUM";
        String interpretation = photovoltaicInterpretation(activePower, availableCapacity, irradiance, cloudCover, snowCover);
        return new EnvironmentAssessmentResult(
                stationId,
                ResourceType.PHOTOVOLTAIC.name(),
                factor,
                riskLevel,
                reasons,
                List.of("activePower", "dcPower", "moduleTemperature", "inverterTemperature"),
                interpretation,
                constraints,
                true,
                List.of()
        );
    }

    /**
     * 评估风电环境影响。
     *
     * @param stationId 场站标识
     * @param activePower 实时功率
     * @param availableCapacity 原始可用容量
     * @param linkState 通信链路状态
     * @param metrics 遥测指标
     * @return 风电环境评估结果
     */
    private EnvironmentAssessmentResult assessWind(
            String stationId,
            double activePower,
            double availableCapacity,
            LinkState linkState,
            JsonNode metrics
    ) {
        List<String> missingFields = missingFields(metrics, "windSpeed", "turbulence");
        if (!missingFields.isEmpty()) {
            return unavailableResult(stationId, ResourceType.WIND, missingFields);
        }
        double windSpeed = metric(metrics, "windSpeed");
        double turbulenceIntensity = metric(metrics, "turbulence");
        double airDensity = optionalMetric(metrics, "airDensity", 1.225);
        double icingRisk = optionalMetric(metrics, "icingRisk", 0.0);
        double gustSpeed = optionalMetric(metrics, "gustSpeed", windSpeed);
        double yawDeviation = optionalMetric(metrics, "yawDeviation", 0.0);
        double speedFactor = windSpeed < 3.0 || windSpeed > 25.0
                ? 0.0
                : clamp(Math.pow(windSpeed / 12.0, 3.0), 0.08, 1.15);
        double densityFactor = clamp(airDensity / 1.225, 0.80, 1.08);
        double turbulenceFactor = clamp(1.0 - Math.max(0.0, turbulenceIntensity - 0.12) * 0.70, 0.65, 1.0);
        double icingFactor = clamp(1.0 - icingRisk * 0.65, 0.20, 1.0);
        double gustFactor = gustSpeed >= 22.0 ? 0.75 : 1.0;
        double yawFactor = clamp(1.0 - Math.max(0.0, yawDeviation - 8.0) * 0.015, 0.70, 1.0);
        double factor = applyCommunicationConservativeLimit(
                clamp(speedFactor * densityFactor * turbulenceFactor * icingFactor * gustFactor * yawFactor, 0.0, 1.05),
                linkState
        );
        List<String> reasons = new ArrayList<>();
        reasons.add("风速 " + format(windSpeed) + " m/s");
        reasons.add("湍流强度 " + format(turbulenceIntensity * 100.0) + "%");
        reasons.add("空气密度 " + format(airDensity) + " kg/m3");
        reasons.add("结冰风险 " + format(icingRisk * 100.0) + "%");
        reasons.add("阵风 " + format(gustSpeed) + " m/s");
        reasons.add("偏航偏差 " + format(yawDeviation) + "°");
        List<String> constraints = new ArrayList<>();
        if (icingRisk >= 0.45) {
            constraints.add("叶片结冰风险，风电能力降额");
        }
        if (gustSpeed >= 22.0) {
            constraints.add("阵风接近保护阈值，限制上调能力");
        }
        if (turbulenceIntensity >= 0.25) {
            constraints.add("湍流偏高，限制快速爬坡");
        }
        String riskLevel = constraints.isEmpty()
                ? "NORMAL"
                : icingRisk >= 0.70 || gustSpeed >= 25.0
                ? "HIGH"
                : "MEDIUM";
        String interpretation = windInterpretation(activePower, availableCapacity, windSpeed, icingRisk, yawDeviation);
        return new EnvironmentAssessmentResult(
                stationId,
                ResourceType.WIND.name(),
                factor,
                riskLevel,
                reasons,
                List.of("activePower", "rotorSpeed", "yawDeviation", "rampRate"),
                interpretation,
                constraints,
                true,
                List.of()
        );
    }

    /**
     * 构建不受环境修正的默认结果。
     *
     * @param stationId 场站标识
     * @param resourceType 资源类型
     * @return 默认环境评估结果
     */
    private EnvironmentAssessmentResult neutralResult(String stationId, ResourceType resourceType) {
        return new EnvironmentAssessmentResult(
                stationId,
                resourceType.name(),
                DEFAULT_FACTOR,
                "NORMAL",
                List.of("非光伏或风电资源，不应用天气发电能力修正"),
                List.of(),
                "当前资源类型不依赖光伏或风电环境修正。",
                List.of(),
                true,
                List.of()
        );
    }

    /**
     * 构建关键环境测量缺失时的不可用评估。
     *
     * @param stationId 场站标识
     * @param resourceType 资源类型
     * @param missingFields 缺失字段
     * @return 不可用环境评估
     */
    private EnvironmentAssessmentResult unavailableResult(String stationId, ResourceType resourceType, List<String> missingFields) {
        return new EnvironmentAssessmentResult(
                stationId,
                resourceType.name(),
                DEFAULT_FACTOR,
                "UNKNOWN",
                List.of("关键环境测量缺失，未生成可信环境修正因子"),
                List.of(),
                "环境测量不完整，当前不将环境数据解释为设备或故障证据。",
                List.of("补齐环境测量后方可参与环境能力修正"),
                false,
                missingFields
        );
    }

    /**
     * 从遥测载荷读取指标节点。
     *
     * @param payloadJson 遥测载荷
     * @return 指标节点
     */
    private JsonNode readMetrics(String payloadJson) {
        try {
            return objectMapper.readTree(payloadJson).path("metrics");
        } catch (JsonProcessingException exception) {
            return objectMapper.createObjectNode();
        }
    }

    /**
     * 读取数值指标。
     *
     * @param metrics 指标节点
     * @param name 指标名称
     * @param defaultValue 默认值
     * @return 指标数值
     */
    private double metric(JsonNode metrics, String name) {
        JsonNode value = metrics.path(name);
        return value.asDouble();
    }

    /**
     * 读取非关键环境指标。
     *
     * @param metrics 遥测指标
     * @param name 指标名称
     * @param defaultValue 缺失时的中性值
     * @return 指标数值
     */
    private double optionalMetric(JsonNode metrics, String name, double defaultValue) {
        JsonNode value = metrics.path(name);
        return value.isNumber() ? value.asDouble() : defaultValue;
    }

    /**
     * 查找缺失或非数值的关键环境指标。
     *
     * @param metrics 遥测指标
     * @param names 字段名称
     * @return 缺失字段集合
     */
    private List<String> missingFields(JsonNode metrics, String... names) {
        List<String> missing = new ArrayList<>();
        for (String name : names) {
            if (!metrics.path(name).isNumber()) {
                missing.add(name);
            }
        }
        return missing;
    }

    /**
     * 通信退化时禁止环境修正放大能力。
     *
     * @param factor 原始环境因子
     * @param linkState 链路状态
     * @return 保守环境因子
     */
    private double applyCommunicationConservativeLimit(double factor, LinkState linkState) {
        return linkState == LinkState.S0 ? factor : Math.min(factor, 1.0);
    }

    /**
     * 生成光伏设备数据解释。
     *
     * @param activePower 实时功率
     * @param availableCapacity 原始可用容量
     * @param irradiance 辐照度
     * @param cloudCover 云量
     * @param snowCover 积雪覆盖
     * @return 设备数据解释
     */
    private String photovoltaicInterpretation(
            double activePower,
            double availableCapacity,
            double irradiance,
            double cloudCover,
            double snowCover
    ) {
        double powerRatio = activePower / Math.max(availableCapacity, 1.0);
        if (powerRatio < 0.45 && (irradiance < 450.0 || cloudCover >= 0.60 || snowCover >= 0.20)) {
            return "当前光伏低出力可由低辐照、云量或积雪解释，不直接判定为设备故障。";
        }
        if (powerRatio < 0.45 && irradiance >= 700.0 && cloudCover < 0.35) {
            return "当前辐照条件较好但光伏出力偏低，需关注组件、逆变器、遮挡或通信异常风险。";
        }
        return "当前光伏设备数据与环境条件基本一致，按环境修正后能力参与调度。";
    }

    /**
     * 生成风电设备数据解释。
     *
     * @param activePower 实时功率
     * @param availableCapacity 原始可用容量
     * @param windSpeed 风速
     * @param icingRisk 结冰风险
     * @param yawDeviation 偏航偏差
     * @return 设备数据解释
     */
    private String windInterpretation(
            double activePower,
            double availableCapacity,
            double windSpeed,
            double icingRisk,
            double yawDeviation
    ) {
        double powerRatio = activePower / Math.max(availableCapacity, 1.0);
        if (powerRatio < 0.35 && windSpeed < 4.0) {
            return "当前风电低出力可由低风速解释，不直接判定为设备故障。";
        }
        if (powerRatio < 0.45 && windSpeed >= 7.0) {
            return "当前风速正常但风电出力偏低，需关注偏航、结冰、限功率、风机或通信异常风险。";
        }
        if (icingRisk >= 0.45 || yawDeviation >= 12.0) {
            return "当前风电设备数据受结冰风险或偏航偏差影响，需按降额能力参与调度。";
        }
        return "当前风电设备数据与环境条件基本一致，按环境修正后能力参与调度。";
    }

    /**
     * 限制数值范围。
     *
     * @param value 原始数值
     * @param min 最小值
     * @param max 最大值
     * @return 限制后的数值
     */
    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * 格式化展示数值。
     *
     * @param value 原始数值
     * @return 一位小数字符串
     */
    private String format(double value) {
        return String.format("%.1f", value);
    }
}

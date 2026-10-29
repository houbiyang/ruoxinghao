package com.weaksignal.vpp.simulator;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.eclipse.paho.mqttv5.client.IMqttMessageListener;
import org.eclipse.paho.mqttv5.client.MqttAsyncClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.MqttSubscription;

/**
 * MQTT 模拟器客户端。
 */
public class MqttSimulationClient {

    private final SimulationConfig config;
    private final SimulationEngine simulationEngine;
    private final ScheduledExecutorService scheduler;
    private final ScheduledExecutorService outboundScheduler;
    private MqttAsyncClient mqttAsyncClient;

    /**
     * 创建模拟器客户端。
     *
     * @param config 仿真配置
     * @param simulationEngine 仿真引擎
     */
    public MqttSimulationClient(SimulationConfig config, SimulationEngine simulationEngine) {
        this.config = config;
        this.simulationEngine = simulationEngine;
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        this.outboundScheduler = Executors.newSingleThreadScheduledExecutor();
    }

    /**
     * 启动模拟器客户端。
     *
     * @throws Exception 连接异常
     */
    public void start() throws Exception {
        System.out.println("已启用场站: " + String.join(", ", config.stationIds()));
        System.out.println("当前场景: " + config.initialScenario().getValue());
        System.out.println("MQTT 地址: tcp://" + config.mqttHost() + ":" + config.mqttPort());
        System.out.println("发布周期: " + config.simulationIntervalMillis() + "ms");
        System.out.println("随机种子: " + config.randomSeed());

        mqttAsyncClient = new MqttAsyncClient(
                "tcp://" + config.mqttHost() + ":" + config.mqttPort(),
                "simulator-all-" + java.util.UUID.randomUUID(),
                new MemoryPersistence()
        );
        MqttConnectionOptions options = new MqttConnectionOptions();
        options.setUserName(config.mqttUsername());
        options.setPassword(config.mqttPassword().getBytes(StandardCharsets.UTF_8));
        options.setAutomaticReconnect(true);
        options.setSessionExpiryInterval(60L);
        options.setWill("vpp/all/heartbeat/report", new MqttMessage((
                "{\"stationId\":\"all\",\"online\":false,\"heartbeatAt\":\"" + OffsetDateTime.now() + "\",\"clientId\":\"simulator-all\"}"
        ).getBytes(StandardCharsets.UTF_8)));
        mqttAsyncClient.connect(options).waitForCompletion();
        MqttSubscription[] subscriptions = {
                new MqttSubscription("vpp/all/scenario/apply", 1),
                new MqttSubscription("vpp/+/control/apply", 1),
                new MqttSubscription("vpp/+/control/release", 1),
                new MqttSubscription("vpp/+/fault-action/apply", 1)
        };
        IMqttMessageListener routedListener = (topic, message) -> {
            String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
            if ("vpp/all/scenario/apply".equals(topic)) {
                applyScenario(payload);
                return;
            }
            if (topic.endsWith("/control/apply")) {
                applyDispatchControl(payload);
                return;
            }
            if (topic.endsWith("/control/release")) {
                releaseDispatchControl(payload);
                return;
            }
            if (topic.endsWith("/fault-action/apply")) {
                applyFaultAction(payload);
            }
        };
        IMqttMessageListener[] messageListeners = {routedListener, routedListener, routedListener, routedListener};
        mqttAsyncClient.subscribe(subscriptions, null, null, messageListeners, null).waitForCompletion();
        scheduler.scheduleAtFixedRate(this::publishTickSafely, 0, config.simulationIntervalMillis(), TimeUnit.MILLISECONDS);
    }

    private void publishTickSafely() {
        try {
            SimulationTick tick = simulationEngine.nextTick();
            for (HeartbeatPayload heartbeatPayload : tick.heartbeatPayloads()) {
                publishWithCommunicationDelay(
                        "vpp/" + heartbeatPayload.stationId() + "/heartbeat/report",
                        toHeartbeatJson(heartbeatPayload),
                        true,
                        heartbeatPayload.stationId()
                );
            }
            for (TelemetryPayload telemetryPayload : tick.telemetryPayloads()) {
                publishWithCommunicationDelay(
                        "vpp/" + telemetryPayload.stationId() + "/telemetry/report",
                        toTelemetryJson(telemetryPayload),
                        false,
                        telemetryPayload.stationId()
                );
            }
            for (ScenarioReportPayload scenarioReport : tick.scenarioReports()) {
                publish("vpp/all/scenario/report", toScenarioReportJson(scenarioReport), false);
            }
        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }

    private void applyScenario(String json) {
        String scenarioValue = extractJsonValue(json, "scenario");
        if (scenarioValue == null || scenarioValue.isBlank()) {
            return;
        }
        DemoScenario scenario = DemoScenario.fromValue(scenarioValue);
        String commandType = extractJsonValue(json, "commandType");
        if ("START_EXPERIMENT".equals(commandType)) {
            simulationEngine.applyExperiment(new ExperimentCommand(
                    commandType,
                    extractJsonValue(json, "experimentScenarioCode"),
                    extractJsonValue(json, "experimentRunId"),
                    scenario,
                    extractJsonArray(json, "targetStationIds"),
                    extractJsonDoubleMap(json, "faultParameters"),
                    extractJsonInt(json, "severityLevel", 3),
                    extractJsonInt(json, "durationSeconds", 120),
                    extractJsonBoolean(json, "automaticRecovery", true),
                    extractJsonValue(json, "controlStrategy"),
                    extractJsonLong(json, "randomSeed")
                ));
            return;
        }
        if ("STOP_EXPERIMENT".equals(commandType)) {
            ScenarioReportPayload recoveryStarted = simulationEngine.stopOrConfirmStable(
                    extractJsonValue(json, "experimentRunId"),
                    extractJsonValue(json, "experimentScenarioCode"),
                    extractJsonArray(json, "targetStationIds")
            );
            if (recoveryStarted != null) {
                try {
                    publish("vpp/all/scenario/report", toScenarioReportJson(recoveryStarted), false);
                } catch (MqttException exception) {
                    throw new IllegalStateException("恢复开始报告发布失败", exception);
                }
            }
            return;
        }
        simulationEngine.applyScenario(scenario);
    }

    private void publish(String topic, String payload, boolean retained) throws MqttException {
        MqttMessage message = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
        message.setQos(1);
        message.setRetained(retained);
        mqttAsyncClient.publish(topic, message).waitForCompletion();
    }

    /**
     * 将目标场站消息写入独立出站延迟队列，避免阻塞模拟器固定周期计算。
     *
     * @param topic MQTT 主题
     * @param payload 消息载荷
     * @param retained 是否保留消息
     * @param stationId 场站标识
     * @throws MqttException 无需延迟且立即发布失败时抛出
     */
    private void publishWithCommunicationDelay(
            String topic,
            String payload,
            boolean retained,
            String stationId
    ) throws MqttException {
        long delayMillis = simulationEngine.getCommunicationDelayMillis(stationId);
        if (delayMillis <= 0L) {
            publish(topic, payload, retained);
            return;
        }
        outboundScheduler.schedule(() -> {
            try {
                publish(topic, payload, retained);
            } catch (MqttException exception) {
                throw new IllegalStateException("延迟出站消息发布失败", exception);
            }
        }, delayMillis, TimeUnit.MILLISECONDS);
    }

    private void applyDispatchControl(String json) {
        String commandId = extractJsonValue(json, "commandId");
        String stationId = extractJsonValue(json, "stationId");
        String expireAtValue = extractJsonValue(json, "expireAt");
        if (commandId == null || stationId == null || expireAtValue == null) {
            return;
        }
        try {
            DispatchControlCommand command = new DispatchControlCommand(
                    commandId,
                    extractJsonValue(json, "dispatchPlanId"),
                    extractJsonValue(json, "planVersion"),
                    extractJsonValue(json, "dispatchPlanItemId"),
                    stationId,
                    extractJsonDouble(json, "targetPower", -1),
                    OffsetDateTime.parse(expireAtValue),
                    extractJsonDouble(json, "maximumRampRate", 1),
                    extractJsonValue(json, "controlMode")
            );
            DispatchControlResult result = simulationEngine.applyDispatchControl(command, OffsetDateTime.now());
            if (!result.accepted()) {
                publishAcknowledgementWithDelay(commandId, stationId, "REJECTED", false, result.message(), expireAtValue);
                return;
            }
            publishAcknowledgementWithDelay(commandId, stationId, "RECEIVED", true, "场站边缘控制器已接收指令", expireAtValue);
            publishAcknowledgementWithDelay(commandId, stationId, "EXECUTED", true, result.message(), expireAtValue);
        } catch (Exception exception) {
            try {
                publishAcknowledgement(commandId, stationId, "REJECTED", false, "控制指令格式不合法");
            } catch (MqttException mqttException) {
                mqttException.printStackTrace();
            }
        }
    }

    /**
     * 释放指定计划对场站的功率控制，使后续遥测恢复模拟器自然基线。
     *
     * @param json 调度控制释放消息
     */
    private void releaseDispatchControl(String json) {
        String stationId = extractJsonValue(json, "stationId");
        String dispatchPlanId = extractJsonValue(json, "dispatchPlanId");
        if (stationId == null || stationId.isBlank()) {
            return;
        }
        simulationEngine.releaseDispatchControl(stationId, dispatchPlanId);
    }

    private void applyFaultAction(String json) {
        String actionId = extractJsonValue(json, "actionId");
        String stationId = extractJsonValue(json, "stationId");
        String actionTypeValue = extractJsonValue(json, "actionType");
        String issuedAtValue = extractJsonValue(json, "issuedAt");
        String expireAtValue = extractJsonValue(json, "expireAt");
        if (actionId == null || stationId == null || actionTypeValue == null
                || issuedAtValue == null || expireAtValue == null) {
            return;
        }
        try {
            FaultActionCommand command = new FaultActionCommand(
                    actionId,
                    extractJsonValue(json, "diagnosisRunId"),
                    stationId,
                    FaultActionType.valueOf(actionTypeValue),
                    OffsetDateTime.parse(issuedAtValue),
                    OffsetDateTime.parse(expireAtValue)
            );
            FaultActionResult result = simulationEngine.applyFaultAction(command, OffsetDateTime.now());
            if (!result.accepted()) {
                publishAcknowledgement(actionId, stationId, "REJECTED", false, result.message());
                return;
            }
            publishAcknowledgement(actionId, stationId, "RECEIVED", true, "边缘控制器已接收故障动作");
            publishAcknowledgement(actionId, stationId, "EXECUTED", true, result.message());
        } catch (Exception exception) {
            try {
                publishAcknowledgement(actionId, stationId, "REJECTED", false, "未知动作或故障命令格式不合法");
            } catch (MqttException mqttException) {
                mqttException.printStackTrace();
            }
        }
    }

    private void publishAcknowledgement(
            String commandId,
            String stationId,
            String ackStage,
            boolean success,
            String message
    ) throws MqttException {
        String payload = "{"
                + "\"commandId\":\"" + commandId + "\","
                + "\"stationId\":\"" + stationId + "\","
                + "\"ackStage\":\"" + ackStage + "\","
                + "\"success\":" + success + ","
                + "\"message\":\"" + message + "\","
                + "\"ackTime\":\"" + OffsetDateTime.now() + "\""
                + "}";
        publish("vpp/" + stationId + "/ack/report", payload, false);
    }

    /**
     * 按当前通信实验的实际时延发布控制确认。
     *
     * @param commandId 控制命令标识
     * @param stationId 场站标识
     * @param ackStage 确认阶段
     * @param success 是否成功
     * @param message 确认说明
     */
    private void publishAcknowledgementWithDelay(
            String commandId,
            String stationId,
            String ackStage,
            boolean success,
            String message,
        String expireAtValue
    ) {
        long delayMillis = simulationEngine.getControlAcknowledgementDelayMillis(stationId);
        boolean delayedAcknowledgement = delayMillis > 0;
        if ("EXECUTED".equals(ackStage) && delayMillis > 0) {
            delayMillis += Math.max(1L, config.simulationIntervalMillis());
        }
        outboundScheduler.schedule(() -> {
            try {
                if (delayedAcknowledgement && isExpiredOrRecoveredControlTask(stationId, expireAtValue)) {
                    return;
                }
                publishAcknowledgement(commandId, stationId, ackStage, success, message);
            } catch (MqttException exception) {
                throw new IllegalStateException("控制确认发布失败", exception);
            }
        }, delayMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * 判断延迟控制确认是否已失效。
     *
     * @param stationId 场站标识
     * @param expireAtValue 指令截止时间
     * @return 已过期或通信时延实验已恢复时返回 {@code true}
     */
    private boolean isExpiredOrRecoveredControlTask(String stationId, String expireAtValue) {
        if (expireAtValue == null || expireAtValue.isBlank()) {
            return !simulationEngine.isControlAcknowledgementDelayActive(stationId);
        }
        return OffsetDateTime.parse(expireAtValue).isBefore(OffsetDateTime.now())
                || !simulationEngine.isControlAcknowledgementDelayActive(stationId);
    }

    private String toHeartbeatJson(HeartbeatPayload payload) {
        return "{"
                + "\"stationId\":\"" + payload.stationId() + "\","
                + "\"online\":" + payload.online() + ","
                + "\"heartbeatAt\":\"" + payload.heartbeatAt() + "\","
                + "\"clientId\":\"" + payload.clientId() + "\""
                + "}";
    }

    private String toTelemetryJson(TelemetryPayload payload) {
        StringBuilder metricsBuilder = new StringBuilder("{");
        boolean first = true;
        for (var entry : payload.metrics().entrySet()) {
            if (!first) {
                metricsBuilder.append(",");
            }
            metricsBuilder.append("\"").append(entry.getKey()).append("\":").append(entry.getValue());
            first = false;
        }
        metricsBuilder.append("}");

        return "{"
                + "\"messageId\":\"" + payload.messageId() + "\","
                + "\"stationId\":\"" + payload.stationId() + "\","
                + "\"stationName\":\"" + payload.stationName() + "\","
                + "\"resourceType\":\"" + payload.resourceType() + "\","
                + "\"sequenceNumber\":" + payload.sequenceNumber() + ","
                + "\"deviceTimestamp\":\"" + payload.deviceTimestamp() + "\","
                + "\"sendTimestamp\":\"" + payload.sendTimestamp() + "\","
                + "\"activePower\":" + payload.activePower() + ","
                + "\"availableCapacity\":" + payload.availableCapacity() + ","
                + "\"latency\":" + payload.latency() + ","
                + "\"packetLossRate\":" + payload.packetLossRate() + ","
                + "\"online\":" + payload.online() + ","
                + "\"qualityCode\":\"" + payload.qualityCode() + "\","
                + "\"linkState\":\"" + payload.linkState() + "\","
                + "\"experimentRunId\":" + quotedJsonValue(payload.experimentRunId()) + ","
                + "\"activeFaultCode\":" + quotedJsonValue(payload.activeFaultCode()) + ","
                + "\"metrics\":" + metricsBuilder
                + "}";
    }

    private String toScenarioReportJson(ScenarioReportPayload payload) {
        String targets = payload.targetStationIds().stream().map(stationId -> "\"" + stationId + "\"")
                .collect(java.util.stream.Collectors.joining(","));
        return "{"
                + "\"experimentRunId\":\"" + payload.experimentRunId() + "\","
                + "\"scenarioCode\":\"" + payload.scenarioCode() + "\","
                + "\"status\":\"" + payload.status() + "\","
                + "\"targetStationIds\":[" + targets + "],"
                + "\"reportedAt\":\"" + payload.reportedAt() + "\","
                + "\"failureReason\":null"
                + "}";
    }

    private String quotedJsonValue(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    private String extractJsonValue(String json, String key) {
        String pattern = "\"" + key + "\":\"";
        int startIndex = json.indexOf(pattern);
        if (startIndex < 0) {
            return null;
        }
        int valueStart = startIndex + pattern.length();
        int valueEnd = json.indexOf("\"", valueStart);
        if (valueEnd < 0) {
            return null;
        }
        return json.substring(valueStart, valueEnd);
    }

    private int extractJsonInt(String json, String key, int defaultValue) {
        String pattern = "\"" + key + "\":";
        int startIndex = json.indexOf(pattern);
        if (startIndex < 0) {
            return defaultValue;
        }
        int valueStart = startIndex + pattern.length();
        int valueEnd = valueStart;
        while (valueEnd < json.length() && Character.isDigit(json.charAt(valueEnd))) {
            valueEnd += 1;
        }
        if (valueEnd == valueStart) {
            return defaultValue;
        }
        return Integer.parseInt(json.substring(valueStart, valueEnd));
    }

    private Long extractJsonLong(String json, String key) {
        String pattern = "\"" + key + "\":";
        int startIndex = json.indexOf(pattern);
        if (startIndex < 0 || json.startsWith("null", startIndex + pattern.length())) {
            return null;
        }
        int valueStart = startIndex + pattern.length();
        int valueEnd = valueStart;
        if (valueEnd < json.length() && json.charAt(valueEnd) == '-') {
            valueEnd += 1;
        }
        while (valueEnd < json.length() && Character.isDigit(json.charAt(valueEnd))) {
            valueEnd += 1;
        }
        if (valueEnd == valueStart) {
            return null;
        }
        try {
            return Long.parseLong(json.substring(valueStart, valueEnd));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private double extractJsonDouble(String json, String key, double defaultValue) {
        String pattern = "\"" + key + "\":";
        int startIndex = json.indexOf(pattern);
        if (startIndex < 0) {
            return defaultValue;
        }
        int valueStart = startIndex + pattern.length();
        int valueEnd = valueStart;
        while (valueEnd < json.length()) {
            char currentCharacter = json.charAt(valueEnd);
            if (!Character.isDigit(currentCharacter)
                    && currentCharacter != '.'
                    && currentCharacter != '-') {
                break;
            }
            valueEnd += 1;
        }
        if (valueEnd == valueStart) {
            return defaultValue;
        }
        return Double.parseDouble(json.substring(valueStart, valueEnd));
    }

    private List<String> extractJsonArray(String json, String key) {
        String pattern = "\"" + key + "\":[";
        int startIndex = json.indexOf(pattern);
        if (startIndex < 0) {
            return List.of();
        }
        int valueStart = startIndex + pattern.length();
        int valueEnd = json.indexOf("]", valueStart);
        if (valueEnd < 0) {
            return List.of();
        }
        String arrayBody = json.substring(valueStart, valueEnd).trim();
        if (arrayBody.isBlank()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (String rawValue : arrayBody.split(",")) {
            String value = rawValue.trim();
            if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
                values.add(value.substring(1, value.length() - 1));
            }
        }
        return values;
    }

    private boolean extractJsonBoolean(String json, String key, boolean defaultValue) {
        String pattern = "\"" + key + "\":";
        int startIndex = json.indexOf(pattern);
        if (startIndex < 0) {
            return defaultValue;
        }
        int valueStart = startIndex + pattern.length();
        if (json.startsWith("true", valueStart)) {
            return true;
        }
        if (json.startsWith("false", valueStart)) {
            return false;
        }
        return defaultValue;
    }

    private java.util.Map<String, Double> extractJsonDoubleMap(String json, String key) {
        String pattern = "\"" + key + "\":{";
        int startIndex = json.indexOf(pattern);
        if (startIndex < 0) {
            return java.util.Map.of();
        }
        int valueStart = startIndex + pattern.length();
        int valueEnd = json.indexOf("}", valueStart);
        if (valueEnd < 0) {
            return java.util.Map.of();
        }
        java.util.Map<String, Double> values = new java.util.LinkedHashMap<>();
        for (String entry : json.substring(valueStart, valueEnd).split(",")) {
            String[] parts = entry.split(":", 2);
            if (parts.length != 2) {
                continue;
            }
            String rawKey = parts[0].trim();
            String parameterName = rawKey.startsWith("\"") && rawKey.endsWith("\"")
                    ? rawKey.substring(1, rawKey.length() - 1)
                    : rawKey;
            try {
                double value = Double.parseDouble(parts[1].trim());
                if (Double.isFinite(value)) {
                    values.put(parameterName, value);
                }
            } catch (NumberFormatException exception) {
                // 忽略非数值参数，后端已负责参数契约校验。
            }
        }
        return values;
    }
}

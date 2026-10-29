package com.weaksignal.vpp.backend.mqtt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weaksignal.vpp.backend.config.MqttProperties;
import com.weaksignal.vpp.backend.mqtt.dto.ScenarioApplyCommandMessage;
import com.weaksignal.vpp.backend.mqtt.dto.DispatchControlMessage;
import com.weaksignal.vpp.backend.mqtt.dto.DispatchControlReleaseMessage;
import com.weaksignal.vpp.backend.mqtt.dto.FaultActionCommandMessage;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.eclipse.paho.mqttv5.client.MqttAsyncClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 基于 Paho 的 MQTT 发布网关。
 */
@Component
public class PahoMqttGateway implements MqttGateway {

    private static final Logger LOGGER = LoggerFactory.getLogger(PahoMqttGateway.class);

    private final MqttProperties mqttProperties;
    private final ObjectMapper objectMapper;
    private MqttAsyncClient mqttAsyncClient;

    /**
     * 注入 MQTT 发布依赖。
     *
     * @param mqttProperties MQTT 配置
     * @param objectMapper JSON 组件
     */
    public PahoMqttGateway(MqttProperties mqttProperties, ObjectMapper objectMapper) {
        this.mqttProperties = mqttProperties;
        this.objectMapper = objectMapper;
    }

    /**
     * 初始化 MQTT 连接。
     */
    @PostConstruct
    public void connect() {
        try {
            mqttAsyncClient = new MqttAsyncClient(
                    buildBrokerUrl(),
                    mqttProperties.clientId() + "-publisher",
                    new MemoryPersistence()
            );
            MqttConnectionOptions options = new MqttConnectionOptions();
            options.setUserName(mqttProperties.username());
            options.setPassword(mqttProperties.password() == null ? null : mqttProperties.password().getBytes());
            options.setAutomaticReconnect(true);
            mqttAsyncClient.connect(options).waitForCompletion();
            LOGGER.info("MQTT 发布端已连接。host={}, port={}", mqttProperties.host(), mqttProperties.port());
        } catch (MqttException exception) {
            LOGGER.warn("MQTT 发布端初始化失败，场景命令暂不可下发。host={}, port={}", mqttProperties.host(), mqttProperties.port(), exception);
        }
    }

    @Override
    public void publishGlobalScenario(ScenarioApplyCommandMessage message) {
        if (mqttAsyncClient == null || !mqttAsyncClient.isConnected()) {
            LOGGER.warn("MQTT 发布端未连接，场景命令未发送。commandId={}", message.commandId());
            return;
        }

        try {
            MqttMessage mqttMessage = new MqttMessage(objectMapper.writeValueAsBytes(message));
            mqttMessage.setQos(1);
            mqttAsyncClient.publish("vpp/all/scenario/apply", mqttMessage).waitForCompletion();
            LOGGER.info("已下发全局场景命令。commandId={}, scenario={}", message.commandId(), message.scenario().getValue());
        } catch (MqttException | JsonProcessingException exception) {
            LOGGER.warn("下发全局场景命令失败。commandId={}", message.commandId(), exception);
        }
    }

    @Override
    public boolean publishDispatchControl(DispatchControlMessage message) {
        if (mqttAsyncClient == null || !mqttAsyncClient.isConnected()) {
            LOGGER.warn("MQTT 发布端未连接，控制指令未发送。commandId={}", message.commandId());
            return false;
        }

        try {
            MqttMessage mqttMessage = new MqttMessage(objectMapper.writeValueAsBytes(message));
            mqttMessage.setQos(1);
            mqttAsyncClient.publish("vpp/" + message.stationId() + "/control/apply", mqttMessage)
                    .waitForCompletion();
            LOGGER.info("已下发场站控制指令。commandId={}, stationId={}", message.commandId(), message.stationId());
            return true;
        } catch (MqttException | JsonProcessingException exception) {
            LOGGER.warn("下发场站控制指令失败。commandId={}, stationId={}",
                    message.commandId(), message.stationId(), exception);
            return false;
        }
    }

    @Override
    public boolean publishDispatchControlRelease(DispatchControlReleaseMessage message) {
        if (mqttAsyncClient == null || !mqttAsyncClient.isConnected()) {
            LOGGER.warn("MQTT 发布端未连接，调度控制释放消息未发送。releaseId={}", message.releaseId());
            return false;
        }

        try {
            MqttMessage mqttMessage = new MqttMessage(objectMapper.writeValueAsBytes(message));
            mqttMessage.setQos(1);
            mqttAsyncClient.publish("vpp/" + message.stationId() + "/control/release", mqttMessage)
                    .waitForCompletion();
            LOGGER.info("已下发场站调度控制释放消息。releaseId={}, stationId={}",
                    message.releaseId(), message.stationId());
            return true;
        } catch (MqttException | JsonProcessingException exception) {
            LOGGER.warn("场站调度控制释放消息下发失败。releaseId={}, stationId={}",
                    message.releaseId(), message.stationId(), exception);
            return false;
        }
    }

    @Override
    public boolean publishFaultAction(FaultActionCommandMessage message) {
        if (mqttAsyncClient == null || !mqttAsyncClient.isConnected()) {
            LOGGER.warn("MQTT 发布端未连接，故障动作未发送。actionId={}", message.actionId());
            return false;
        }
        try {
            MqttMessage mqttMessage = new MqttMessage(objectMapper.writeValueAsBytes(message));
            mqttMessage.setQos(1);
            mqttAsyncClient.publish("vpp/" + message.stationId() + "/fault-action/apply", mqttMessage)
                    .waitForCompletion();
            LOGGER.info("已下发故障处置动作。actionId={}, stationId={}, actionType={}",
                    message.actionId(), message.stationId(), message.actionType());
            return true;
        } catch (MqttException | JsonProcessingException exception) {
            LOGGER.warn("故障处置动作下发失败。actionId={}, stationId={}",
                    message.actionId(), message.stationId(), exception);
            return false;
        }
    }

    /**
     * 关闭 MQTT 连接。
     */
    @PreDestroy
    public void disconnect() {
        if (mqttAsyncClient == null) {
            return;
        }
        try {
            mqttAsyncClient.disconnect();
        } catch (MqttException exception) {
            LOGGER.warn("关闭 MQTT 发布端失败。", exception);
        }
    }

    private String buildBrokerUrl() {
        return "tcp://" + mqttProperties.host() + ":" + mqttProperties.port();
    }
}

package com.weaksignal.vpp.backend.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weaksignal.vpp.backend.application.TelemetryProcessingService;
import com.weaksignal.vpp.backend.application.ExperimentFaultObservationService;
import com.weaksignal.vpp.backend.application.DispatchService;
import com.weaksignal.vpp.backend.application.FaultIntelligenceService;
import com.weaksignal.vpp.backend.config.MqttProperties;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.FaultEventEntity;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.FaultEventRepository;
import com.weaksignal.vpp.backend.mqtt.dto.CommandAckMessage;
import com.weaksignal.vpp.backend.mqtt.dto.FaultReportMessage;
import com.weaksignal.vpp.backend.mqtt.dto.HeartbeatReportMessage;
import com.weaksignal.vpp.backend.mqtt.dto.TelemetryReportMessage;
import com.weaksignal.vpp.backend.mqtt.dto.ScenarioReportMessage;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.UUID;
import org.eclipse.paho.mqttv5.client.IMqttMessageListener;
import org.eclipse.paho.mqttv5.client.MqttAsyncClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttSubscription;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * MQTT 入站订阅监听器。
 */
@Component
public class MqttInboundListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(MqttInboundListener.class);

    private final MqttProperties mqttProperties;
    private final ObjectMapper objectMapper;
    private final TelemetryProcessingService telemetryProcessingService;
    private final DispatchService dispatchService;
    private final FaultIntelligenceService faultIntelligenceService;
    private final FaultEventRepository faultEventRepository;
    private final ExperimentFaultObservationService experimentFaultObservationService;
    private MqttAsyncClient mqttAsyncClient;

    /**
     * 注入入站监听依赖。
     */
    public MqttInboundListener(
            MqttProperties mqttProperties,
            ObjectMapper objectMapper,
            TelemetryProcessingService telemetryProcessingService,
            DispatchService dispatchService,
            FaultIntelligenceService faultIntelligenceService,
            FaultEventRepository faultEventRepository,
            ExperimentFaultObservationService experimentFaultObservationService
    ) {
        this.mqttProperties = mqttProperties;
        this.objectMapper = objectMapper;
        this.telemetryProcessingService = telemetryProcessingService;
        this.dispatchService = dispatchService;
        this.faultIntelligenceService = faultIntelligenceService;
        this.faultEventRepository = faultEventRepository;
        this.experimentFaultObservationService = experimentFaultObservationService;
    }

    /**
     * 初始化 MQTT 订阅。
     */
    @PostConstruct
    public void connectAndSubscribe() {
        try {
            mqttAsyncClient = new MqttAsyncClient(buildBrokerUrl(), mqttProperties.clientId(), new MemoryPersistence());
            MqttConnectionOptions options = new MqttConnectionOptions();
            options.setUserName(mqttProperties.username());
            options.setPassword(mqttProperties.password() == null ? null : mqttProperties.password().getBytes());
            options.setAutomaticReconnect(true);
            mqttAsyncClient.connect(options).waitForCompletion();
            MqttSubscription[] subscriptions = {
                    new MqttSubscription("vpp/+/telemetry/report", 1),
                    new MqttSubscription("vpp/+/heartbeat/report", 1),
                    new MqttSubscription("vpp/+/ack/report", 1),
                    new MqttSubscription("vpp/+/fault/report", 1),
                    new MqttSubscription("vpp/all/scenario/report", 1)
            };
            IMqttMessageListener[] messageListeners = {
                    (topic, message) -> handleTelemetry(message.getPayload()),
                    (topic, message) -> handleHeartbeat(message.getPayload()),
                    (topic, message) -> handleAck(message.getPayload()),
                    (topic, message) -> handleFault(message.getPayload()),
                    (topic, message) -> handleScenarioReport(message.getPayload())
            };
            mqttAsyncClient.subscribe(subscriptions, null, null, messageListeners, null).waitForCompletion();
            LOGGER.info("MQTT 入站订阅已建立。host={}, port={}", mqttProperties.host(), mqttProperties.port());
        } catch (MqttException exception) {
            LOGGER.warn("MQTT 入站订阅初始化失败。host={}, port={}", mqttProperties.host(), mqttProperties.port(), exception);
        }
    }

    private void handleTelemetry(byte[] payload) {
        try {
            TelemetryReportMessage message = objectMapper.readValue(payload, TelemetryReportMessage.class);
            telemetryProcessingService.handleTelemetry(message);
        } catch (Exception exception) {
            LOGGER.warn("遥测消息反序列化或处理失败，本条消息已跳过。", exception);
        }
    }

    private void handleHeartbeat(byte[] payload) {
        try {
            HeartbeatReportMessage message = objectMapper.readValue(payload, HeartbeatReportMessage.class);
            telemetryProcessingService.handleHeartbeat(message);
        } catch (Exception exception) {
            LOGGER.warn("心跳消息反序列化或处理失败，本条消息已跳过。", exception);
        }
    }

    private void handleAck(byte[] payload) {
        try {
            CommandAckMessage message = objectMapper.readValue(payload, CommandAckMessage.class);
            dispatchService.handleAcknowledgement(message);
            faultIntelligenceService.handleAcknowledgement(message);
        } catch (Exception exception) {
            LOGGER.warn("命令确认消息处理失败，本条消息已跳过。", exception);
        }
    }

    private void handleFault(byte[] payload) {
        try {
            FaultReportMessage message = objectMapper.readValue(payload, FaultReportMessage.class);
            FaultEventEntity entity = new FaultEventEntity();
            entity.setEventId(message.eventId().isBlank() ? UUID.randomUUID().toString() : message.eventId());
            entity.setStationId(message.stationId());
            entity.setFaultCode(message.faultCode());
            entity.setTitle(message.title());
            entity.setDescription(message.description());
            entity.setLevel(message.level());
            entity.setStatus("ACTIVE");
            entity.setOccurredAt(message.occurredAt());
            faultEventRepository.save(entity);
        } catch (Exception exception) {
            LOGGER.warn("故障消息处理失败，本条消息已跳过。", exception);
        }
    }

    private void handleScenarioReport(byte[] payload) {
        try {
            experimentFaultObservationService.handleScenarioReport(objectMapper.readValue(payload, ScenarioReportMessage.class));
        } catch (Exception exception) {
            LOGGER.warn("场景生命周期报告处理失败，本条消息已跳过。", exception);
        }
    }

    /**
     * 关闭 MQTT 订阅连接。
     */
    @PreDestroy
    public void disconnect() {
        if (mqttAsyncClient == null) {
            return;
        }
        try {
            mqttAsyncClient.disconnect();
        } catch (MqttException exception) {
            LOGGER.warn("关闭 MQTT 入站连接失败。", exception);
        }
    }

    private String buildBrokerUrl() {
        return "tcp://" + mqttProperties.host() + ":" + mqttProperties.port();
    }
}

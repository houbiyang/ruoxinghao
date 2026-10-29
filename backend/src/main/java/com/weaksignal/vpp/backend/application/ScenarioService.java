package com.weaksignal.vpp.backend.application;

import com.weaksignal.vpp.backend.api.ApiException;
import com.weaksignal.vpp.backend.api.dto.ScenarioActivationResponse;
import com.weaksignal.vpp.backend.api.dto.ScenarioStatusResponse;
import com.weaksignal.vpp.backend.domain.DemoScenario;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.ControlCommandEntity;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.ExperimentRunEntity;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.ControlCommandRepository;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.ExperimentRunRepository;
import com.weaksignal.vpp.backend.infrastructure.redis.RedisRealtimeStateStore;
import com.weaksignal.vpp.backend.mqtt.MqttGateway;
import com.weaksignal.vpp.backend.mqtt.dto.ScenarioApplyCommandMessage;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 场景管理服务。
 */
@Service
public class ScenarioService {

    private final RedisRealtimeStateStore redisRealtimeStateStore;
    private final ControlCommandRepository controlCommandRepository;
    private final ExperimentRunRepository experimentRunRepository;
    private final MqttGateway mqttGateway;
    private final Clock clock;

    /**
     * 注入场景管理依赖。
     *
     * @param redisRealtimeStateStore Redis 状态存储
     * @param controlCommandRepository 控制命令仓储
     * @param experimentRunRepository 实验运行仓储
     * @param mqttGateway MQTT 网关
     * @param clock 系统时钟
     */
    public ScenarioService(
            RedisRealtimeStateStore redisRealtimeStateStore,
            ControlCommandRepository controlCommandRepository,
            ExperimentRunRepository experimentRunRepository,
            MqttGateway mqttGateway,
            Clock clock
    ) {
        this.redisRealtimeStateStore = redisRealtimeStateStore;
        this.controlCommandRepository = controlCommandRepository;
        this.experimentRunRepository = experimentRunRepository;
        this.mqttGateway = mqttGateway;
        this.clock = clock;
    }

    /**
     * 查询当前场景。
     *
     * @return 当前场景信息
     */
    public ScenarioStatusResponse getCurrentScenario() {
        DemoScenario scenario = redisRealtimeStateStore.getActiveScenario().orElse(DemoScenario.STABLE);
        return new ScenarioStatusResponse(scenario, scenario.getControlMode());
    }

    /**
     * 激活指定场景。
     *
     * @param scenarioCode 场景编码
     * @return 激活结果
     */
    @Transactional
    public ScenarioActivationResponse activateScenario(String scenarioCode) {
        DemoScenario scenario;
        try {
            scenario = DemoScenario.fromValue(scenarioCode);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SCENARIO", "非法场景参数", java.util.Map.of("scenario", scenarioCode));
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        String commandId = UUID.randomUUID().toString();

        ControlCommandEntity commandEntity = new ControlCommandEntity();
        commandEntity.setCommandId(commandId);
        commandEntity.setStationId("all");
        commandEntity.setCommandType("scenario-apply");
        commandEntity.setCommandPayload("{\"scenario\":\"" + scenario.getValue() + "\"}");
        commandEntity.setSendTime(now);
        commandEntity.setExpireAt(now.plusMinutes(5));
        commandEntity.setStatus("DISPATCHED");
        commandEntity.setCreatedAt(now);
        controlCommandRepository.save(commandEntity);

        ExperimentRunEntity experimentRunEntity = new ExperimentRunEntity();
        experimentRunEntity.setRunId(UUID.randomUUID().toString());
        experimentRunEntity.setScenarioCode(scenario.getValue());
        experimentRunEntity.setStatus("RUNNING");
        experimentRunEntity.setStartedAt(now);
        experimentRunEntity.setResultSummary("场景切换已下发，等待模拟器回传状态变化。");
        experimentRunRepository.save(experimentRunEntity);

        redisRealtimeStateStore.saveActiveScenario(scenario);
        mqttGateway.publishGlobalScenario(new ScenarioApplyCommandMessage(commandId, scenario, now, "backend-api"));

        return new ScenarioActivationResponse(scenario, scenario.getControlMode(), now, commandId, "DISPATCHED");
    }
}


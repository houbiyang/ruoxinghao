package com.weaksignal.vpp.backend.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weaksignal.vpp.backend.api.ApiException;
import com.weaksignal.vpp.backend.api.dto.ExperimentStartRequest;
import com.weaksignal.vpp.backend.domain.DemoScenario;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.ExperimentRunEntity;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.ExperimentRunRepository;
import com.weaksignal.vpp.backend.infrastructure.redis.RedisRealtimeStateStore;
import com.weaksignal.vpp.backend.mqtt.MqttGateway;
import com.weaksignal.vpp.backend.mqtt.dto.ScenarioApplyCommandMessage;
import com.weaksignal.vpp.backend.mqtt.dto.DispatchControlMessage;
import com.weaksignal.vpp.backend.mqtt.dto.FaultActionCommandMessage;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

/**
 * 故障实验服务测试。
 */
class ExperimentServiceTest {

    @Test
    void shouldListStandardExperimentScenarios() {
        ExperimentService service = createService(repositoryProxy(Map.of()), new AtomicReference<>());

        assertEquals(10, service.listScenarios().size());
    }

    @Test
    void shouldStartExperimentAndPublishMqttCommand() {
        List<ExperimentRunEntity> savedRuns = new ArrayList<>();
        AtomicReference<ScenarioApplyCommandMessage> publishedMessage = new AtomicReference<>();
        ExperimentRunRepository repository = repositoryProxy(Map.of(
                "findFirstByStatusInOrderByStartedAtDesc", arguments -> Optional.empty(),
                "save", arguments -> {
                    savedRuns.add((ExperimentRunEntity) arguments[0]);
                    return arguments[0];
                }
        ));

        ExperimentService service = createService(repository, publishedMessage);
        var response = service.startExperiment("T1_LATENCY_JITTER", new ExperimentStartRequest(List.of("pv-gobi-01"), 120, "测试"));

        assertEquals("T1_LATENCY_JITTER", response.scenarioCode());
        assertEquals("INJECTING", response.status());
        assertEquals(1, savedRuns.size());
        assertEquals("START_EXPERIMENT", publishedMessage.get().commandType());
        assertEquals(DemoScenario.DEGRADED, publishedMessage.get().scenario());
        assertEquals(List.of("pv-gobi-01"), publishedMessage.get().targetStationIds());
        assertFalse(publishedMessage.get().automaticRecovery());
    }

    @Test
    void shouldKeepPhysicalFaultUntilBackendFinishesOrchestration() {
        AtomicReference<ScenarioApplyCommandMessage> publishedMessage = new AtomicReference<>();
        ExperimentRunRepository repository = repositoryProxy(Map.of(
                "findFirstByStatusInOrderByStartedAtDesc", arguments -> Optional.empty(),
                "save", arguments -> arguments[0]
        ));

        ExperimentService service = createService(repository, publishedMessage);
        service.startExperiment("ENV_WIND_CALM", new ExperimentStartRequest(
                List.of("wind-ridge-02"),
                60,
                Map.of("windSpeed", 0.8, "turbulence", 0.06),
                3,
                true,
                "验证后端统一恢复"
        ));

        assertFalse(publishedMessage.get().automaticRecovery());
    }

    @Test
    void shouldRejectDuplicateRunningExperiment() {
        ExperimentRunEntity existing = new ExperimentRunEntity();
        existing.setRunId("run-1");
        existing.setScenarioCode("T1_LATENCY_JITTER");
        existing.setStatus("INJECTING");
        ExperimentRunRepository repository = repositoryProxy(Map.of(
                "findFirstByStatusInOrderByStartedAtDesc", arguments -> Optional.of(existing)
        ));

        ExperimentService service = createService(repository, new AtomicReference<>());

        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.startExperiment("T2_RANDOM_PACKET_LOSS", new ExperimentStartRequest(null, null, null))
        );
        assertEquals("EXPERIMENT_ALREADY_RUNNING", exception.getCode());
    }

    @Test
    void shouldStopRunningExperiment() {
        ExperimentRunEntity existing = new ExperimentRunEntity();
        existing.setRunId("run-1");
        existing.setScenarioCode("T1_LATENCY_JITTER");
        existing.setStatus("RUNNING");
        existing.setStartedAt(OffsetDateTime.parse("2026-06-30T12:00:00+08:00"));
        existing.setResultSummary("已启动");
        AtomicReference<ScenarioApplyCommandMessage> publishedMessage = new AtomicReference<>();
        ExperimentRunRepository repository = repositoryProxy(Map.of(
                "findByRunId", arguments -> Optional.of(existing),
                "save", arguments -> arguments[0]
        ));

        ExperimentService service = createService(repository, publishedMessage);
        var response = service.stopExperiment("run-1");

        assertEquals("STOPPED", response.status());
        assertEquals("STOP_EXPERIMENT", publishedMessage.get().commandType());
        assertEquals(DemoScenario.STABLE, publishedMessage.get().scenario());
    }

    @Test
    void shouldReturnNotFoundWhenStoppingMissingRun() {
        ExperimentRunRepository repository = repositoryProxy(Map.of("findByRunId", arguments -> Optional.empty()));
        ExperimentService service = createService(repository, new AtomicReference<>());

        ApiException exception = assertThrows(ApiException.class, () -> service.stopExperiment("missing"));
        assertEquals("EXPERIMENT_RUN_NOT_FOUND", exception.getCode());
    }

    @Test
    void shouldDeleteStoppedExperimentRunOnly() {
        ExperimentRunEntity existing = new ExperimentRunEntity();
        existing.setRunId("run-1");
        existing.setScenarioCode("T1_LATENCY_JITTER");
        existing.setStatus("STOPPED");
        AtomicReference<ExperimentRunEntity> deletedRun = new AtomicReference<>();
        ExperimentRunRepository repository = repositoryProxy(Map.of(
                "findByRunId", arguments -> Optional.of(existing),
                "delete", arguments -> {
                    deletedRun.set((ExperimentRunEntity) arguments[0]);
                    return null;
                }
        ));

        var response = createService(repository, new AtomicReference<>()).deleteExperiment("run-1");

        assertEquals("run-1", response.runId());
        assertEquals(existing, deletedRun.get());
    }

    @Test
    void shouldRejectDeletingRunningExperiment() {
        ExperimentRunEntity existing = new ExperimentRunEntity();
        existing.setRunId("run-1");
        existing.setScenarioCode("T1_LATENCY_JITTER");
        existing.setStatus("RUNNING");
        ExperimentRunRepository repository = repositoryProxy(Map.of("findByRunId", arguments -> Optional.of(existing)));

        ApiException exception = assertThrows(
                ApiException.class,
                () -> createService(repository, new AtomicReference<>()).deleteExperiment("run-1")
        );

        assertEquals("EXPERIMENT_RUNNING_CANNOT_DELETE", exception.getCode());
    }

    private ExperimentService createService(
            ExperimentRunRepository repository,
            AtomicReference<ScenarioApplyCommandMessage> publishedMessage
    ) {
        return new ExperimentService(
                repository,
                new RedisRealtimeStateStore(null, new ObjectMapper().findAndRegisterModules(), new com.weaksignal.vpp.backend.config.RedisCacheProperties(45, 20)) {
                    @Override
                    public void saveActiveScenario(DemoScenario scenario) {
                    }
                },
                new MqttGateway() {
                    @Override
                    public void publishGlobalScenario(ScenarioApplyCommandMessage message) {
                        publishedMessage.set(message);
                    }

                    @Override
                    public boolean publishDispatchControl(DispatchControlMessage message) {
                        return true;
                    }

                    @Override
                    public boolean publishFaultAction(FaultActionCommandMessage message) {
                        return true;
                    }
                },
                new ObjectMapper().findAndRegisterModules(),
                Clock.fixed(OffsetDateTime.parse("2026-06-30T12:00:00+08:00").toInstant(), ZoneId.of("Asia/Shanghai"))
        );
    }

    @SuppressWarnings("unchecked")
    private ExperimentRunRepository repositoryProxy(Map<String, java.util.function.Function<Object[], Object>> handlers) {
        return (ExperimentRunRepository) Proxy.newProxyInstance(
                ExperimentRunRepository.class.getClassLoader(),
                new Class<?>[]{ExperimentRunRepository.class},
                (proxy, method, args) -> {
                    java.util.function.Function<Object[], Object> handler = handlers.get(method.getName());
                    if (handler != null) {
                        return handler.apply(args == null ? new Object[0] : args);
                    }
                    if (method.getName().equals("findByScenarioCodeStartingWithOrderByStartedAtDesc")) {
                        Pageable pageable = (Pageable) args[1];
                        assertTrue(pageable.getPageSize() > 0);
                        return List.of();
                    }
                    if (method.getName().equals("toString")) {
                        return "ExperimentRunRepositoryProxy";
                    }
                    return null;
                }
        );
    }
}

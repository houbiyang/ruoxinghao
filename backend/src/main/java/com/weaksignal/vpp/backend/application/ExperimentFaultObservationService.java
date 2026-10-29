package com.weaksignal.vpp.backend.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weaksignal.vpp.backend.domain.DemoScenario;
import com.weaksignal.vpp.backend.domain.ExperimentScenarioDefinition;
import com.weaksignal.vpp.backend.domain.LinkState;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.ExperimentRunEntity;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.ExperimentRunRepository;
import com.weaksignal.vpp.backend.mqtt.MqttGateway;
import com.weaksignal.vpp.backend.mqtt.dto.ScenarioApplyCommandMessage;
import com.weaksignal.vpp.backend.mqtt.dto.ScenarioReportMessage;
import com.weaksignal.vpp.backend.mqtt.dto.TelemetryReportMessage;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 实验故障证据和恢复证据服务。
 */
@Service
public class ExperimentFaultObservationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExperimentFaultObservationService.class);
    private static final String EXPERIMENT_PREFIX = "T";
    private static final String INJECTING = "INJECTING";
    private static final String FAULT_OBSERVED = "FAULT_OBSERVED";
    private static final String RECOVERING = "RECOVERING";
    private static final String COMPLETED = "COMPLETED";
    private static final String INJECTION_NOT_OBSERVED = "INJECTION_NOT_OBSERVED";
    private static final int LATENCY_REQUIRED_CONSECUTIVE_SAMPLES = 3;
    private static final int PACKET_LOSS_WINDOW_EXPECTED_MESSAGES = 8;
    private static final long FAULT_INJECTION_STAGE_SECONDS = 60;
    private static final long ORCHESTRATION_STAGE_SECONDS = 60;
    private static final long RECOVERY_REPORT_FALLBACK_SECONDS = 10;
    private static final TypeReference<LinkedHashMap<String, Object>> SUMMARY_TYPE = new TypeReference<>() { };

    private final ExperimentRunRepository experimentRunRepository;
    private final ExperimentClosedLoopOrchestrationService closedLoopOrchestrationService;
    private final MqttGateway mqttGateway;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    @Value("${app.experiment.required-fault-evidence-samples:3}")
    private int requiredFaultEvidenceSamples = 3;
    @Value("${app.experiment.recovery-timeout-seconds:120}")
    private long recoveryTimeoutSeconds = 120;

    /**
     * 注入故障观测服务依赖。
     *
     * @param experimentRunRepository 实验运行仓储
     * @param closedLoopOrchestrationService 闭环编排服务
     * @param mqttGateway MQTT 网关
     * @param objectMapper JSON 组件
     * @param clock 系统时钟
     */
    public ExperimentFaultObservationService(
            ExperimentRunRepository experimentRunRepository,
            ExperimentClosedLoopOrchestrationService closedLoopOrchestrationService,
            MqttGateway mqttGateway,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.experimentRunRepository = experimentRunRepository;
        this.closedLoopOrchestrationService = closedLoopOrchestrationService;
        this.mqttGateway = mqttGateway;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * 接收链路状态机产生的真实状态变化。
     *
     * @param stationId 场站标识
     * @param linkState 实际链路状态
     * @param occurredAt 状态变化时间
     */
    @Transactional
    public void observeLinkState(String stationId, LinkState linkState, OffsetDateTime occurredAt) {
        experimentRunRepository.findByStatusIn(
                List.of(INJECTING, RECOVERING)
        ).forEach(runEntity -> observeRunLinkState(runEntity, stationId, linkState, occurredAt));
    }

    /**
     * 根据实验关联遥测判定非失联类故障是否已实际生效。
     * 仅接受与当前运行标识和故障编码完全一致的目标场站遥测，避免用时间窗口或原始载荷猜测证据。
     *
     * @param message 模拟器上报的遥测消息
     * @param evaluatedLinkState 后端判定的实际链路状态
     * @param receivedAt 后端接收时间
     */
    @Transactional
    public void observeTelemetry(TelemetryReportMessage message, LinkState evaluatedLinkState, OffsetDateTime receivedAt) {
        observeLocalAutonomyEvidence(message, receivedAt);
        if (evaluatedLinkState == LinkState.S0) {
            experimentRunRepository.findByStatusIn(List.of(RECOVERING))
                    .forEach(runEntity -> observeRunLinkState(runEntity, message.stationId(), LinkState.S0, receivedAt));
        }
        if (message.experimentRunId() == null || message.activeFaultCode() == null
                || message.sequenceNumber() <= 0 || message.deviceTimestamp() == null) {
            return;
        }
        ExperimentRunEntity runEntity = experimentRunRepository.findByRunId(message.experimentRunId()).orElse(null);
        if (runEntity == null || !INJECTING.equals(runEntity.getStatus())
                || !runEntity.getScenarioCode().equals(message.activeFaultCode())) {
            return;
        }
        Map<String, Object> summary = readSummary(runEntity);
        if (!stringList(summary.get("targetStationIds")).contains(message.stationId())) {
            return;
        }
        if ("COM_LATENCY".equals(runEntity.getScenarioCode())) {
            observeLatencyEvidence(runEntity, summary, message, receivedAt);
            return;
        }
        if ("COM_PACKET_LOSS".equals(runEntity.getScenarioCode())) {
            observePacketLossEvidence(runEntity, summary, message, receivedAt);
            return;
        }
        if (runEntity.getScenarioCode().startsWith("ENV_") || runEntity.getScenarioCode().startsWith("DEV_")) {
            observePhysicalFaultEvidence(runEntity, summary, message, receivedAt);
            return;
        }
        if (!matchesTelemetryEvidence(runEntity.getScenarioCode(), message, evaluatedLinkState)) {
            return;
        }
        Map<String, Object> observedValues = objectMap(summary.get("observedFaultValues"));
        observedValues.put(message.stationId(), Map.of(
                "activeFaultCode", message.activeFaultCode(),
                "observedAt", receivedAt.toString(),
                "latency", message.latency(),
                "packetLossRate", message.packetLossRate(),
                "availableCapacity", message.availableCapacity()
        ));
        summary.put("observedFaultValues", observedValues);
        if (observedValues.keySet().containsAll(stringList(summary.get("targetStationIds")))) {
            markFaultObserved(runEntity, summary, receivedAt);
            return;
        }
        saveSummary(runEntity, summary);
    }

    private void observeLocalAutonomyEvidence(TelemetryReportMessage message, OffsetDateTime receivedAt) {
        if (message.experimentRunId() == null || message.experimentRunId().isBlank()
                || message.metrics() == null
                || !message.metrics().containsKey("localAutonomyReferencePower")) {
            return;
        }
        ExperimentRunEntity runEntity = experimentRunRepository.findByRunId(message.experimentRunId()).orElse(null);
        if (runEntity == null || !"COM_OFFLINE".equals(runEntity.getScenarioCode())
                || (!RECOVERING.equals(runEntity.getStatus()) && !FAULT_OBSERVED.equals(runEntity.getStatus())
                && !"ORCHESTRATING".equals(runEntity.getStatus()))) {
            return;
        }
        Map<String, Object> summary = readSummary(runEntity);
        if (!stringList(summary.get("targetStationIds")).contains(message.stationId())) {
            return;
        }
        Map<String, Object> evidenceByStation = objectMap(summary.get("localAutonomyEvidence"));
        Map<String, Object> stationEvidence = new LinkedHashMap<>();
        stationEvidence.put("observedAt", receivedAt.toString());
        stationEvidence.put("referencePowerKw", message.metrics().get("localAutonomyReferencePower"));
        stationEvidence.put("actualPowerKw", message.metrics().get("localAutonomyActualPower"));
        stationEvidence.put("actionCount", message.metrics().get("localAutonomyActionCount"));
        stationEvidence.put("constraintViolationCount",
                message.metrics().get("localAutonomyConstraintViolationCount"));
        stationEvidence.put("recoveryStableSampleCount", message.metrics().get("recoveryStableSampleCount"));
        stationEvidence.put("rejoinedCommunicationGraph",
                metricAtLeast(message.metrics(), "rejoinedCommunicationGraph", 1.0));
        evidenceByStation.put(message.stationId(), stationEvidence);
        summary.put("localAutonomyEvidence", evidenceByStation);
        saveSummary(runEntity, summary);
    }

    /**
     * 按环境场景规则确认实际环境测量与功率降额同时成立。
     *
     * @param runEntity 实验运行实体
     * @param summary 实验摘要
     * @param message 实际遥测
     * @param receivedAt 接收时间
     */
    private void observePhysicalFaultEvidence(
            ExperimentRunEntity runEntity,
            Map<String, Object> summary,
            TelemetryReportMessage message,
            OffsetDateTime receivedAt
    ) {
        Map<String, Double> metrics = message.metrics();
        Map<String, Object> progressByStation = objectMap(summary.get("physicalEvidenceProgress"));
        Map<String, Object> stationProgress = objectMap(progressByStation.get(message.stationId()));
        int consecutiveCount = integerValue(stationProgress.get("consecutiveCount")) == null
                ? 0 : integerValue(stationProgress.get("consecutiveCount"));
        if (metrics == null || !matchesPhysicalFaultEvidence(runEntity.getScenarioCode(), summary, message, metrics)) {
            stationProgress.put("consecutiveCount", 0);
            stationProgress.put("lastObservedAt", receivedAt.toString());
            progressByStation.put(message.stationId(), stationProgress);
            summary.put("physicalEvidenceProgress", progressByStation);
            saveSummary(runEntity, summary);
            return;
        }
        consecutiveCount += 1;
        stationProgress.put("consecutiveCount", consecutiveCount);
        stationProgress.put("lastSequenceNumber", message.sequenceNumber());
        stationProgress.put("lastObservedAt", receivedAt.toString());
        progressByStation.put(message.stationId(), stationProgress);
        summary.put("physicalEvidenceProgress", progressByStation);
        if (consecutiveCount < requiredFaultEvidenceSamples) {
            saveSummary(runEntity, summary);
            return;
        }
        Map<String, Object> observedValues = objectMap(summary.get("observedFaultValues"));
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("observedAt", receivedAt.toString());
        evidence.put("activePower", message.activePower());
        evidence.put("availableCapacity", message.availableCapacity());
        evidence.put("environmentMetrics", metrics);
        evidence.put("consecutiveCount", consecutiveCount);
        observedValues.put(message.stationId(), evidence);
        summary.put("observedFaultValues", observedValues);
        if (observedValues.keySet().containsAll(stringList(summary.get("targetStationIds")))) {
            markFaultObserved(runEntity, summary, receivedAt);
            return;
        }
        saveSummary(runEntity, summary);
    }

    /**
     * 记录通信时延的连续遥测证据。
     *
     * @param runEntity 实验运行实体
     * @param summary 实验摘要
     * @param message 遥测消息
     * @param receivedAt 接收时间
     */
    private void observeLatencyEvidence(
            ExperimentRunEntity runEntity,
            Map<String, Object> summary,
            TelemetryReportMessage message,
            OffsetDateTime receivedAt
    ) {
        double threshold = faultParameter(summary, "latency", 800.0);
        Map<String, Object> stationProgress = objectMap(objectMap(summary.get("latencyEvidenceProgress")).get(message.stationId()));
        int consecutiveCount = integerValue(stationProgress.get("consecutiveCount")) == null ? 0 : integerValue(stationProgress.get("consecutiveCount"));
        Integer previousLatency = integerValue(stationProgress.get("previousLatency"));
        double jitter = previousLatency == null ? 0.0 : Math.abs(message.latency() - previousLatency);
        List<Object> evidence = objectList(summary.get("latencyEvidence"));
        evidence.add(Map.of(
                "stationId", message.stationId(),
                "sequenceNumber", message.sequenceNumber(),
                "actualLatency", message.latency(),
                "actualJitter", jitter,
                "observedAt", receivedAt.toString(),
                "threshold", threshold
        ));
        if (message.latency() >= threshold) {
            consecutiveCount += 1;
        } else {
            consecutiveCount = 0;
        }
        stationProgress.put("consecutiveCount", consecutiveCount);
        stationProgress.put("previousLatency", message.latency());
        stationProgress.put("lastObservedAt", receivedAt.toString());
        Map<String, Object> progressByStation = objectMap(summary.get("latencyEvidenceProgress"));
        progressByStation.put(message.stationId(), stationProgress);
        summary.put("latencyEvidence", evidence);
        summary.put("latencyEvidenceProgress", progressByStation);
        if (consecutiveCount >= LATENCY_REQUIRED_CONSECUTIVE_SAMPLES) {
            Map<String, Object> observedValues = objectMap(summary.get("observedFaultValues"));
            observedValues.put(message.stationId(), Map.of(
                    "consecutiveCount", consecutiveCount,
                    "actualLatency", message.latency(),
                    "actualJitter", jitter,
                    "observedAt", receivedAt.toString(),
                    "threshold", threshold
            ));
            summary.put("observedFaultValues", observedValues);
            markFaultObserved(runEntity, summary, receivedAt);
            return;
        }
        saveSummary(runEntity, summary);
    }

    /**
     * 根据目标场站遥测序列缺口统计固定窗口丢包率。
     *
     * @param runEntity 实验运行实体
     * @param summary 实验摘要
     * @param message 遥测消息
     * @param receivedAt 接收时间
     */
    private void observePacketLossEvidence(
            ExperimentRunEntity runEntity,
            Map<String, Object> summary,
            TelemetryReportMessage message,
            OffsetDateTime receivedAt
    ) {
        Map<String, Object> progressByStation = objectMap(summary.get("packetLossProgress"));
        Map<String, Object> progress = objectMap(progressByStation.get(message.stationId()));
        Long previousSequence = longValue(progress.get("previousSequence"));
        long expectedIncrement = previousSequence == null
                ? Math.max(1, Math.round(faultParameter(summary, "burstLength", 1.0)) + 1)
                : Math.max(1, message.sequenceNumber() - previousSequence);
        int expectedCount = integerValue(progress.get("expectedCount")) == null ? 0 : integerValue(progress.get("expectedCount"));
        int receivedCount = integerValue(progress.get("receivedCount")) == null ? 0 : integerValue(progress.get("receivedCount"));
        expectedCount += Math.toIntExact(expectedIncrement);
        receivedCount += 1;
        progress.put("windowStartedAt", progress.getOrDefault("windowStartedAt", receivedAt.toString()));
        progress.put("previousSequence", message.sequenceNumber());
        progress.put("expectedCount", expectedCount);
        progress.put("receivedCount", receivedCount);
        progressByStation.put(message.stationId(), progress);
        summary.put("packetLossProgress", progressByStation);
        if (expectedCount < PACKET_LOSS_WINDOW_EXPECTED_MESSAGES) {
            saveSummary(runEntity, summary);
            return;
        }
        double actualPacketLossRate = (expectedCount - receivedCount) * 100.0 / expectedCount;
        int sequenceGapCount = expectedCount - receivedCount;
        List<Object> windows = objectList(summary.get("packetLossWindows"));
        windows.add(Map.of(
                "stationId", message.stationId(),
                "windowStartedAt", progress.get("windowStartedAt"),
                "windowEndedAt", receivedAt.toString(),
                "expectedCount", expectedCount,
                "receivedCount", receivedCount,
                "sequenceGapCount", sequenceGapCount,
                "actualPacketLossRate", actualPacketLossRate
        ));
        summary.put("packetLossWindows", windows);
        double threshold = faultParameter(summary, "packetLossRate", 35.0);
        if (actualPacketLossRate >= threshold) {
            Map<String, Object> observedValues = objectMap(summary.get("observedFaultValues"));
            observedValues.put(message.stationId(), Map.of(
                    "windowStartedAt", progress.get("windowStartedAt"),
                    "windowEndedAt", receivedAt.toString(),
                    "expectedCount", expectedCount,
                    "receivedCount", receivedCount,
                    "sequenceGapCount", sequenceGapCount,
                    "actualPacketLossRate", actualPacketLossRate,
                    "threshold", threshold
            ));
            summary.put("observedFaultValues", observedValues);
            markFaultObserved(runEntity, summary, receivedAt);
            return;
        }
        progressByStation.put(message.stationId(), new LinkedHashMap<>(Map.of(
                "previousSequence", message.sequenceNumber(),
                "expectedCount", 0,
                "receivedCount", 0,
                "windowStartedAt", receivedAt.toString()
        )));
        summary.put("packetLossProgress", progressByStation);
        saveSummary(runEntity, summary);
    }

    /**
     * 接收模拟器恢复报告。报告仅作为恢复证据之一，不能直接完成实验。
     *
     * @param message 模拟器场景报告
     */
    @Transactional
    public void handleScenarioReport(ScenarioReportMessage message) {
        ExperimentRunEntity runEntity = experimentRunRepository.findByRunId(message.experimentRunId()).orElse(null);
        if (runEntity == null || !runEntity.getScenarioCode().equals(message.scenarioCode())) {
            LOGGER.warn("忽略无效场景报告。runId={}, scenarioCode={}", message.experimentRunId(), message.scenarioCode());
            return;
        }
        Map<String, Object> summary = readSummary(runEntity);
        if (!sameTargets(summary, message.targetStationIds())) {
            LOGGER.warn("忽略过期或目标不一致的场景报告。runId={}, status={}", message.experimentRunId(), runEntity.getStatus());
            return;
        }
        if (!RECOVERING.equals(runEntity.getStatus()) && !FAULT_OBSERVED.equals(runEntity.getStatus())
                && !"ORCHESTRATING".equals(runEntity.getStatus())) {
            LOGGER.warn("忽略非恢复流程中的场景报告。runId={}, status={}", message.experimentRunId(), runEntity.getStatus());
            return;
        }
        String previousStatus = stringValue(summary.get("recoveryReportStatus"));
        if ("RECOVERED".equals(previousStatus) && "RECOVERED".equals(message.status())) {
            return;
        }
        summary.put("recoveryReportStatus", message.status());
        summary.put("recoveryReportAt", message.reportedAt());
        summary.put("recoveryReportFailureReason", message.failureReason());
        completeIfRecoveryConfirmed(runEntity, summary, message.reportedAt());
        saveSummary(runEntity, summary);
    }

    /**
     * 由自动恢复调度或人工停止请求进入统一恢复流程。
     *
     * @param runId 实验运行标识
     * @return 是否已进入恢复流程
     */
    @Transactional
    public boolean beginRecovery(String runId) {
        return beginRecovery(runId, false);
    }

    /**
     * 进入统一恢复流程，并区分自动恢复与用户主动停止。
     *
     * <p>用户主动停止采用幂等语义：实验已经处于恢复中时仍会重新发布恢复命令；
     * 如果恢复报告与目标场站正常状态均已到达，则直接结束为已停止。</p>
     *
     * @param runId 实验运行标识
     * @param manualStopRequested 是否由用户主动停止触发
     * @return 是否已接受恢复或停止请求
     */
    @Transactional
    public boolean beginRecovery(String runId, boolean manualStopRequested) {
        ExperimentRunEntity runEntity = experimentRunRepository.findByRunId(runId).orElse(null);
        if (runEntity == null || isTerminal(runEntity.getStatus())) {
            return false;
        }
        Map<String, Object> summary = readSummary(runEntity);
        List<String> targetStationIds = stringList(summary.get("targetStationIds"));
        if (RECOVERING.equals(runEntity.getStatus())) {
            if (!manualStopRequested) {
                return false;
            }
            summary.put("manualStopRequested", true);
            completeIfRecoveryConfirmed(runEntity, summary, OffsetDateTime.now(clock));
            saveSummary(runEntity, summary);
            publishRecoveryCommand(runEntity, targetStationIds);
            return true;
        }
        boolean faultEvidenceConfirmed = FAULT_OBSERVED.equals(runEntity.getStatus())
                || FAULT_OBSERVED.equals(stringValue(summary.get("evidenceStatus")))
                || summary.get("faultObservedAt") != null;
        runEntity.setStatus(RECOVERING);
        summary.put("lifecycleStatus", RECOVERING);
        summary.put("faultEvidenceConfirmed", faultEvidenceConfirmed);
        summary.put("recoveryStartedAt", OffsetDateTime.now(clock));
        summary.put("recoveryReportStatus", summary.get("recoveryReportStatus"));
        summary.put("recoveredLinkStates", objectMap(summary.get("recoveredLinkStates")));
        summary.put("recoveryLinkStateHistory", new ArrayList<>());
        summary.put("manualStopRequested", manualStopRequested);
        completeIfRecoveryConfirmed(runEntity, summary, OffsetDateTime.now(clock));
        saveSummary(runEntity, summary);
        publishRecoveryCommand(runEntity, targetStationIds);
        return true;
    }

    /**
     * 向模拟器发布恢复稳定场景命令。
     *
     * @param runEntity 实验运行实体
     * @param targetStationIds 目标场站标识
     */
    private void publishRecoveryCommand(ExperimentRunEntity runEntity, List<String> targetStationIds) {
        mqttGateway.publishGlobalScenario(new ScenarioApplyCommandMessage(
                UUID.randomUUID().toString(), DemoScenario.STABLE, OffsetDateTime.now(clock), "backend-experiment",
                "STOP_EXPERIMENT", runEntity.getScenarioCode(), runEntity.getRunId(), targetStationIds,
                Map.of(), null, null, true
        ));
    }

    /**
     * 在恢复报告与全部目标场站 S0 均到达时完成实验。
     *
     * @param runEntity 实验运行实体
     * @param summary 实验摘要
     * @param completedAt 完成时间
     */
    private void completeIfRecoveryConfirmed(
            ExperimentRunEntity runEntity,
            Map<String, Object> summary,
            OffsetDateTime completedAt
    ) {
        String recoveryReportStatus = stringValue(summary.get("recoveryReportStatus"));
        if (!RECOVERING.equals(runEntity.getStatus())
                || !Boolean.TRUE.equals(summary.get("faultEvidenceConfirmed"))
                || !containsAllState(objectMap(summary.get("recoveredLinkStates")), stringList(summary.get("targetStationIds")), LinkState.S0)) {
            return;
        }
        if (!"RECOVERED".equals(recoveryReportStatus)) {
            return;
        }
        if ("COM_OFFLINE".equals(runEntity.getScenarioCode())
                && !containsAllRejoinedStations(
                objectMap(summary.get("localAutonomyEvidence")),
                stringList(summary.get("targetStationIds")))) {
            return;
        }
        if (Boolean.TRUE.equals(summary.get("manualStopRequested"))) {
            runEntity.setStatus("STOPPED");
            runEntity.setEndedAt(completedAt);
            summary.put("lifecycleStatus", "STOPPED");
            summary.put("recoveredAt", completedAt);
            return;
        }
        if ("FAILED".equals(stringValue(summary.get("orchestrationStatus")))) {
            String orchestrationFailureReason = stringValue(summary.get("failureReason"));
            markRecoveryFailed(
                    runEntity,
                    summary,
                    completedAt,
                    orchestrationFailureReason == null || orchestrationFailureReason.isBlank()
                            ? "闭环重调度执行失败"
                            : orchestrationFailureReason
            );
            return;
        }
        String convergenceStatus = stringValue(summary.get("convergenceStatus"));
        if (Boolean.FALSE.equals(summary.get("safetyGatePassed"))) {
            markRecoveryFailed(runEntity, summary, completedAt, "安全门未通过，未下发控制命令");
            return;
        }
        if ("FAILED".equals(convergenceStatus) || "CONVERGENCE_TIMEOUT".equals(convergenceStatus)) {
            markRecoveryFailed(runEntity, summary, completedAt, "调度控制未达到可控范围：" + convergenceStatus);
            return;
        }
        if (!"TARGET_REACHED".equals(convergenceStatus)) {
            return;
        }
        runEntity.setStatus(COMPLETED);
        runEntity.setEndedAt(completedAt);
        summary.put("lifecycleStatus", COMPLETED);
        summary.put("recoveredAt", completedAt);
    }

    private boolean containsAllRejoinedStations(
            Map<String, Object> evidenceByStation,
            List<String> targetStationIds
    ) {
        return targetStationIds.stream().allMatch(stationId -> Boolean.TRUE.equals(
                objectMap(evidenceByStation.get(stationId)).get("rejoinedCommunicationGraph")
        ));
    }

    /**
     * 在物理恢复已确认但控制闭环失败时写入真实失败终态。
     *
     * @param runEntity 实验运行实体
     * @param summary 实验摘要
     * @param failedAt 失败确认时间
     * @param failureReason 失败原因
     */
    private void markRecoveryFailed(
            ExperimentRunEntity runEntity,
            Map<String, Object> summary,
            OffsetDateTime failedAt,
            String failureReason
    ) {
        runEntity.setStatus("FAILED");
        runEntity.setEndedAt(failedAt);
        summary.put("lifecycleStatus", "FAILED");
        summary.put("failureReason", failureReason);
        summary.put("recoveredAt", failedAt);
    }

    /**
     * 定时检查注入证据截止和自动恢复时点。
     */
    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void inspectLifecycle() {
        OffsetDateTime now = OffsetDateTime.now(clock);
        experimentRunRepository.findByStatusIn(List.of(INJECTING, FAULT_OBSERVED, RECOVERING))
                .forEach(runEntity -> inspectRun(runEntity, now));
    }

    private void observeRunLinkState(ExperimentRunEntity runEntity, String stationId, LinkState linkState, OffsetDateTime occurredAt) {
        Map<String, Object> summary = readSummary(runEntity);
        List<String> targets = stringList(summary.get("targetStationIds"));
        if (!targets.contains(stationId)) {
            return;
        }
        if (INJECTING.equals(runEntity.getStatus()) && "COM_OFFLINE".equals(runEntity.getScenarioCode()) && linkState == LinkState.S3) {
            Map<String, Object> observedStates = objectMap(summary.get("observedLinkStates"));
            observedStates.put(stationId, Map.of("linkState", LinkState.S3.name(), "observedAt", occurredAt.toString()));
            summary.put("observedLinkStates", observedStates);
            if (containsAllState(observedStates, targets, LinkState.S3)) {
                summary.put("observedFaultValues", observedStates);
                markFaultObserved(runEntity, summary, occurredAt);
                return;
            }
            saveSummary(runEntity, summary);
            return;
        }
        if (RECOVERING.equals(runEntity.getStatus())) {
            List<Object> recoveryHistory = objectList(summary.get("recoveryLinkStateHistory"));
            recoveryHistory.add(Map.of("stationId", stationId, "linkState", linkState.name(), "observedAt", occurredAt.toString()));
            summary.put("recoveryLinkStateHistory", recoveryHistory);
        }
        if (RECOVERING.equals(runEntity.getStatus())) {
            Map<String, Object> progressByStation = objectMap(summary.get("recoveryStableProgress"));
            Map<String, Object> stationProgress = objectMap(progressByStation.get(stationId));
            int consecutiveCount = integerValue(stationProgress.get("consecutiveCount")) == null
                    ? 0 : integerValue(stationProgress.get("consecutiveCount"));
            consecutiveCount = linkState == LinkState.S0 ? consecutiveCount + 1 : 0;
            stationProgress.put("consecutiveCount", consecutiveCount);
            stationProgress.put("lastObservedAt", occurredAt.toString());
            progressByStation.put(stationId, stationProgress);
            summary.put("recoveryStableProgress", progressByStation);
        }
        int stableSampleCount = integerValue(objectMap(objectMap(summary.get("recoveryStableProgress"))
                .get(stationId)).get("consecutiveCount")) == null ? 0
                : integerValue(objectMap(objectMap(summary.get("recoveryStableProgress"))
                .get(stationId)).get("consecutiveCount"));
        if (RECOVERING.equals(runEntity.getStatus()) && linkState == LinkState.S0
                && stableSampleCount >= requiredFaultEvidenceSamples) {
            Map<String, Object> recoveredStates = objectMap(summary.get("recoveredLinkStates"));
            recoveredStates.put(stationId, Map.of(
                    "linkState", LinkState.S0.name(),
                    "observedAt", occurredAt.toString(),
                    "consecutiveCount", stableSampleCount
            ));
            summary.put("recoveredLinkStates", recoveredStates);
            confirmRecoveryFromStableTelemetry(summary, occurredAt);
            completeIfRecoveryConfirmed(runEntity, summary, occurredAt);
        }
        if (RECOVERING.equals(runEntity.getStatus()) || COMPLETED.equals(runEntity.getStatus())
                || "FAILED".equals(runEntity.getStatus())) {
            saveSummary(runEntity, summary);
        }
    }

    /**
     * 模拟器恢复报告缺失时，使用恢复命令发出后的持续 S0 遥测作为物理恢复兜底证据。
     *
     * @param summary 实验摘要
     * @param observedAt 稳定遥测观测时间
     */
    private void confirmRecoveryFromStableTelemetry(Map<String, Object> summary, OffsetDateTime observedAt) {
        if (summary.get("recoveryReportStatus") != null) {
            return;
        }
        OffsetDateTime recoveryStartedAt = offsetDateTime(summary.get("recoveryStartedAt"));
        if (recoveryStartedAt == null
                || Duration.between(recoveryStartedAt, observedAt).getSeconds() < RECOVERY_REPORT_FALLBACK_SECONDS) {
            return;
        }
        summary.put("recoveryReportStatus", "RECOVERED");
        summary.put("recoveryReportAt", observedAt);
        summary.put("recoveryEvidenceSource", "STABLE_TELEMETRY_FALLBACK");
        summary.put("recoveryReportFailureReason", null);
    }

    private boolean matchesTelemetryEvidence(
            String scenarioCode,
            TelemetryReportMessage message,
            LinkState evaluatedLinkState
    ) {
        return switch (scenarioCode) {
            case "T0_STABLE_BASELINE" -> evaluatedLinkState == LinkState.S0;
            case "COM_PACKET_LOSS" -> message.packetLossRate() > 0 && evaluatedLinkState != LinkState.S0;
            case "COM_LATENCY" -> message.latency() > 0 && evaluatedLinkState != LinkState.S0;
            case "COM_OFFLINE" -> false;
            case "ENV_WIND_CALM", "ENV_OVERCAST", "ENV_HEAT", "ENV_DUST",
                    "DEV_INVERTER", "DEV_STORAGE" -> message.metrics() != null && !message.metrics().isEmpty();
            default -> evaluatedLinkState != LinkState.S0 || (message.metrics() != null && !message.metrics().isEmpty());
        };
    }

    /**
     * 判断四种环境故障是否满足真实测量与功率降额条件。
     *
     * @param scenarioCode 场景编码
     * @param summary 实验摘要
     * @param message 遥测消息
     * @param metrics 环境测量
     * @return 满足环境故障证据时返回 {@code true}
     */
    private boolean matchesPhysicalFaultEvidence(
            String scenarioCode,
            Map<String, Object> summary,
            TelemetryReportMessage message,
            Map<String, Double> metrics
    ) {
        return switch (scenarioCode) {
            case "ENV_WIND_CALM" -> metricAtMost(metrics, "windSpeed", faultParameter(summary, "windSpeed", 0.8))
                    && metricPresent(metrics, "turbulence")
                    && message.activePower() <= 1.0 && message.availableCapacity() <= 1.0;
            case "ENV_OVERCAST" -> metricAtMost(metrics, "irradiance", faultParameter(summary, "irradiance", 160.0))
                    && metricAtLeast(metrics, "cloudCover", faultParameter(summary, "cloudCover", 0.85))
                    && message.availableCapacity() <= 30.0 && message.activePower() <= 30.0;
            case "ENV_HEAT" -> metricAtLeast(metrics, "ambientTemperature", faultParameter(summary, "ambientTemperature", 46.0))
                    && metricAtLeast(metrics, "moduleTemperature", faultParameter(summary, "moduleTemperature", 65.0))
                    && message.availableCapacity() <= 140.0;
            case "ENV_DUST" -> metricAtLeast(metrics, "dustDensity", faultParameter(summary, "dustDensity", 18.0))
                    && metricAtMost(metrics, "visibility", faultParameter(summary, "visibility", 2.0))
                    && metricAtMost(metrics, "conversionEfficiency", faultParameter(summary, "conversionEfficiency", 0.62))
                    && message.availableCapacity() <= 110.0;
            case "DEV_INVERTER" -> metricPresent(metrics, "deviceHealthStatus")
                    && metricAtLeast(metrics, "deratingRate", faultParameter(summary, "deratingRate", 0.45))
                    && Math.abs(message.activePower()) <= message.availableCapacity() + 0.1;
            case "DEV_STORAGE" -> metricAtMost(metrics, "onlineClusterCount",
                    faultParameter(summary, "onlineClusterCount", 6.0))
                    && metricPresent(metrics, "remainingSoc")
                    && Math.abs(message.activePower()) <= message.availableCapacity() + 0.1;
            default -> false;
        };
    }

    /**
     * 判断测量字段存在。
     *
     * @param metrics 环境测量
     * @param name 字段名称
     * @return 字段存在且为有限数值时返回 {@code true}
     */
    private boolean metricPresent(Map<String, Double> metrics, String name) {
        Double value = metrics.get(name);
        return value != null && Double.isFinite(value);
    }

    /**
     * 判断测量不大于目标阈值。
     *
     * @param metrics 环境测量
     * @param name 字段名称
     * @param threshold 阈值
     * @return 条件成立时返回 {@code true}
     */
    private boolean metricAtMost(Map<String, Double> metrics, String name, double threshold) {
        return metricPresent(metrics, name) && metrics.get(name) <= threshold;
    }

    /**
     * 判断测量不小于目标阈值。
     *
     * @param metrics 环境测量
     * @param name 字段名称
     * @param threshold 阈值
     * @return 条件成立时返回 {@code true}
     */
    private boolean metricAtLeast(Map<String, Double> metrics, String name, double threshold) {
        return metricPresent(metrics, name) && metrics.get(name) >= threshold;
    }

    private void markFaultObserved(ExperimentRunEntity runEntity, Map<String, Object> summary, OffsetDateTime observedAt) {
        runEntity.setStatus(FAULT_OBSERVED);
        summary.put("lifecycleStatus", FAULT_OBSERVED);
        summary.put("evidenceStatus", FAULT_OBSERVED);
        summary.put("faultObservedAt", observedAt);
        OffsetDateTime injectionStartedAt = offsetDateTime(summary.get("injectionStartedAt"));
        OffsetDateTime orchestrationScheduledAt = (injectionStartedAt == null ? observedAt : injectionStartedAt)
                .plusSeconds(FAULT_INJECTION_STAGE_SECONDS);
        summary.put("orchestrationScheduledAt", orchestrationScheduledAt);
        summary.put("orchestrationStageEndsAt", orchestrationScheduledAt.plusSeconds(ORCHESTRATION_STAGE_SECONDS));
        saveSummary(runEntity, summary);
    }

    private void inspectRun(ExperimentRunEntity runEntity, OffsetDateTime now) {
        Map<String, Object> summary = readSummary(runEntity);
        if (RECOVERING.equals(runEntity.getStatus())) {
            OffsetDateTime recoveryStartedAt = offsetDateTime(summary.get("recoveryStartedAt"));
            if (recoveryStartedAt != null
                    && !now.isBefore(recoveryStartedAt.plusSeconds(recoveryTimeoutSeconds))) {
                markRecoveryFailed(
                        runEntity,
                        summary,
                        now,
                        "RECOVERY_TIMEOUT：恢复期内未同时满足 S4 重新入图、S0 稳定和调度收敛条件"
                );
                summary.put("recoveryTimeoutAt", now);
                saveSummary(runEntity, summary);
            }
            return;
        }
        if (INJECTING.equals(runEntity.getStatus())) {
            OffsetDateTime deadline = offsetDateTime(summary.get("injectionDeadline"));
            if (deadline != null && now.isAfter(deadline)) {
                runEntity.setStatus(INJECTION_NOT_OBSERVED);
                runEntity.setEndedAt(now);
                summary.put("lifecycleStatus", INJECTION_NOT_OBSERVED);
                summary.put("evidenceStatus", "NOT_OBSERVED");
                summary.put("observationFailureReason", "注入截止前未观测到目标场站进入预期链路状态");
                summary.put("failureReason", "注入截止前未观测到目标场站进入预期链路状态");
                summary.put("observedFaultValues", objectMap(summary.get("observedLinkStates")));
                summary.put("orchestrationStatus", "SKIPPED");
                saveSummary(runEntity, summary);
            }
            return;
        }
        if (FAULT_OBSERVED.equals(runEntity.getStatus())) {
            OffsetDateTime orchestrationScheduledAt = offsetDateTime(summary.get("orchestrationScheduledAt"));
            if (orchestrationScheduledAt == null) {
                OffsetDateTime injectionStartedAt = offsetDateTime(summary.get("injectionStartedAt"));
                OffsetDateTime faultObservedAt = offsetDateTime(summary.get("faultObservedAt"));
                OffsetDateTime orchestrationBaseAt = injectionStartedAt == null ? faultObservedAt : injectionStartedAt;
                if (orchestrationBaseAt != null) {
                    orchestrationScheduledAt = orchestrationBaseAt.plusSeconds(FAULT_INJECTION_STAGE_SECONDS);
                    summary.put("orchestrationScheduledAt", orchestrationScheduledAt);
                    summary.put("orchestrationStageEndsAt", orchestrationScheduledAt.plusSeconds(ORCHESTRATION_STAGE_SECONDS));
                    saveSummary(runEntity, summary);
                }
            }
            String orchestrationStatus = stringValue(summary.get("orchestrationStatus"));
            if ("PENDING".equals(orchestrationStatus)
                    && orchestrationScheduledAt != null
                    && !now.isBefore(orchestrationScheduledAt)) {
                triggerOrchestrationAfterEvidenceCommitted(runEntity.getRunId());
                return;
            }
        }
        if (FAULT_OBSERVED.equals(runEntity.getStatus()) && Boolean.TRUE.equals(summary.get("automaticRecovery"))) {
            OffsetDateTime orchestrationStageEndsAt = offsetDateTime(summary.get("orchestrationStageEndsAt"));
            if (orchestrationStageEndsAt == null) {
                OffsetDateTime orchestrationScheduledAt = offsetDateTime(summary.get("orchestrationScheduledAt"));
                if (orchestrationScheduledAt != null) {
                    orchestrationStageEndsAt = orchestrationScheduledAt.plusSeconds(ORCHESTRATION_STAGE_SECONDS);
                    summary.put("orchestrationStageEndsAt", orchestrationStageEndsAt);
                    saveSummary(runEntity, summary);
                }
            }
            if (orchestrationStageEndsAt != null && !now.isBefore(orchestrationStageEndsAt)) {
                beginRecovery(runEntity.getRunId());
            }
        }
    }

    private void triggerOrchestrationAfterEvidenceCommitted(String experimentRunId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            closedLoopOrchestrationService.triggerIfEvidenceObserved(experimentRunId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                closedLoopOrchestrationService.triggerIfEvidenceObserved(experimentRunId);
            }
        });
    }

    private boolean sameTargets(Map<String, Object> summary, List<String> targetStationIds) {
        return List.copyOf(stringList(summary.get("targetStationIds"))).equals(List.copyOf(targetStationIds));
    }

    private boolean containsAllState(Map<String, Object> states, List<String> targets, LinkState expectedState) {
        return targets.stream().allMatch(target -> {
            Object item = states.get(target);
            return item instanceof Map<?, ?> map && expectedState.name().equals(map.get("linkState"));
        });
    }

    private boolean isTerminal(String status) {
        return COMPLETED.equals(status) || INJECTION_NOT_OBSERVED.equals(status) || "FAILED".equals(status) || "STOPPED".equals(status);
    }

    private Map<String, Object> readSummary(ExperimentRunEntity runEntity) {
        try {
            String source = runEntity.getResultSummary();
            if (source == null || !source.startsWith("{")) {
                return new LinkedHashMap<>();
            }
            return objectMapper.readValue(source.split("；", 2)[0], SUMMARY_TYPE);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("实验摘要反序列化失败", exception);
        }
    }

    private void saveSummary(ExperimentRunEntity runEntity, Map<String, Object> summary) {
        try {
            runEntity.setResultSummary(objectMapper.writeValueAsString(summary));
            experimentRunRepository.save(runEntity);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("实验摘要序列化失败", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> objectMap(Object value) {
        if (value instanceof Map<?, ?> source) {
            return new LinkedHashMap<>((Map<String, Object>) source);
        }
        return new LinkedHashMap<>();
    }

    private List<String> stringList(Object value) {
        if (value instanceof List<?> source) {
            return source.stream().filter(String.class::isInstance).map(String.class::cast).toList();
        }
        return List.of();
    }

    private String stringValue(Object value) {
        return value instanceof String string ? string : null;
    }

    private Integer integerValue(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    private Long longValue(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    @SuppressWarnings("unchecked")
    private List<Object> objectList(Object value) {
        return value instanceof List<?> source ? new ArrayList<>((List<Object>) source) : new ArrayList<>();
    }

    private double faultParameter(Map<String, Object> summary, String parameterName, double defaultValue) {
        Object value = objectMap(summary.get("faultParameters")).get(parameterName);
        return value instanceof Number number && Double.isFinite(number.doubleValue()) ? number.doubleValue() : defaultValue;
    }

    private OffsetDateTime offsetDateTime(Object value) {
        return value instanceof String string && !string.isBlank() ? OffsetDateTime.parse(string) : null;
    }
}

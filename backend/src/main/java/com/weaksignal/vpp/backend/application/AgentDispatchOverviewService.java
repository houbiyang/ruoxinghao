package com.weaksignal.vpp.backend.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weaksignal.vpp.backend.api.ApiException;
import com.weaksignal.vpp.backend.api.dto.AgentDispatchOverviewResponse;
import com.weaksignal.vpp.backend.api.dto.DispatchCommandResponse;
import com.weaksignal.vpp.backend.api.dto.DispatchPlanExecutionResponse;
import com.weaksignal.vpp.backend.api.dto.DispatchPlanItemResponse;
import com.weaksignal.vpp.backend.api.dto.DispatchPlanResponse;
import com.weaksignal.vpp.backend.api.dto.DispatchPlanTrackingItemResponse;
import com.weaksignal.vpp.backend.api.dto.DispatchPlanTrackingResponse;
import com.weaksignal.vpp.backend.api.dto.ExperimentComparisonResponse;
import com.weaksignal.vpp.backend.api.dto.ExperimentRunResponse;
import com.weaksignal.vpp.backend.api.dto.ExperimentStartRequest;
import com.weaksignal.vpp.backend.api.dto.ExperimentStartResponse;
import com.weaksignal.vpp.backend.api.dto.ExperimentStopResponse;
import com.weaksignal.vpp.backend.api.dto.ExperimentTimelinePointResponse;
import com.weaksignal.vpp.backend.api.dto.ResourceCapabilityResponse;
import com.weaksignal.vpp.backend.api.dto.ScenarioStatusResponse;
import com.weaksignal.vpp.backend.api.dto.SettlementResultResponse;
import com.weaksignal.vpp.backend.api.dto.StationStateResponse;
import com.weaksignal.vpp.backend.config.MpcConvergenceProperties;
import com.weaksignal.vpp.backend.capability.ResourceCapabilityService;
import com.weaksignal.vpp.backend.domain.ControlMode;
import com.weaksignal.vpp.backend.domain.ExperimentScenarioDefinition;
import com.weaksignal.vpp.backend.domain.LinkState;
import com.weaksignal.vpp.backend.domain.ResourceType;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.DispatchConvergenceRoundEntity;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.DispatchConvergenceRoundRepository;
import com.weaksignal.vpp.backend.marketagent.MarketAgentDecision;
import com.weaksignal.vpp.backend.marketagent.MarketSafetyDecision;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 多智能体调度中心聚合查询服务。
 */
@Service
public class AgentDispatchOverviewService {

    private static final String CENTER_NODE_ID = "agent-dispatch-engine";
    private static final List<String> KEY_AGENT_TYPES = List.of(
            "MARKET_INTERFACE",
            "FORECAST",
            "ENVIRONMENT_ASSESSMENT",
            "RESOURCE_CAPABILITY",
            "MARKET_DISPATCH",
            "COMMUNICATION_QUALITY",
            "CONTROL_DECISION",
            "SECURITY_AUDIT"
    );

    private final DashboardQueryService dashboardQueryService;
    private final ScenarioService scenarioService;
    private final DispatchService dispatchService;
    private final ExperimentService experimentService;
    private final ExperimentAnalysisService experimentAnalysisService;
    private final MarketDispatchPlanningService marketDispatchPlanningService;
    private final MarketSettlementService marketSettlementService;
    private final MarketAgentOrchestrationService marketAgentOrchestrationService;
    private final ResourceCapabilityService resourceCapabilityService;
    private final Clock clock;
    private ObjectMapper objectMapper = new ObjectMapper();
    private DispatchConvergenceRoundRepository dispatchConvergenceRoundRepository;
    private MpcConvergenceProperties mpcConvergenceProperties = new MpcConvergenceProperties(
            1.0, 1.0, 1.0, 3, java.time.Duration.ofSeconds(45), java.time.Duration.ofSeconds(15),
            java.time.Duration.ofMinutes(2), java.time.Duration.ofSeconds(15), 3, java.time.Duration.ofMinutes(10),
            java.time.Duration.ofSeconds(30), java.time.Duration.ofSeconds(30));

    /**
     * 注入收敛轮次审计仓储。
     *
     * @param dispatchConvergenceRoundRepository 收敛轮次仓储
     */
    @Autowired
    public void setDispatchConvergenceRoundRepository(DispatchConvergenceRoundRepository dispatchConvergenceRoundRepository) {
        this.dispatchConvergenceRoundRepository = dispatchConvergenceRoundRepository;
    }

    /**
     * 注入 MPC 收敛配置，确保接口展示与后端判定使用同一组参数。
     *
     * @param mpcConvergenceProperties MPC 收敛配置
     */
    @Autowired
    public void setMpcConvergenceProperties(MpcConvergenceProperties mpcConvergenceProperties) {
        this.mpcConvergenceProperties = mpcConvergenceProperties;
    }

    /**
     * 注入统一 JSON 映射器，用于读取实验结构化摘要。
     *
     * @param objectMapper JSON 映射器
     */
    @Autowired
    public void setObjectMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 注入多智能体调度中心聚合依赖。
     *
     * @param dashboardQueryService 场站总览查询服务
     * @param scenarioService 场景服务
     * @param dispatchService 调度执行服务
     * @param experimentService 实验服务
     * @param experimentAnalysisService 实验分析服务
     * @param marketDispatchPlanningService 市场调度计划服务
     * @param marketSettlementService 市场结算服务
     * @param marketAgentOrchestrationService 市场调度多智能体服务
     * @param resourceCapabilityService 资源能力服务
     * @param clock 系统时钟
     */
    public AgentDispatchOverviewService(
            DashboardQueryService dashboardQueryService,
            ScenarioService scenarioService,
            DispatchService dispatchService,
            ExperimentService experimentService,
            ExperimentAnalysisService experimentAnalysisService,
            MarketDispatchPlanningService marketDispatchPlanningService,
            MarketSettlementService marketSettlementService,
            MarketAgentOrchestrationService marketAgentOrchestrationService,
            ResourceCapabilityService resourceCapabilityService,
            Clock clock
    ) {
        this.dashboardQueryService = dashboardQueryService;
        this.scenarioService = scenarioService;
        this.dispatchService = dispatchService;
        this.experimentService = experimentService;
        this.experimentAnalysisService = experimentAnalysisService;
        this.marketDispatchPlanningService = marketDispatchPlanningService;
        this.marketSettlementService = marketSettlementService;
        this.marketAgentOrchestrationService = marketAgentOrchestrationService;
        this.resourceCapabilityService = resourceCapabilityService;
        this.clock = clock;
    }

    /**
     * 查询多智能体调度中心总览。
     *
     * @return 聚合总览响应
     */
    public AgentDispatchOverviewResponse getOverview() {
        OffsetDateTime now = OffsetDateTime.now(clock);
        List<StationStateResponse> stations = dashboardQueryService.getStations();
        ScenarioStatusResponse scenarioStatus = scenarioService.getCurrentScenario();
        List<ResourceCapabilityResponse> capabilities = safeOptional(resourceCapabilityService::getLatestCapabilities).orElse(List.of());
        DispatchPlanResponse latestPlan = safeOptional(marketDispatchPlanningService::getCurrentActivePlan)
                .or(() -> safeOptional(marketDispatchPlanningService::getLatestPlan).filter(plan -> isCurrentActivePlan(plan, now)))
                .orElse(null);
        DispatchPlanExecutionResponse executionResponse = latestPlan == null
                ? null
                : safeOptional(() -> marketDispatchPlanningService.getExecution(latestPlan.planId())).orElse(null);
        DispatchPlanItemResponse currentPlanItem = resolveCurrentPlanItem(latestPlan, executionResponse, now);
        DispatchPlanTrackingResponse trackingResponse = latestPlan == null
                ? null
                : safeOptional(() -> marketDispatchPlanningService.getTracking(latestPlan.planId())).orElse(null);
        DispatchPlanTrackingItemResponse currentTrackingItem = resolveCurrentTrackingItem(trackingResponse, currentPlanItem);
        SettlementResultResponse latestSettlement = safeOptional(marketSettlementService::getLatestSettlement).orElse(null);
        ExperimentRunResponse currentExperiment = experimentService.getCurrentRun();
        ExperimentRunResponse comparisonRun = resolveComparisonRun(currentExperiment);
        ExperimentComparisonResponse experimentComparison = comparisonRun == null
                ? null
                : safeOptional(() -> experimentAnalysisService.getComparison(comparisonRun.runId())).orElse(null);
        List<ExperimentTimelinePointResponse> experimentTimeline = comparisonRun == null
                ? List.of()
                : safeOptional(() -> experimentAnalysisService.getTimeline(comparisonRun.runId())).orElse(List.of());
        var latestAgentRun = safeOptional(marketAgentOrchestrationService::getLatestRun).orElse(null);

        return new AgentDispatchOverviewResponse(
                buildSystemStatus(now, scenarioStatus, latestPlan != null, stations, latestAgentRun != null),
                buildDispatchStrategy(scenarioStatus, stations, currentPlanItem, capabilities, executionResponse, latestAgentRun),
                buildTopology(scenarioStatus, stations, capabilities, currentPlanItem, executionResponse),
                buildDispatchSummary(latestPlan, currentPlanItem, currentTrackingItem, latestSettlement, executionResponse, experimentComparison, capabilities),
                buildExperimentStatus(currentExperiment, now),
                buildComparisonSummary(comparisonRun, experimentComparison),
                buildAgentDecisionSummary(latestAgentRun),
                buildExecutionTimeline(comparisonRun, latestAgentRun, executionResponse, experimentTimeline),
                buildResourceTracking(comparisonRun, latestPlan, executionResponse, stations)
        );
    }

    /**
     * 代理启动实验。
     *
     * @param scenarioCode 场景编码
     * @return 启动结果
     */
    public ExperimentStartResponse startExperiment(String scenarioCode) {
        return experimentService.startExperiment(scenarioCode, new ExperimentStartRequest(null, null, null, null, null, "agent-dispatch-overview"));
    }

    /**
     * 代理停止当前实验。
     *
     * @return 停止结果
     */
    public ExperimentStopResponse stopCurrentExperiment() {
        ExperimentRunResponse currentRun = experimentService.getCurrentRun();
        if (currentRun == null) {
            throw new ApiException(HttpStatus.CONFLICT, "EXPERIMENT_NOT_RUNNING", "当前没有运行中的实验", Map.of());
        }
        return experimentService.stopExperiment(currentRun.runId());
    }

    private AgentDispatchOverviewResponse.SystemStatusResponse buildSystemStatus(
            OffsetDateTime now,
            ScenarioStatusResponse scenarioStatus,
            boolean planAvailable,
            List<StationStateResponse> stations,
            boolean agentRunAvailable
    ) {
        Map<String, Object> deepSeekStatus = safeOptional(marketAgentOrchestrationService::getDeepSeekStatus).orElse(Map.of());
        boolean aiEnabled = readBoolean(deepSeekStatus.get("enabled"));
        boolean aiAvailable = readBoolean(deepSeekStatus.get("available"));
        String aiProvider = deepSeekStatus.get("providerCode") instanceof String providerCode ? providerCode : null;
        String dataSourceStatus = stations.isEmpty() ? "UNAVAILABLE" : planAvailable || agentRunAvailable ? "REALTIME" : "DEGRADED";
        return new AgentDispatchOverviewResponse.SystemStatusResponse(
                aiEnabled,
                aiAvailable,
                aiProvider,
                scenarioStatus.controlMode(),
                scenarioStatus.scenario().name(),
                dataSourceStatus,
                now
        );
    }

    private AgentDispatchOverviewResponse.DispatchStrategyResponse buildDispatchStrategy(
            ScenarioStatusResponse scenarioStatus,
            List<StationStateResponse> stations,
            DispatchPlanItemResponse currentPlanItem,
            List<ResourceCapabilityResponse> capabilities,
            DispatchPlanExecutionResponse executionResponse,
            com.weaksignal.vpp.backend.api.dto.MarketAgentRunResponse latestAgentRun
    ) {
        List<DispatchCommandResponse> commands = executionResponse == null ? List.of() : executionResponse.commands();
        Map<String, StationStateResponse> stationMap = stations.stream()
                .collect(Collectors.toMap(StationStateResponse::stationId, station -> station, (left, right) -> left));
        Map<String, DispatchCommandResponse> latestCommandByStation = mapLatestCommandByStation(commands);
        boolean safetyPassed = latestAgentRun != null && latestAgentRun.safetyDecision().passed();
        ResourceCapabilityResponse storageCapability = capabilities.stream()
                .filter(capability -> capability.resourceType() == ResourceType.STORAGE)
                .findFirst()
                .orElse(null);
        StationStateResponse loadStation = stationMap.get("load-oasis-04");
        StationStateResponse chargingStation = stationMap.get("charge-corridor-05");
        StationStateResponse gridStation = stationMap.get("grid-link-00");
        StationStateResponse photovoltaicStation = stationMap.get("pv-gobi-01");
        StationStateResponse windStation = stationMap.get("wind-ridge-02");
        StationStateResponse storageStation = stationMap.get("storage-hub-03");

        BigDecimal loadReductionPower = resolveCommandAdjustment(loadStation, latestCommandByStation.get("load-oasis-04"), true);
        BigDecimal chargingIncreasePower = resolveCommandAdjustment(chargingStation, latestCommandByStation.get("charge-corridor-05"), false);
        BigDecimal peakShavingDisplayPower = loadReductionPower == null && loadStation != null ? decimal(loadStation.activePower(), 1) : loadReductionPower == null ? null : loadReductionPower.negate();
        BigDecimal valleyFillingDisplayPower = chargingIncreasePower == null && chargingStation != null ? decimal(chargingStation.activePower(), 1) : chargingIncreasePower;
        BigDecimal demandResponseDisplayPower = combineSignedDemandResponse(
                loadReductionPower,
                chargingIncreasePower,
                loadStation == null ? null : decimal(loadStation.activePower(), 1),
                chargingStation == null ? null : decimal(chargingStation.activePower(), 1)
        );
        BigDecimal storageChargePower = resolveStorageChargePower(currentPlanItem, latestCommandByStation.get("storage-hub-03"));
        BigDecimal storageDischargePower = resolveStorageDischargePower(currentPlanItem, latestCommandByStation.get("storage-hub-03"));
        BigDecimal renewableOutputPower = resolveRenewableOutputPower(currentPlanItem, photovoltaicStation, windStation);
        BigDecimal renewableCurtailmentPower = resolveRenewableCurtailmentPower(currentPlanItem, photovoltaicStation, windStation);
        BigDecimal gridImportPower = resolveGridImportPower(currentPlanItem, gridStation);
        BigDecimal gridExportPower = resolveGridExportPower(currentPlanItem, gridStation);

        AgentDispatchOverviewResponse.DispatchStrategyGroupResponse loadGroup = new AgentDispatchOverviewResponse.DispatchStrategyGroupResponse(
                "load",
                "负荷侧操作",
                "当前策略",
                List.of(
                        buildStrategyItem(
                                "LOAD_PEAK_SHAVING",
                                "削峰",
                                "load",
                                resolveStrategyStatus(safetyPassed, loadReductionPower, latestCommandByStation.get("load-oasis-04")),
                                peakShavingDisplayPower,
                                "kW",
                                loadReductionPower == null
                                        ? "当前未检测到明确削峰指令，展示可调负荷实时受控功率。"
                                        : "根据负荷实时功率与最新控制目标执行削峰，已形成明确负荷下调量。",
                                List.of("load-oasis-04"),
                                safetyPassed && loadReductionPower != null
                        ),
                        buildStrategyItem(
                                "LOAD_VALLEY_FILLING",
                                "填谷",
                                "load",
                                resolveStrategyStatus(safetyPassed, chargingIncreasePower, latestCommandByStation.get("charge-corridor-05")),
                                valleyFillingDisplayPower,
                                "kW",
                                chargingIncreasePower == null
                                        ? "当前没有填谷窗口，展示充电设施实时受控功率。"
                                        : "根据充电设施实时功率与最新控制目标执行填谷增补。",
                                List.of("charge-corridor-05"),
                                safetyPassed && chargingIncreasePower != null
                        ),
                        buildStrategyItem(
                                "LOAD_DEMAND_RESPONSE",
                                "需求响应",
                                "load",
                                resolveCombinedStrategyStatus(
                                        safetyPassed,
                                        java.util.Arrays.asList(loadReductionPower, chargingIncreasePower),
                                        java.util.Arrays.asList(latestCommandByStation.get("load-oasis-04"), latestCommandByStation.get("charge-corridor-05"))
                                ),
                                demandResponseDisplayPower,
                                "kW",
                                loadReductionPower == null && chargingIncreasePower == null
                                        ? "当前无显式需求响应动作，展示负荷侧实时受控总功率。"
                                        : "负荷侧调节动作已根据模拟器实时遥测进入需求响应链路。",
                                List.of("load-oasis-04", "charge-corridor-05"),
                                safetyPassed && (loadReductionPower != null || chargingIncreasePower != null)
                        )
                )
        );

        AgentDispatchOverviewResponse.DispatchStrategyGroupResponse generationGroup = new AgentDispatchOverviewResponse.DispatchStrategyGroupResponse(
                "generation",
                "电源侧操作",
                "功率修正",
                List.of(
                        buildStrategyItem(
                                "GEN_OUTPUT_REGULATION",
                                "出力调节",
                                "generation",
                                renewableOutputPower == null ? "STANDBY" : "EXECUTING",
                                renewableOutputPower,
                                "kW",
                                renewableOutputPower == null ? "暂无新能源调节数据。" : "根据模拟器上送的新能源实时出力展示当前调度后的电源侧出力。",
                                List.of("pv-gobi-01", "wind-ridge-02"),
                                safetyPassed && renewableOutputPower != null
                        ),
                        buildStrategyItem(
                                "GEN_RENEWABLE_CURTAILMENT",
                                "新能源消纳控制",
                                "generation",
                                renewableCurtailmentPower == null ? "STANDBY" : "EXECUTING",
                                renewableCurtailmentPower,
                                "kW",
                                renewableCurtailmentPower == null
                                        ? "当前无新能源消纳控制动作。"
                                        : "依据新能源可用容量与实时出力差值展示当前消纳控制结果。",
                                List.of("pv-gobi-01", "wind-ridge-02"),
                                safetyPassed && renewableCurtailmentPower != null
                        ),
                        buildStrategyItem(
                                "GEN_RESERVE_DISPATCH",
                                "备用调度",
                                "generation",
                                currentPlanItem != null && currentPlanItem.upwardReserve() > 0 ? resolveCombinedStrategyStatus(
                                        safetyPassed,
                                        List.of(decimal(currentPlanItem.upwardReserve(), 1)),
                                        java.util.Arrays.asList(latestCommandByStation.get("storage-hub-03"), latestCommandByStation.get("grid-link-00"))
                                ) : "STANDBY",
                                currentPlanItem != null && currentPlanItem.upwardReserve() > 0 ? decimal(currentPlanItem.upwardReserve(), 1) : null,
                                "kW",
                                currentPlanItem != null && currentPlanItem.upwardReserve() > 0
                                        ? "根据计划向上备用需求保留资源。"
                                        : "当前无备用调度需求。",
                                List.of("storage-hub-03", "grid-link-00"),
                                safetyPassed && currentPlanItem != null && currentPlanItem.upwardReserve() > 0
                        )
                )
        );

        AgentDispatchOverviewResponse.DispatchStrategyGroupResponse storageGroup = new AgentDispatchOverviewResponse.DispatchStrategyGroupResponse(
                "storage",
                "储能操作",
                "SOC 与出力",
                List.of(
                        buildStrategyItem(
                                "STORAGE_CHARGE",
                                "充电",
                                "storage",
                                resolveStrategyStatus(safetyPassed, storageChargePower, latestCommandByStation.get("storage-hub-03")),
                                storageChargePower,
                                "kW",
                                storageChargePower == null
                                        ? "当前无储能充电动作。"
                                        : "根据储能计划与执行指令展示当前充电补能功率。",
                                List.of("storage-hub-03"),
                                safetyPassed && storageChargePower != null
                        ),
                        buildStrategyItem(
                                "STORAGE_DISCHARGE",
                                "放电",
                                "storage",
                                resolveStrategyStatus(safetyPassed, storageDischargePower, latestCommandByStation.get("storage-hub-03")),
                                storageDischargePower == null ? null : storageDischargePower.negate(),
                                "kW",
                                storageDischargePower == null
                                        ? "当前无储能放电动作。"
                                        : "根据储能执行回执展示当前放电支撑功率。",
                                List.of("storage-hub-03"),
                                safetyPassed && storageDischargePower != null
                        ),
                        buildStrategyItem(
                                "STORAGE_SOC_MANAGEMENT",
                                "SOC管理",
                                "storage",
                                storageCapability != null && storageCapability.storageConstraint() != null ? "EXECUTING" : "STANDBY",
                                storageCapability != null && storageCapability.storageConstraint() != null
                                        ? BigDecimal.valueOf(storageCapability.storageConstraint().soc()).setScale(1, RoundingMode.HALF_UP)
                                        : null,
                                "%",
                                storageCapability != null && storageCapability.storageConstraint() != null
                                        ? "依据实时储能约束维持 SOC 安全区间。"
                                        : "当前没有可用储能 SOC 数据。",
                                List.of("storage-hub-03"),
                                storageCapability != null && storageCapability.storageConstraint() != null
                        )
                )
        );

        AgentDispatchOverviewResponse.DispatchStrategyGroupResponse gridGroup = new AgentDispatchOverviewResponse.DispatchStrategyGroupResponse(
                "grid",
                "电网交互",
                "并网交换",
                List.of(
                        buildStrategyItem(
                                "GRID_IMPORT",
                                "市电购电",
                                "grid",
                                gridImportPower == null ? "STANDBY" : "EXECUTING",
                                gridImportPower,
                                "kW",
                                gridImportPower == null ? "当前无市电购电动作。" : "根据并网点实时功率展示当前购电支撑结果。",
                                List.of("grid-link-00"),
                                safetyPassed && gridImportPower != null
                        ),
                        buildStrategyItem(
                                "GRID_EXPORT",
                                "市电外送",
                                "grid",
                                gridExportPower == null ? "STANDBY" : "EXECUTING",
                                gridExportPower == null ? null : gridExportPower.negate(),
                                "kW",
                                gridExportPower == null ? "当前无市电外送动作。" : "根据并网点实时功率展示当前外送执行结果。",
                                List.of("grid-link-00"),
                                safetyPassed && gridExportPower != null
                        )
                )
        );

        return new AgentDispatchOverviewResponse.DispatchStrategyResponse(
                "当前执行策略",
                scenarioStatus.controlMode().getValue(),
                loadGroup,
                generationGroup,
                storageGroup,
                gridGroup
        );
    }

    private AgentDispatchOverviewResponse.ResourceTopologyResponse buildTopology(
            ScenarioStatusResponse scenarioStatus,
            List<StationStateResponse> stations,
            List<ResourceCapabilityResponse> capabilities,
            DispatchPlanItemResponse currentPlanItem,
            DispatchPlanExecutionResponse executionResponse
    ) {
        Map<String, ResourceCapabilityResponse> capabilityMap = capabilities.stream()
                .collect(Collectors.toMap(ResourceCapabilityResponse::stationId, capability -> capability, (left, right) -> left));
        Map<String, DispatchCommandResponse> latestCommandByStation = executionResponse == null
                ? Map.of()
                : executionResponse.commands().stream()
                .collect(Collectors.toMap(
                        DispatchCommandResponse::stationId,
                        command -> command,
                        (left, right) -> right,
                        LinkedHashMap::new
                ));

        List<AgentDispatchOverviewResponse.ResourceNodeResponse> nodes = stations.stream()
                .sorted(Comparator.comparing(StationStateResponse::stationId))
                .map(station -> toResourceNode(station, capabilityMap.get(station.stationId()), scenarioStatus.controlMode()))
                .toList();

        List<AgentDispatchOverviewResponse.EnergyLinkResponse> energyLinks = buildEnergyLinks(stations);
        List<AgentDispatchOverviewResponse.ControlLinkResponse> controlLinks = stations.stream()
                .map(station -> {
                    DispatchCommandResponse latestCommand = latestCommandByStation.get(station.stationId());
                    return new AgentDispatchOverviewResponse.ControlLinkResponse(
                            "control-" + station.stationId(),
                            CENTER_NODE_ID,
                            station.stationId(),
                            mapCommandStatus(latestCommand == null ? null : latestCommand.status()),
                            station.linkState(),
                            station.linkState() != LinkState.S3
                    );
                })
                .toList();

        BigDecimal totalSupply = energyLinks.stream()
                .filter(link -> "SUPPLY".equals(link.direction()))
                .map(AgentDispatchOverviewResponse.EnergyLinkResponse::power)
                .filter(value -> value != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalConsume = energyLinks.stream()
                .filter(link -> "CONSUME".equals(link.direction()))
                .map(AgentDispatchOverviewResponse.EnergyLinkResponse::power)
                .filter(value -> value != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new AgentDispatchOverviewResponse.ResourceTopologyResponse(
                new AgentDispatchOverviewResponse.CenterNodeResponse(
                        CENTER_NODE_ID,
                        "多智能体协同调度引擎",
                        scenarioStatus.controlMode(),
                        "RUNNING",
                        "策略执行中"
                ),
                nodes,
                energyLinks,
                controlLinks,
                new AgentDispatchOverviewResponse.BalanceSummaryResponse(totalSupply, totalConsume, totalSupply.subtract(totalConsume), "kW"),
                List.of(
                        new AgentDispatchOverviewResponse.LegendItemResponse("能源流向（供能）", "energy", "#67f06b"),
                        new AgentDispatchOverviewResponse.LegendItemResponse("能源流向（用能）", "energy", "#39b3ff"),
                        new AgentDispatchOverviewResponse.LegendItemResponse("控制指令流", "control", "#ffb349")
                )
        );
    }

    private AgentDispatchOverviewResponse.DispatchSummaryResponse buildDispatchSummary(
            DispatchPlanResponse latestPlan,
            DispatchPlanItemResponse currentPlanItem,
            DispatchPlanTrackingItemResponse currentTrackingItem,
            SettlementResultResponse latestSettlement,
            DispatchPlanExecutionResponse executionResponse,
            ExperimentComparisonResponse experimentComparison,
            List<ResourceCapabilityResponse> capabilities
    ) {
        BigDecimal plannedTarget = resolvePlannedTargetPower(currentPlanItem, currentTrackingItem, executionResponse);
        BigDecimal actualPower = currentTrackingItem == null ? null : decimal(currentTrackingItem.actualNetPower(), 1);
        BigDecimal trackingErrorRate = calculateTrackingErrorRate(currentTrackingItem, plannedTarget);
        BigDecimal curtailmentImprovement = extractComparisonImprovement(experimentComparison, "curtailment");
        BigDecimal curtailmentRate = extractComparisonRate(experimentComparison, "curtailment");
        BigDecimal executionSuccess = executionResponse == null ? null : calculateExecutionSuccessRate(executionResponse.commands());
        BigDecimal reserveSatisfaction = calculateReserveSatisfaction(currentPlanItem, capabilities);

        return new AgentDispatchOverviewResponse.DispatchSummaryResponse(
                new AgentDispatchOverviewResponse.MetricValueResponse("计划目标功率", plannedTarget, "kW", null, null, "UNKNOWN", null, plannedTarget != null),
                new AgentDispatchOverviewResponse.MetricValueResponse("实际功率", actualPower, "kW", null, null, "UNKNOWN", null, actualPower != null),
                new AgentDispatchOverviewResponse.MetricValueResponse(
                        "跟踪误差率",
                        trackingErrorRate,
                        "%",
                        trackingErrorRate == null ? null : trackingErrorRate.abs(),
                        trackingErrorRate == null ? null : trackingErrorRate.abs(),
                        trackingErrorRate == null ? "UNKNOWN" : "DOWN",
                        false,
                        trackingErrorRate != null
                ),
                new AgentDispatchOverviewResponse.MetricValueResponse(
                        "收益改善",
                        latestSettlement == null ? null : latestSettlement.netRevenue().setScale(2, RoundingMode.HALF_UP),
                        "元",
                        latestSettlement == null ? null : latestSettlement.netRevenue().setScale(2, RoundingMode.HALF_UP),
                        null,
                        latestSettlement == null ? "UNKNOWN" : latestSettlement.netRevenue().compareTo(BigDecimal.ZERO) >= 0 ? "UP" : "DOWN",
                        true,
                        latestSettlement != null
                ),
                new AgentDispatchOverviewResponse.MetricValueResponse(
                        "弃电降低",
                        curtailmentImprovement,
                        "kWh",
                        curtailmentImprovement,
                        curtailmentRate,
                        curtailmentImprovement == null ? "UNKNOWN" : "DOWN",
                        false,
                        curtailmentImprovement != null
                ),
                new AgentDispatchOverviewResponse.MetricValueResponse(
                        "备用满足率",
                        reserveSatisfaction,
                        "%",
                        null,
                        null,
                        reserveSatisfaction == null ? "UNKNOWN" : "UP",
                        true,
                        reserveSatisfaction != null
                ),
                new AgentDispatchOverviewResponse.MetricValueResponse(
                        "执行成功率",
                        executionSuccess,
                        "%",
                        executionSuccess,
                        null,
                        executionSuccess == null ? "UNKNOWN" : "UP",
                        true,
                        executionSuccess != null
                ),
                latestPlan == null ? null : latestPlan.planId(),
                latestPlan == null ? null : latestPlan.version()
        );
    }

    private AgentDispatchOverviewResponse.ExperimentStatusResponse buildExperimentStatus(ExperimentRunResponse currentExperiment, OffsetDateTime now) {
        if (currentExperiment == null) {
            return new AgentDispatchOverviewResponse.ExperimentStatusResponse(null, null, null, false, null, null, List.of(), null, null);
        }
        ExperimentScenarioDefinition definition = ExperimentScenarioDefinition.fromCode(currentExperiment.scenarioCode());
        JsonNode summary = readExperimentSummary(currentExperiment.resultSummary());
        Integer durationSeconds = summary.path("durationSeconds").canConvertToInt()
                ? summary.path("durationSeconds").asInt()
                : definition.getDurationSeconds();
        List<String> requestedTargetStationIds = new ArrayList<>();
        summary.path("targetStationIds").forEach(stationId -> requestedTargetStationIds.add(stationId.asText()));
        List<String> targetStationIds = requestedTargetStationIds.isEmpty()
                ? definition.getTargetStationIds()
                : requestedTargetStationIds;
        long elapsedSeconds = java.time.Duration.between(currentExperiment.startedAt(), currentExperiment.endedAt() == null ? now : currentExperiment.endedAt()).toSeconds();
        return new AgentDispatchOverviewResponse.ExperimentStatusResponse(
                currentExperiment.runId(),
                currentExperiment.scenarioCode(),
                currentExperiment.scenarioName(),
                isActiveExperimentStatus(currentExperiment.status()),
                durationSeconds,
                elapsedSeconds,
                targetStationIds,
                currentExperiment.startedAt(),
                definition.getExpectedLinkState()
        );
    }

    private JsonNode readExperimentSummary(String resultSummary) {
        if (resultSummary == null || resultSummary.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(resultSummary.split("；", 2)[0]);
        } catch (Exception exception) {
            return objectMapper.createObjectNode();
        }
    }

    private boolean isActiveExperimentStatus(String status) {
        return List.of("STARTING", "RUNNING", "INJECTING", "FAULT_OBSERVED", "ORCHESTRATING", "RECOVERING").contains(status);
    }

    private AgentDispatchOverviewResponse.ComparisonSummaryResponse buildComparisonSummary(
            ExperimentRunResponse comparisonRun,
            ExperimentComparisonResponse experimentComparison
    ) {
        if (comparisonRun == null) {
            return new AgentDispatchOverviewResponse.ComparisonSummaryResponse(
                    null,
                    null,
                    null,
                    defaultComparisonMetrics()
            );
        }
        return new AgentDispatchOverviewResponse.ComparisonSummaryResponse(
                comparisonRun.runId(),
                comparisonRun.scenarioCode(),
                comparisonRun.scenarioName(),
                mapComparisonMetrics(experimentComparison)
        );
    }

    private AgentDispatchOverviewResponse.AgentDecisionSummaryResponse buildAgentDecisionSummary(
            com.weaksignal.vpp.backend.api.dto.MarketAgentRunResponse latestAgentRun
    ) {
        if (latestAgentRun == null) {
            return new AgentDispatchOverviewResponse.AgentDecisionSummaryResponse(List.of(), "当前没有本轮智能体记录", "UNKNOWN", null, false, null, "规则保守模式", null);
        }
        Map<String, MarketAgentDecision> decisionMap = latestAgentRun.decisions().stream()
                .collect(Collectors.toMap(decision -> decision.agentType().name(), decision -> decision, (left, right) -> left));
        boolean legacyCompatible = KEY_AGENT_TYPES.stream().anyMatch(agentType -> !decisionMap.containsKey(agentType));
        boolean fallbackActive = latestAgentRun.safetyDecision().fallbackUsed()
                || latestAgentRun.decisions().stream().anyMatch(MarketAgentDecision::fallbackUsed)
                || legacyCompatible;
        String safetyDecisionLevel = resolveSafetyDecisionLevel(
                latestAgentRun.safetyDecision().decisionLevel(),
                latestAgentRun.safetyDecision().passed(),
                fallbackActive
        );
        List<AgentDispatchOverviewResponse.AgentDecisionRowResponse> rows = KEY_AGENT_TYPES.stream()
                .map(agentType -> {
                    MarketAgentDecision decision = decisionMap.get(agentType);
                    if (decision == null) {
                        return compatibleDecisionRow(agentType, latestAgentRun.safetyDecision().passed());
                    }
                    boolean adopted = latestAgentRun.safetyDecision().passed()
                            && decision.candidateActions().stream().anyMatch(action -> latestAgentRun.safetyDecision().approvedActions().contains(action));
                    return new AgentDispatchOverviewResponse.AgentDecisionRowResponse(
                            agentType,
                            agentDisplayName(agentType),
                            true,
                            decimal(decision.confidence() * 100, 0),
                            String.join("，", decision.candidateActions()),
                            resolveAgentFinalDecision(decision, latestAgentRun.safetyDecision(), safetyDecisionLevel, adopted),
                            adopted,
                            decision.evidenceRefs().size(),
                            resolveAgentFallbackReason(decision, latestAgentRun.safetyDecision(), safetyDecisionLevel, adopted),
                            decision.stageStatus().name(),
                            decision.retryCount(),
                            decision.errorCategory().name(),
                            decision.inputVersion(),
                            decision.outputVersion()
                    );
                })
                .toList();
        return new AgentDispatchOverviewResponse.AgentDecisionSummaryResponse(
                rows,
                resolveOverallDecisionExplanation(latestAgentRun.safetyDecision(), safetyDecisionLevel, legacyCompatible),
                safetyDecisionLevel,
                latestAgentRun.safetyDecision().passed(),
                fallbackActive,
                latestAgentRun.runId(),
                fallbackActive ? "规则保守模式" : "DeepSeek 模式",
                resolveSummaryFallbackReason(fallbackActive, legacyCompatible)
        );
    }

    /**
     * 为旧版智能体运行补齐新增链路节点的展示行。
     *
     * @param agentType 智能体类型
     * @param safetyPassed 安全门是否通过
     * @return 兼容展示行
     */
    private AgentDispatchOverviewResponse.AgentDecisionRowResponse compatibleDecisionRow(String agentType, boolean safetyPassed) {
        return new AgentDispatchOverviewResponse.AgentDecisionRowResponse(
                agentType,
                agentDisplayName(agentType),
                true,
                new BigDecimal("65"),
                compatibleSuggestedAction(agentType),
                safetyPassed ? "规则兼容输出" : "安全门拒绝",
                safetyPassed,
                1,
                "旧版运行记录缺少该智能体明细，已由统一裁决层按当前链路补齐。",
                "LEGACY",
                0,
                "NONE",
                "legacy-input",
                "legacy-output"
        );
    }

    /**
     * 返回旧版运行补齐时的默认建议动作。
     *
     * @param agentType 智能体类型
     * @return 建议动作
     */
    private String compatibleSuggestedAction(String agentType) {
        return switch (agentType) {
            case "MARKET_INTERFACE" -> "VERIFY_MARKET_SIGNAL_VERSION";
            case "FORECAST" -> "USE_P50_FORECAST";
            case "ENVIRONMENT_ASSESSMENT" -> "APPLY_ENVIRONMENT_CORRECTION";
            case "RESOURCE_CAPABILITY" -> "FREEZE_RESOURCE_CAPABILITY_BOUNDARY";
            case "MARKET_DISPATCH" -> "GENERATE_MARKET_OPTIMAL_PLAN";
            case "COMMUNICATION_QUALITY" -> "APPLY_S0_S4_CAPACITY_FACTOR";
            case "CONTROL_DECISION" -> "ISSUE_SAFE_DISPATCH_DECISION";
            case "SECURITY_AUDIT" -> "ALLOW_SAFE_ACTIONS";
            default -> "RULE_COMPATIBLE_OUTPUT";
        };
    }

    /**
     * 将智能体行级裁决归一为分层裁决语义，避免前端显示“待执行”造成意见冲突。
     *
     * @param safetyDecision 安全门裁决
     * @param safetyDecisionLevel 安全门级别
     * @param adopted 当前智能体动作是否被安全门采纳
     * @return 归一后的行级裁决
     */
    private String resolveAgentFinalDecision(
            MarketAgentDecision decision,
            MarketSafetyDecision safetyDecision,
            String safetyDecisionLevel,
            boolean adopted
    ) {
        if (decision.stageStatus() == com.weaksignal.vpp.backend.marketagent.MarketAgentStageStatus.BLOCKED) {
            return "阶段阻断";
        }
        if (decision.stageStatus() == com.weaksignal.vpp.backend.marketagent.MarketAgentStageStatus.FAILED) {
            return "阶段失败";
        }
        if (decision.stageStatus() == com.weaksignal.vpp.backend.marketagent.MarketAgentStageStatus.RULE_FALLBACK) {
            return "规则回退";
        }
        if (!safetyDecision.passed()) {
            return "已完成，未采纳";
        }
        if (adopted && "DEGRADED".equals(safetyDecisionLevel)) {
            return "降级执行";
        }
        if (adopted) {
            return "已执行";
        }
        if ("DEGRADED".equals(safetyDecisionLevel)) {
            return "约束覆盖";
        }
        return "已归一";
    }

    /**
     * 生成智能体行级说明，说明动作是被执行、覆盖还是由规则回退提供。
     *
     * @param decision 智能体决策
     * @param safetyDecision 安全门裁决
     * @param safetyDecisionLevel 安全门级别
     * @param adopted 当前智能体动作是否被采纳
     * @return 行级补充说明
     */
    private String resolveAgentFallbackReason(
            MarketAgentDecision decision,
            MarketSafetyDecision safetyDecision,
            String safetyDecisionLevel,
            boolean adopted
    ) {
        if (decision.fallbackUsed()) {
            return resolveFallbackReason(decision.errorCategory());
        }
        if (safetyDecision.passed() && !adopted && "DEGRADED".equals(safetyDecisionLevel)) {
            return "该智能体输出已进入统一裁决层，由更高优先级约束覆盖。";
        }
        if (!safetyDecision.passed() && decision.stageStatus() == com.weaksignal.vpp.backend.marketagent.MarketAgentStageStatus.SUCCESS) {
            return "当前阶段已完成，但全局安全门未允许进入执行链路。";
        }
        return null;
    }

    private String resolveFallbackReason(com.weaksignal.vpp.backend.marketagent.MarketAgentErrorCategory errorCategory) {
        return switch (errorCategory) {
            case PROVIDER_ENDPOINT_NOT_FOUND -> "模型服务端点或模型配置不存在";
            case AUTH_ERROR -> "模型服务鉴权失败";
            case RATE_LIMIT -> "模型服务限流，已采用规则回退";
            case PROVIDER_5XX -> "模型服务异常，已采用规则回退";
            case TIMEOUT -> "模型服务响应超时，已采用规则回退";
            case INVALID_SCHEMA -> "模型输出格式不合法，已采用规则回退";
            case INVALID_ACTION -> "模型输出动作不在白名单内，已采用规则回退";
            default -> "当前阶段采用规则回退";
        };
    }

    /**
     * 生成最终裁决文案，屏蔽旧版运行记录的兼容性细节。
     *
     * @param safetyDecision 安全门裁决
     * @param safetyDecisionLevel 安全门级别
     * @param legacyCompatible 是否存在旧版运行记录补齐
     * @return 面向页面展示的最终裁决说明
     */
    private String resolveOverallDecisionExplanation(
            MarketSafetyDecision safetyDecision,
            String safetyDecisionLevel,
            boolean legacyCompatible
    ) {
        if (legacyCompatible && safetyDecision.passed()) {
            return safetyDecisionLevel + "：旧版运行已由统一裁决层补齐，按约束覆盖与降级执行处理。";
        }
        if (safetyDecision.explanation() != null && safetyDecision.explanation().contains("兼容旧版高风险审批记录")) {
            return safetyDecisionLevel + "：统一裁决层已完成规则兼容，按约束覆盖与降级执行处理。";
        }
        return safetyDecision.explanation();
    }

    /**
     * 生成总览级回退说明。
     *
     * @param fallbackActive 是否存在规则回退或兼容补齐
     * @param legacyCompatible 是否存在旧版运行记录补齐
     * @return 总览级回退说明
     */
    private String resolveSummaryFallbackReason(boolean fallbackActive, boolean legacyCompatible) {
        if (!fallbackActive) {
            return null;
        }
        return legacyCompatible ? "旧版运行已按当前智能体链路补齐" : "存在规则回退决策";
    }

    private String resolveSafetyDecisionLevel(String decisionLevel, boolean passed, boolean fallbackActive) {
        if (decisionLevel != null && !decisionLevel.isBlank()) {
            return decisionLevel;
        }
        if (!passed) {
            return "REJECTED";
        }
        return fallbackActive ? "DEGRADED" : "PASSED";
    }

    private AgentDispatchOverviewResponse.ExecutionTimelineResponse buildExecutionTimeline(
            ExperimentRunResponse comparisonRun,
            com.weaksignal.vpp.backend.api.dto.MarketAgentRunResponse latestAgentRun,
            DispatchPlanExecutionResponse executionResponse,
            List<ExperimentTimelinePointResponse> experimentTimeline
    ) {
        if (comparisonRun == null) {
            return new AgentDispatchOverviewResponse.ExecutionTimelineResponse(
                    null,
                    null,
                    baseTimelineSteps(null, null, null, null, null, null, null)
            );
        }
        OffsetDateTime commandDispatchedAt = executionResponse == null ? null : executionResponse.latestCommandTime();
        OffsetDateTime resourceExecutedAt = executionResponse == null
                ? null
                : executionResponse.commands().stream()
                .filter(command -> "EXECUTED".equals(command.status()))
                .map(DispatchCommandResponse::latestAckTime)
                .filter(value -> value != null)
                .max(Comparator.naturalOrder())
                .orElse(commandDispatchedAt);
        OffsetDateTime recoveryAt = experimentTimeline.stream()
                .filter(point -> point.linkState().equals(LinkState.S4.name()) || point.linkState().equals(LinkState.S0.name()))
                .map(ExperimentTimelinePointResponse::timestamp)
                .max(Comparator.naturalOrder())
                .orElse(null);
        OffsetDateTime resultAt = recoveryAt == null
                ? null
                : "RUNNING".equals(comparisonRun.status()) ? null : Optional.ofNullable(comparisonRun.endedAt()).orElse(recoveryAt);

        List<AgentDispatchOverviewResponse.ExecutionStepResponse> steps = baseTimelineSteps(
                comparisonRun.startedAt(),
                latestAgentRun == null ? null : latestAgentRun.createdAt(),
                latestAgentRun == null ? null : latestAgentRun.createdAt(),
                commandDispatchedAt,
                resourceExecutedAt,
                recoveryAt,
                resultAt
        );
        return new AgentDispatchOverviewResponse.ExecutionTimelineResponse(
                comparisonRun.runId(),
                resolveCurrentStepCode(steps),
                steps
        );
    }

    private List<AgentDispatchOverviewResponse.ExecutionStepResponse> baseTimelineSteps(
            OffsetDateTime faultAt,
            OffsetDateTime diagnosisAt,
            OffsetDateTime safetyAt,
            OffsetDateTime commandAt,
            OffsetDateTime resourceAt,
            OffsetDateTime recoveryAt,
            OffsetDateTime resultAt
    ) {
        return List.of(
                timelineStep("FAULT_OCCURRED", "故障发生", faultAt == null ? "PENDING" : "SUCCEEDED", faultAt, "故障场景已注入。", null),
                timelineStep("AGENT_DIAGNOSIS", "智能体诊断", diagnosisAt == null ? faultAt == null ? "PENDING" : "RUNNING" : "SUCCEEDED", diagnosisAt, "多智能体已输出结构化诊断结论。", null),
                timelineStep("SAFETY_VALIDATION", "安全门校验", safetyAt == null ? diagnosisAt == null ? "PENDING" : "RUNNING" : "SUCCEEDED", safetyAt, "安全门已完成边界校验。", null),
                timelineStep("COMMAND_DISPATCHED", "调度下发", commandAt == null ? safetyAt == null ? "PENDING" : "RUNNING" : "SUCCEEDED", commandAt, "控制指令已下发。", null),
                timelineStep("RESOURCE_EXECUTION", "资源执行", resourceAt == null ? commandAt == null ? "PENDING" : "RUNNING" : "SUCCEEDED", resourceAt, "资源执行结果已回传。", null),
                timelineStep("RECOVERY_SYNC", "恢复同步", recoveryAt == null ? resourceAt == null ? "PENDING" : "RUNNING" : "SUCCEEDED", recoveryAt, "链路恢复同步结果已记录。", null),
                timelineStep("RESULT_COMPARISON", "结果对比", resultAt == null ? recoveryAt == null ? "PENDING" : "RUNNING" : "SUCCEEDED", resultAt, "实验结果对比已生成。", null)
        );
    }

    private AgentDispatchOverviewResponse.ExecutionStepResponse timelineStep(
            String stepCode,
            String stepLabel,
            String status,
            OffsetDateTime timestamp,
            String description,
            String relatedEntityId
    ) {
        return new AgentDispatchOverviewResponse.ExecutionStepResponse(stepCode, stepLabel, status, timestamp, description, relatedEntityId);
    }

    private List<AgentDispatchOverviewResponse.ComparisonMetricResponse> defaultComparisonMetrics() {
        return List.of(
                unavailableComparisonMetric("peak-load", "峰值负荷", "kW"),
                unavailableComparisonMetric("grid-purchase-cost", "购电成本", "元"),
                unavailableComparisonMetric("curtailment", "弃光弃风", "kWh"),
                unavailableComparisonMetric("soc-swing", "SOC波动", "%"),
                unavailableComparisonMetric("recovery-time", "链路恢复时间", "s"),
                unavailableComparisonMetric("execution-success-rate", "执行成功率", "%")
        );
    }

    private List<AgentDispatchOverviewResponse.ComparisonMetricResponse> mapComparisonMetrics(ExperimentComparisonResponse comparison) {
        if (comparison == null || comparison.derivedMetrics() == null || comparison.derivedMetrics().isEmpty()) {
            return defaultComparisonMetrics();
        }
        Map<String, ExperimentComparisonResponse.DerivedMetricResponse> metricMap = comparison.derivedMetrics().stream()
                .collect(Collectors.toMap(
                        ExperimentComparisonResponse.DerivedMetricResponse::metricCode,
                        metric -> metric,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        return List.of(
                toComparisonMetric(metricMap.get("peak-load"), "kW"),
                toComparisonMetric(metricMap.get("grid-purchase-cost"), "元"),
                toComparisonMetric(metricMap.get("curtailment"), "kWh"),
                toComparisonMetric(metricMap.get("soc-swing"), "%"),
                toComparisonMetric(metricMap.get("recovery-time"), "s"),
                toComparisonMetric(metricMap.get("execution-success-rate"), "%")
        );
    }

    private AgentDispatchOverviewResponse.ComparisonMetricResponse toComparisonMetric(
            ExperimentComparisonResponse.DerivedMetricResponse metric,
            String defaultUnit
    ) {
        if (metric == null || !metric.available()) {
            return unavailableComparisonMetric(metric == null ? null : metric.metricCode(), metric == null ? null : metric.metricName(), defaultUnit);
        }
        BigDecimal beforeValue = metric.beforeValue();
        BigDecimal afterValue = metric.duringValue();
        String trendDirection = afterValue.compareTo(beforeValue) > 0 ? "UP" : afterValue.compareTo(beforeValue) < 0 ? "DOWN" : "FLAT";
        boolean improved = metric.improvementValue() != null && metric.improvementValue().compareTo(BigDecimal.ZERO) >= 0;
        return new AgentDispatchOverviewResponse.ComparisonMetricResponse(
                metric.metricCode(),
                metric.metricName(),
                beforeValue,
                afterValue,
                metric.unit() == null ? defaultUnit : metric.unit(),
                metric.improvementValue(),
                metric.improvementRate(),
                trendDirection,
                improved,
                true
        );
    }

    private AgentDispatchOverviewResponse.ComparisonMetricResponse unavailableComparisonMetric(
            String metricCode,
            String metricName,
            String unit
    ) {
        return new AgentDispatchOverviewResponse.ComparisonMetricResponse(
                metricCode,
                metricName,
                null,
                null,
                unit,
                null,
                null,
                "UNKNOWN",
                null,
                false
        );
    }

    private AgentDispatchOverviewResponse.ResourceNodeResponse toResourceNode(
            StationStateResponse station,
            ResourceCapabilityResponse capability,
            ControlMode currentControlMode
    ) {
        ControlMode nodeControlMode = station.linkState() == LinkState.S3 ? ControlMode.LOCAL_AUTONOMY : currentControlMode;
        String statusText = capability != null && capability.storageConstraint() != null
                ? "SOC: " + decimal(capability.storageConstraint().soc(), 1) + "%"
                : "链路: " + station.linkState().name();
        return new AgentDispatchOverviewResponse.ResourceNodeResponse(
                station.stationId(),
                station.stationId(),
                mapNodeType(station.resourceType()),
                station.stationName(),
                decimal(station.activePower(), 1),
                "kW",
                station.linkState(),
                station.online(),
                nodeControlMode,
                false,
                statusText
        );
    }

    private List<AgentDispatchOverviewResponse.EnergyLinkResponse> buildEnergyLinks(List<StationStateResponse> stations) {
        Map<String, StationStateResponse> stationMap = stations.stream()
                .collect(Collectors.toMap(StationStateResponse::stationId, station -> station, (left, right) -> left));
        List<AgentDispatchOverviewResponse.EnergyLinkResponse> links = new ArrayList<>();
        addStationLink(links, stationMap.get("pv-gobi-01"), "SUPPLY");
        addStationLink(links, stationMap.get("wind-ridge-02"), "SUPPLY");
        addStationLink(links, stationMap.get("thermal-plant-06"), "SUPPLY");
        addGridLink(links, stationMap.get("grid-link-00"));
        addStorageLink(links, stationMap.get("storage-hub-03"));
        addStationLink(links, stationMap.get("load-oasis-04"), "CONSUME");
        addStationLink(links, stationMap.get("charge-corridor-05"), "CONSUME");
        return links;
    }

    private void addStationLink(
            List<AgentDispatchOverviewResponse.EnergyLinkResponse> links,
            StationStateResponse station,
            String direction
    ) {
        if (station == null) {
            return;
        }
        links.add(new AgentDispatchOverviewResponse.EnergyLinkResponse(
                "energy-" + station.stationId(),
                "SUPPLY".equals(direction) ? station.stationId() : CENTER_NODE_ID,
                "SUPPLY".equals(direction) ? CENTER_NODE_ID : station.stationId(),
                decimal(station.activePower(), 1),
                "kW",
                direction,
                mapLinkStatus(station.linkState()),
                station.online()
        ));
    }

    private void addGridLink(
            List<AgentDispatchOverviewResponse.EnergyLinkResponse> links,
            StationStateResponse station
    ) {
        if (station == null) {
            return;
        }
        BigDecimal power = decimal(Math.abs(station.activePower()), 1);
        String direction = station.activePower() > 0 ? "SUPPLY" : station.activePower() < 0 ? "CONSUME" : "UNKNOWN";
        links.add(new AgentDispatchOverviewResponse.EnergyLinkResponse(
                "energy-" + station.stationId(),
                "SUPPLY".equals(direction) ? station.stationId() : CENTER_NODE_ID,
                "SUPPLY".equals(direction) ? CENTER_NODE_ID : station.stationId(),
                power,
                "kW",
                direction,
                mapLinkStatus(station.linkState()),
                station.online() && power != null
        ));
    }

    private void addStorageLink(
            List<AgentDispatchOverviewResponse.EnergyLinkResponse> links,
            StationStateResponse station
    ) {
        if (station == null) {
            return;
        }
        String direction = station.activePower() > 0 ? "SUPPLY" : station.activePower() < 0 ? "CONSUME" : "UNKNOWN";
        BigDecimal power = decimal(Math.abs(station.activePower()), 1);
        links.add(new AgentDispatchOverviewResponse.EnergyLinkResponse(
                "energy-" + station.stationId(),
                "SUPPLY".equals(direction) ? station.stationId() : CENTER_NODE_ID,
                "SUPPLY".equals(direction) ? CENTER_NODE_ID : station.stationId(),
                power,
                "kW",
                direction,
                mapLinkStatus(station.linkState()),
                station.online() && station.linkState() != LinkState.S3
        ));
    }

    private DispatchPlanItemResponse resolveCurrentPlanItem(
            DispatchPlanResponse latestPlan,
            DispatchPlanExecutionResponse executionResponse,
            OffsetDateTime now
    ) {
        if (latestPlan == null || latestPlan.items().isEmpty()) {
            return null;
        }
        if (executionResponse != null && executionResponse.activeBatchId() != null) {
            Optional<String> latestItemId = executionResponse.commands().stream()
                    .filter(command -> executionResponse.activeBatchId().equals(command.batchId()))
                    .max(Comparator.comparing(DispatchCommandResponse::sendTime))
                    .map(DispatchCommandResponse::dispatchPlanItemId);
            if (latestItemId.isPresent()) {
                return latestPlan.items().stream()
                        .filter(item -> latestItemId.get().equals(item.itemId()))
                        .findFirst()
                        .orElse(latestPlan.items().getFirst());
            }
        }
        return latestPlan.items().stream()
                .filter(item -> !item.intervalStart().isAfter(now) && item.intervalEnd().isAfter(now))
                .findFirst()
                .orElse(latestPlan.items().getFirst());
    }

    private DispatchPlanTrackingItemResponse resolveCurrentTrackingItem(
            DispatchPlanTrackingResponse trackingResponse,
            DispatchPlanItemResponse currentPlanItem
    ) {
        if (trackingResponse == null || currentPlanItem == null) {
            return null;
        }
        return trackingResponse.items().stream()
                .filter(item -> currentPlanItem.itemId().equals(item.dispatchPlanItemId()))
                .findFirst()
                .orElse(null);
    }

    private ExperimentRunResponse resolveComparisonRun(ExperimentRunResponse currentRun) {
        if (currentRun != null) {
            return currentRun;
        }
        return experimentService.listLatestRuns(10).stream()
                .sorted(Comparator.comparing(ExperimentRunResponse::startedAt).reversed())
                .findFirst()
                .orElse(null);
    }

    private BigDecimal calculateTrackingErrorRate(
            DispatchPlanTrackingItemResponse trackingItem,
            BigDecimal plannedTargetPower
    ) {
        if (trackingItem == null || plannedTargetPower == null || plannedTargetPower.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        /*
         * 跟踪误差率统一按 (实际净功率 - 计划净功率) / |计划净功率| × 100 计算，
         * 用于同时兼容正向供电和负向吸收场景。
         */
        BigDecimal actualTrackingError = BigDecimal.valueOf(trackingItem.actualNetPower()).subtract(plannedTargetPower);
        BigDecimal rate = actualTrackingError.multiply(BigDecimal.valueOf(100))
                .divide(plannedTargetPower.abs(), 2, RoundingMode.HALF_UP);
        return rate;
    }

    private BigDecimal calculateExecutionSuccessRate(List<DispatchCommandResponse> commands) {
        if (commands.isEmpty()) {
            return null;
        }
        long succeededCount = commands.stream().filter(command -> "EXECUTED".equals(command.status())).count();
        return BigDecimal.valueOf(succeededCount * 100.0 / commands.size()).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateReserveSatisfaction(DispatchPlanItemResponse currentPlanItem, List<ResourceCapabilityResponse> capabilities) {
        if (currentPlanItem == null || currentPlanItem.upwardReserve() <= 0) {
            return null;
        }
        double availableReserve = capabilities.stream()
                .filter(capability -> capability.linkState() != LinkState.S3)
                .mapToDouble(capability -> capability.resourceType() == ResourceType.STORAGE && capability.storageConstraint() != null
                        ? capability.storageConstraint().reservedUpPower()
                        : Math.max(0, capability.availableCapacity() - capability.currentPower()))
                .sum();
        if (currentPlanItem.upwardReserve() == 0) {
            return null;
        }
        double reserveRate = Math.min(100.0, availableReserve * 100.0 / currentPlanItem.upwardReserve());
        return decimal(reserveRate, 2);
    }

    private BigDecimal extractComparisonImprovement(ExperimentComparisonResponse comparison, String metricCode) {
        if (comparison == null || comparison.derivedMetrics() == null) {
            return null;
        }
        return comparison.derivedMetrics().stream()
                .filter(metric -> metricCode.equals(metric.metricCode()))
                .map(ExperimentComparisonResponse.DerivedMetricResponse::improvementValue)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private BigDecimal extractComparisonRate(ExperimentComparisonResponse comparison, String metricCode) {
        if (comparison == null || comparison.derivedMetrics() == null) {
            return null;
        }
        return comparison.derivedMetrics().stream()
                .filter(metric -> metricCode.equals(metric.metricCode()))
                .map(ExperimentComparisonResponse.DerivedMetricResponse::improvementRate)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private BigDecimal resolvePlannedTargetPower(
            DispatchPlanItemResponse currentPlanItem,
            DispatchPlanTrackingItemResponse currentTrackingItem,
            DispatchPlanExecutionResponse executionResponse
    ) {
        if (currentTrackingItem != null && currentTrackingItem.plannedNetPower() != 0) {
            return decimal(currentTrackingItem.plannedNetPower(), 1);
        }
        if (currentPlanItem != null && currentPlanItem.netPowerTarget() != 0) {
            return decimal(currentPlanItem.netPowerTarget(), 1);
        }
        return currentPlanItem == null ? null : decimal(currentPlanItem.netPowerTarget(), 1);
    }

    private boolean isCurrentActivePlan(DispatchPlanResponse plan, OffsetDateTime now) {
        return plan.status() == com.weaksignal.vpp.backend.domain.DispatchPlanStatus.ACTIVE
                && !now.isBefore(plan.validFrom())
                && now.isBefore(plan.validUntil());
    }

    private AgentDispatchOverviewResponse.DispatchStrategyItemResponse buildStrategyItem(
            String strategyCode,
            String strategyName,
            String groupType,
            String status,
            BigDecimal targetDeltaPower,
            String unit,
            String reason,
            List<String> affectedResourceIds,
            boolean adoptedBySafetyGate
    ) {
        return new AgentDispatchOverviewResponse.DispatchStrategyItemResponse(
                strategyCode,
                strategyName,
                groupType,
                status,
                targetDeltaPower,
                unit,
                reason,
                affectedResourceIds,
                adoptedBySafetyGate
        );
    }

    private Map<String, DispatchCommandResponse> mapLatestCommandByStation(List<DispatchCommandResponse> commands) {
        return commands.stream()
                .collect(Collectors.toMap(
                        DispatchCommandResponse::stationId,
                        command -> command,
                        (left, right) -> right.sendTime().isAfter(left.sendTime()) ? right : left,
                        LinkedHashMap::new
                ));
    }

    private BigDecimal resolveCommandAdjustment(
            StationStateResponse station,
            DispatchCommandResponse command,
            boolean reductionDirection
    ) {
        if (station == null || command == null) {
            return null;
        }
        double delta = reductionDirection
                ? station.activePower() - command.targetPower()
                : command.targetPower() - station.activePower();
        return delta > 0 ? decimal(delta, 1) : null;
    }

    private BigDecimal resolveStorageChargePower(
            DispatchPlanItemResponse currentPlanItem,
            DispatchCommandResponse storageCommand
    ) {
        if (currentPlanItem != null && currentPlanItem.storageChargePower() > 0) {
            return decimal(currentPlanItem.storageChargePower(), 1);
        }
        if (storageCommand != null && storageCommand.targetPower() < 0) {
            return decimal(Math.abs(storageCommand.targetPower()), 1);
        }
        return null;
    }

    private BigDecimal resolveStorageDischargePower(
            DispatchPlanItemResponse currentPlanItem,
            DispatchCommandResponse storageCommand
    ) {
        if (currentPlanItem != null && currentPlanItem.storageDischargePower() > 0) {
            return decimal(currentPlanItem.storageDischargePower(), 1);
        }
        if (storageCommand != null && storageCommand.targetPower() > 0) {
            return decimal(storageCommand.targetPower(), 1);
        }
        return null;
    }

    private BigDecimal resolveRenewableOutputPower(
            DispatchPlanItemResponse currentPlanItem,
            StationStateResponse photovoltaicStation,
            StationStateResponse windStation
    ) {
        if (currentPlanItem != null && currentPlanItem.netPowerTarget() != 0) {
            return decimal(currentPlanItem.netPowerTarget(), 1);
        }
        double actualOutput = 0;
        if (photovoltaicStation != null) {
            actualOutput += photovoltaicStation.activePower();
        }
        if (windStation != null) {
            actualOutput += windStation.activePower();
        }
        return actualOutput == 0 ? null : decimal(actualOutput, 1);
    }

    private BigDecimal resolveRenewableCurtailmentPower(
            DispatchPlanItemResponse currentPlanItem,
            StationStateResponse photovoltaicStation,
            StationStateResponse windStation
    ) {
        if (currentPlanItem != null && currentPlanItem.renewableCurtailment() > 0) {
            return decimal(currentPlanItem.renewableCurtailment(), 1);
        }
        double curtailedPower = 0;
        curtailedPower += resolveAvailableGap(photovoltaicStation);
        curtailedPower += resolveAvailableGap(windStation);
        return curtailedPower > 0 ? decimal(curtailedPower, 1) : null;
    }

    private double resolveAvailableGap(StationStateResponse station) {
        if (station == null || station.availableCapacity() <= station.activePower()) {
            return 0;
        }
        return station.availableCapacity() - station.activePower();
    }

    private BigDecimal resolveGridImportPower(DispatchPlanItemResponse currentPlanItem, StationStateResponse gridStation) {
        if (currentPlanItem != null && currentPlanItem.gridImportPower() > 0) {
            return decimal(currentPlanItem.gridImportPower(), 1);
        }
        if (gridStation != null && gridStation.activePower() > 0) {
            return decimal(gridStation.activePower(), 1);
        }
        return null;
    }

    private BigDecimal resolveGridExportPower(DispatchPlanItemResponse currentPlanItem, StationStateResponse gridStation) {
        if (currentPlanItem != null && currentPlanItem.gridExportPower() > 0) {
            return decimal(currentPlanItem.gridExportPower(), 1);
        }
        if (gridStation != null && gridStation.activePower() < 0) {
            return decimal(Math.abs(gridStation.activePower()), 1);
        }
        return null;
    }

    private BigDecimal combineSignedDemandResponse(
            BigDecimal loadReductionPower,
            BigDecimal chargingIncreasePower,
            BigDecimal loadActualPower,
            BigDecimal chargingActualPower
    ) {
        if (loadReductionPower == null && chargingIncreasePower == null) {
            if (loadActualPower == null && chargingActualPower == null) {
                return null;
            }
            BigDecimal actualPower = BigDecimal.ZERO;
            if (loadActualPower != null) {
                actualPower = actualPower.add(loadActualPower);
            }
            if (chargingActualPower != null) {
                actualPower = actualPower.add(chargingActualPower);
            }
            return actualPower.setScale(1, RoundingMode.HALF_UP);
        }
        BigDecimal combinedPower = BigDecimal.ZERO;
        if (loadReductionPower != null) {
            combinedPower = combinedPower.subtract(loadReductionPower);
        }
        if (chargingIncreasePower != null) {
            combinedPower = combinedPower.add(chargingIncreasePower);
        }
        return combinedPower.setScale(1, RoundingMode.HALF_UP);
    }

    private String resolveStrategyStatus(
            boolean safetyPassed,
            BigDecimal metricValue,
            DispatchCommandResponse command
    ) {
        if (metricValue == null) {
            return "STANDBY";
        }
        if (!safetyPassed) {
            return "REJECTED";
        }
        if (command == null) {
            return "ADOPTED";
        }
        return switch (command.status()) {
            case "CREATED", "SENT", "ACCEPTED" -> "EXECUTING";
            case "EXECUTED" -> "COMPLETED";
            case "REJECTED", "TIMEOUT", "FAILED" -> "REJECTED";
            default -> "ADOPTED";
        };
    }

    private String resolveCombinedStrategyStatus(
            boolean safetyPassed,
            List<BigDecimal> metricValues,
            List<DispatchCommandResponse> commands
    ) {
        boolean hasMetricValue = metricValues.stream().anyMatch(value -> value != null);
        if (!hasMetricValue) {
            return "STANDBY";
        }
        if (!safetyPassed) {
            return "REJECTED";
        }
        if (commands.stream().anyMatch(command -> command != null && "EXECUTED".equals(command.status()))) {
            return "COMPLETED";
        }
        if (commands.stream().anyMatch(command -> command != null && ("CREATED".equals(command.status()) || "SENT".equals(command.status()) || "ACCEPTED".equals(command.status())))) {
            return "EXECUTING";
        }
        if (commands.stream().anyMatch(command -> command != null && ("REJECTED".equals(command.status()) || "TIMEOUT".equals(command.status()) || "FAILED".equals(command.status())))) {
            return "REJECTED";
        }
        return "ADOPTED";
    }

    private String resolveCurrentStepCode(List<AgentDispatchOverviewResponse.ExecutionStepResponse> steps) {
        return steps.stream()
                .filter(step -> "RUNNING".equals(step.status()))
                .map(AgentDispatchOverviewResponse.ExecutionStepResponse::stepCode)
                .findFirst()
                .orElseGet(() -> steps.stream()
                        .filter(step -> "SUCCEEDED".equals(step.status()))
                        .reduce((left, right) -> right)
                        .map(AgentDispatchOverviewResponse.ExecutionStepResponse::stepCode)
                        .orElse(null));
    }

    private String mapNodeType(ResourceType resourceType) {
        return switch (resourceType) {
            case PHOTOVOLTAIC -> "photovoltaic";
            case WIND -> "wind";
            case THERMAL -> "thermal";
            case GRID -> "grid";
            case STORAGE -> "storage";
            case LOAD -> "load";
            case CHARGING -> "charging";
        };
    }

    private String mapLinkStatus(LinkState linkState) {
        return switch (linkState) {
            case S0 -> "NORMAL";
            case S1, S2 -> "DEGRADED";
            case S3 -> "ISOLATED";
            case S4 -> "RECOVERING";
        };
    }

    private String mapCommandStatus(String status) {
        if (status == null) {
            return "IDLE";
        }
        return switch (status) {
            case "CREATED" -> "PENDING";
            case "SENT" -> "SENT";
            case "ACCEPTED" -> "EXECUTING";
            case "EXECUTED" -> "COMPLETED";
            case "REJECTED", "TIMEOUT", "FAILED" -> "BLOCKED";
            default -> "IDLE";
        };
    }

    private String agentDisplayName(String agentType) {
        return switch (agentType) {
            case "MARKET_INTERFACE" -> "市场接口智能体";
            case "FORECAST" -> "预测智能体";
            case "ENVIRONMENT_ASSESSMENT" -> "环境感知智能体";
            case "MARKET_DISPATCH" -> "市场调度智能体";
            case "RESOURCE_CAPABILITY", "ASSET_DIAGNOSIS" -> "资源能力智能体";
            case "COMMUNICATION_QUALITY" -> "通信质量智能体";
            case "CONTROL_DECISION" -> "控制决策智能体";
            case "SECURITY_AUDIT" -> "安全审计智能体";
            default -> agentType;
        };
    }

    /**
     * 构建当前计划的逐资源真实执行跟踪数据。
     *
     * @param experiment 当前实验
     * @param plan 当前计划
     * @param execution 当前执行批次
     * @param stations 最新场站遥测
     * @return 执行跟踪数据；没有计划时返回空状态而不填充演示数据
     */
    private AgentDispatchOverviewResponse.ResourceTrackingResponse buildResourceTracking(
            ExperimentRunResponse experiment,
            DispatchPlanResponse plan,
            DispatchPlanExecutionResponse execution,
            List<StationStateResponse> stations
    ) {
        DispatchConvergenceRoundEntity round = experiment == null || dispatchConvergenceRoundRepository == null
                ? null
                : dispatchConvergenceRoundRepository.findFirstByExperimentRunIdOrderByRoundNumberDesc(experiment.runId()).orElse(null);
        String historicalConvergenceStatus = round == null ? null : round.getStatus();
        if (execution == null && plan != null) {
            DispatchPlanResponse trackingPlan = plan;
            execution = safeOptional(() -> marketDispatchPlanningService.getExecution(trackingPlan.planId())).orElse(null);
        }
        if (plan == null) {
            DispatchPlanResponse historicalPlan = round == null || round.getReplacementPlanId() == null
                    ? null
                    : safeOptional(() -> marketDispatchPlanningService.getPlan(round.getReplacementPlanId())).orElse(null);
            String currentStatus = historicalPlan != null
                    && (historicalPlan.status() == com.weaksignal.vpp.backend.domain.DispatchPlanStatus.EXPIRED
                    || !historicalPlan.validUntil().isAfter(OffsetDateTime.now(clock)))
                    ? "PLAN_EXPIRED" : "NO_ACTIVE_PLAN";
            return new AgentDispatchOverviewResponse.ResourceTrackingResponse(
                    experiment == null ? null : experiment.runId(), null, null, null,
                    null, null, null, null, currentStatus, 0,
                    mpcConvergenceProperties.requiredSatisfiedSamples(), List.of(), List.of(), List.of(),
                    historicalConvergenceStatus,
                    historicalPlan == null ? null : historicalPlan.status().name(),
                    historicalPlan == null ? null : historicalPlan.validUntil(),
                    false);
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        boolean planValid = plan.status() == com.weaksignal.vpp.backend.domain.DispatchPlanStatus.ACTIVE
                && !now.isBefore(plan.validFrom()) && now.isBefore(plan.validUntil());
        Map<String, StationStateResponse> stationMap = stations.stream().collect(Collectors.toMap(
                StationStateResponse::stationId, station -> station, (left, right) -> left));
        List<DispatchCommandResponse> commands = execution == null ? List.of() : execution.commands();
        List<AgentDispatchOverviewResponse.ResourceTrackingItemResponse> resources = new ArrayList<>();
        List<AgentDispatchOverviewResponse.ResourceTrackingPointResponse> points = new ArrayList<>();
        BigDecimal plannedBalance = BigDecimal.ZERO;
        BigDecimal actualBalance = BigDecimal.ZERO;
        BigDecimal supply = BigDecimal.ZERO;
        BigDecimal consume = BigDecimal.ZERO;
        for (DispatchCommandResponse command : commands) {
            StationStateResponse station = stationMap.get(command.stationId());
            BigDecimal target = decimal(command.targetPower(), 1);
            BigDecimal actual = station == null ? null : decimal(station.activePower(), 1);
            BigDecimal error = actual == null ? null : actual.subtract(target).abs();
            boolean fresh = station != null && station.updatedAt() != null && station.dataAge() <= 45;
            String status = resolveResourceTrackingStatus(planValid, command.status(), fresh, error);
            BigDecimal signedTarget = signedPower(station == null ? null : station.resourceType(), target);
            plannedBalance = plannedBalance.add(signedTarget);
            if (signedTarget.signum() >= 0) supply = supply.add(signedTarget); else consume = consume.add(signedTarget.abs());
            if (actual != null) actualBalance = actualBalance.add(signedPower(station == null ? null : station.resourceType(), actual));
            resources.add(new AgentDispatchOverviewResponse.ResourceTrackingItemResponse(
                    command.stationId(), station == null ? "UNKNOWN" : station.resourceType().name(),
                    station == null ? null : station.linkState(), target, actual, error, decimal(mpcConvergenceProperties.defaultResourceTrackingToleranceKw(), 1),
                    status, command.status(), command.sendTime(), station == null ? null : station.updatedAt(), fresh,
                    round == null ? 0 : round.getConsecutiveSuccessCount(), mpcConvergenceProperties.requiredSatisfiedSamples()));
            if (planValid && actual != null && station.updatedAt() != null
                    && !station.updatedAt().isBefore(plan.validFrom())
                    && station.updatedAt().isBefore(plan.validUntil())) {
                points.add(new AgentDispatchOverviewResponse.ResourceTrackingPointResponse(command.stationId(), station.updatedAt(), target, actual));
            }
        }
        List<OffsetDateTime> boundaries = experiment == null || dispatchConvergenceRoundRepository == null ? List.of()
                : dispatchConvergenceRoundRepository.findByExperimentRunIdOrderByRoundNumberAsc(experiment.runId()).stream()
                .map(DispatchConvergenceRoundEntity::getStartedAt).toList();
        boolean currentlyWithinTolerance = planValid && !resources.isEmpty() && resources.stream()
                .allMatch(resource -> "TRACKING".equals(resource.trackingStatus())
                        || "TARGET_REACHED".equals(resource.trackingStatus()));
        boolean roundMatchesPlan = round != null && (plan.planId().equals(round.getSourcePlanId())
                || plan.planId().equals(round.getReplacementPlanId()));
        String currentConvergenceStatus;
        if (!planValid) {
            currentConvergenceStatus = "PLAN_EXPIRED";
        } else if (resources.stream().anyMatch(resource -> "REOPTIMIZATION_REQUIRED".equals(resource.trackingStatus()))) {
            currentConvergenceStatus = "REOPTIMIZATION_REQUIRED";
        } else if (resources.stream().anyMatch(resource -> "WAITING_ACK".equals(resource.trackingStatus()))) {
            currentConvergenceStatus = "WAITING_ACK";
        } else if (resources.stream().anyMatch(resource -> "WAITING_TELEMETRY".equals(resource.trackingStatus()))) {
            currentConvergenceStatus = "WAITING_TELEMETRY";
        } else if (currentlyWithinTolerance && roundMatchesPlan && "TARGET_REACHED".equals(historicalConvergenceStatus)) {
            currentConvergenceStatus = "TARGET_REACHED";
        } else {
            currentConvergenceStatus = "TRACKING";
        }
        return new AgentDispatchOverviewResponse.ResourceTrackingResponse(
                experiment == null ? null : experiment.runId(), round == null ? null : round.getRoundNumber(), plan.planId(),
                execution == null ? null : execution.activeBatchId(), supply, consume, plannedBalance.abs(), actualBalance.abs(),
                currentConvergenceStatus, roundMatchesPlan ? round.getConsecutiveSuccessCount() : 0,
                mpcConvergenceProperties.requiredSatisfiedSamples(), resources, points, boundaries,
                historicalConvergenceStatus, plan.status().name(), plan.validUntil(), currentlyWithinTolerance);
    }

    private String resolveResourceTrackingStatus(boolean planValid, String commandStatus, boolean fresh, BigDecimal error) {
        if (!planValid) return "PLAN_EXPIRED";
        if (!"EXECUTED".equals(commandStatus)) return "WAITING_ACK";
        if (!fresh) return "WAITING_TELEMETRY";
        return error != null && error.compareTo(decimal(mpcConvergenceProperties.defaultResourceTrackingToleranceKw(), 1)) <= 0 ? "TRACKING" : "REOPTIMIZATION_REQUIRED";
    }

    private BigDecimal signedPower(com.weaksignal.vpp.backend.domain.ResourceType resourceType, BigDecimal power) {
        if (resourceType == null) return BigDecimal.ZERO;
        return switch (resourceType) {
            case PHOTOVOLTAIC, WIND, THERMAL, GRID, STORAGE -> power;
            case LOAD, CHARGING -> power.abs().negate();
        };
    }

    private boolean readBoolean(Object value) {
        return value instanceof Boolean booleanValue && booleanValue;
    }

    private BigDecimal decimal(double value, int scale) {
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP);
    }

    private <T> Optional<T> safeOptional(Supplier<T> supplier) {
        try {
            return Optional.ofNullable(supplier.get());
        } catch (ApiException exception) {
            if (exception.getStatus() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            }
            throw exception;
        }
    }
}

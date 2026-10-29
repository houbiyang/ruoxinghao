package com.weaksignal.vpp.backend.api.dto;

import com.weaksignal.vpp.backend.domain.ControlMode;
import com.weaksignal.vpp.backend.domain.LinkState;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 多智能体调度中心聚合总览响应。
 *
 * @param systemStatus 系统状态
 * @param dispatchStrategy 调度策略
 * @param resourceTopology 资源拓扑
 * @param dispatchSummary 调度结果摘要
 * @param experimentStatus 实验状态
 * @param comparisonSummary 实验对比摘要
 * @param agentDecisionSummary 智能体裁决摘要
 * @param executionTimeline 执行时间线
 * @param resourceTracking 逐资源执行跟踪
 */
public record AgentDispatchOverviewResponse(
        SystemStatusResponse systemStatus,
        DispatchStrategyResponse dispatchStrategy,
        ResourceTopologyResponse resourceTopology,
        DispatchSummaryResponse dispatchSummary,
        ExperimentStatusResponse experimentStatus,
        ComparisonSummaryResponse comparisonSummary,
        AgentDecisionSummaryResponse agentDecisionSummary,
        ExecutionTimelineResponse executionTimeline,
        ResourceTrackingResponse resourceTracking
) {

    /**
     * 兼容未包含逐资源追踪数据的既有概览响应构造调用。
     */
    public AgentDispatchOverviewResponse(
            SystemStatusResponse systemStatus,
            DispatchStrategyResponse dispatchStrategy,
            ResourceTopologyResponse resourceTopology,
            DispatchSummaryResponse dispatchSummary,
            ExperimentStatusResponse experimentStatus,
            ComparisonSummaryResponse comparisonSummary,
            AgentDecisionSummaryResponse agentDecisionSummary,
            ExecutionTimelineResponse executionTimeline
    ) {
        this(systemStatus, dispatchStrategy, resourceTopology, dispatchSummary, experimentStatus,
                comparisonSummary, agentDecisionSummary, executionTimeline, null);
    }

    /** 逐资源执行跟踪响应。 */
    public record ResourceTrackingResponse(
            String experimentRunId, Integer optimizationRound, String planId, String executionBatchId,
            BigDecimal totalSupplyPower, BigDecimal totalConsumePower, BigDecimal plannedBalanceError,
            BigDecimal actualBalanceError, String convergenceStatus, int consecutiveSatisfiedSamples,
            int requiredSatisfiedSamples, List<ResourceTrackingItemResponse> resources,
            List<ResourceTrackingPointResponse> points, List<OffsetDateTime> optimizationBoundaries,
            String historicalConvergenceStatus, String planStatus, OffsetDateTime planValidUntil,
            boolean currentlyWithinTolerance
    ) { }

    /** 单资源实时执行跟踪响应。 */
    public record ResourceTrackingItemResponse(
            String stationId, String resourceType, LinkState linkState, BigDecimal targetPower,
            BigDecimal actualPower, BigDecimal absoluteError, BigDecimal tolerance, String trackingStatus,
            String commandStatus, OffsetDateTime targetGeneratedAt, OffsetDateTime actualReceivedAt,
            boolean telemetryFresh, int consecutiveSatisfiedSamples, int requiredSatisfiedSamples
    ) { }

    /** 单资源真实遥测跟踪点。 */
    public record ResourceTrackingPointResponse(String stationId, OffsetDateTime timestamp, BigDecimal targetPower, BigDecimal actualPower) { }

    /**
     * 系统状态响应。
     *
     * @param aiEnabled AI 功能是否启用
     * @param aiAvailable AI Provider 是否可用
     * @param aiProvider AI Provider 编码
     * @param controlMode 当前控制模式
     * @param activeScenario 当前场景编码
     * @param dataSourceStatus 数据源状态
     * @param lastUpdatedAt 聚合更新时间
     */
    public record SystemStatusResponse(
            boolean aiEnabled,
            boolean aiAvailable,
            String aiProvider,
            ControlMode controlMode,
            String activeScenario,
            String dataSourceStatus,
            OffsetDateTime lastUpdatedAt
    ) {
    }

    /**
     * 调度策略响应。
     *
     * @param currentStrategyTitle 当前策略标题
     * @param strategyMode 策略模式说明
     * @param loadSideActions 负荷侧动作组
     * @param generationSideActions 电源侧动作组
     * @param storageActions 储能动作组
     * @param gridActions 电网交互组
     */
    public record DispatchStrategyResponse(
            String currentStrategyTitle,
            String strategyMode,
            DispatchStrategyGroupResponse loadSideActions,
            DispatchStrategyGroupResponse generationSideActions,
            DispatchStrategyGroupResponse storageActions,
            DispatchStrategyGroupResponse gridActions
    ) {
    }

    /**
     * 调度策略分组响应。
     *
     * @param groupType 分组类型
     * @param groupTitle 分组标题
     * @param groupSubtitle 分组副标题
     * @param items 分组动作集合
     */
    public record DispatchStrategyGroupResponse(
            String groupType,
            String groupTitle,
            String groupSubtitle,
            List<DispatchStrategyItemResponse> items
    ) {
    }

    /**
     * 调度策略动作响应。
     *
     * @param strategyCode 策略编码
     * @param strategyName 策略名称
     * @param groupType 分组类型
     * @param status 当前状态
     * @param targetDeltaPower 目标调节功率
     * @param unit 单位
     * @param reason 原因说明
     * @param affectedResourceIds 受影响资源标识
     * @param adoptedBySafetyGate 是否被安全门采纳
     */
    public record DispatchStrategyItemResponse(
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
    }

    /**
     * 资源拓扑响应。
     *
     * @param centerNode 中心节点
     * @param resourceNodes 资源节点集合
     * @param energyLinks 能量流集合
     * @param controlLinks 控制流集合
     * @param balanceSummary 能量平衡摘要
     * @param legend 图例集合
     */
    public record ResourceTopologyResponse(
            CenterNodeResponse centerNode,
            List<ResourceNodeResponse> resourceNodes,
            List<EnergyLinkResponse> energyLinks,
            List<ControlLinkResponse> controlLinks,
            BalanceSummaryResponse balanceSummary,
            List<LegendItemResponse> legend
    ) {
    }

    /**
     * 中心节点响应。
     *
     * @param nodeId 节点标识
     * @param displayName 展示名称
     * @param controlMode 当前控制模式
     * @param systemStatus 系统状态
     * @param statusText 状态说明
     */
    public record CenterNodeResponse(
            String nodeId,
            String displayName,
            ControlMode controlMode,
            String systemStatus,
            String statusText
    ) {
    }

    /**
     * 资源节点响应。
     *
     * @param nodeId 节点标识
     * @param stationId 场站标识
     * @param nodeType 节点类型
     * @param displayName 展示名称
     * @param activePower 实时功率
     * @param unit 单位
     * @param linkState 链路状态
     * @param online 是否在线
     * @param controlMode 控制模式
     * @param selected 是否选中
     * @param statusText 状态说明
     */
    public record ResourceNodeResponse(
            String nodeId,
            String stationId,
            String nodeType,
            String displayName,
            BigDecimal activePower,
            String unit,
            LinkState linkState,
            boolean online,
            ControlMode controlMode,
            boolean selected,
            String statusText
    ) {
    }

    /**
     * 能量流响应。
     *
     * @param linkId 连线标识
     * @param sourceNodeId 源节点
     * @param targetNodeId 目标节点
     * @param power 功率值
     * @param unit 单位
     * @param direction 流向
     * @param status 状态
     * @param active 是否激活
     */
    public record EnergyLinkResponse(
            String linkId,
            String sourceNodeId,
            String targetNodeId,
            BigDecimal power,
            String unit,
            String direction,
            String status,
            boolean active
    ) {
    }

    /**
     * 控制流响应。
     *
     * @param linkId 连线标识
     * @param sourceNodeId 源节点
     * @param targetNodeId 目标节点
     * @param commandStatus 指令状态
     * @param linkState 链路状态
     * @param active 是否激活
     */
    public record ControlLinkResponse(
            String linkId,
            String sourceNodeId,
            String targetNodeId,
            String commandStatus,
            LinkState linkState,
            boolean active
    ) {
    }

    /**
     * 能量平衡摘要响应。
     *
     * @param totalSupplyPower 总供给功率
     * @param totalConsumePower 总用能功率
     * @param netBalancePower 净平衡功率
     * @param unit 单位
     */
    public record BalanceSummaryResponse(
            BigDecimal totalSupplyPower,
            BigDecimal totalConsumePower,
            BigDecimal netBalancePower,
            String unit
    ) {
    }

    /**
     * 图例项响应。
     *
     * @param label 图例名称
     * @param type 图例类型
     * @param color 图例颜色
     */
    public record LegendItemResponse(
            String label,
            String type,
            String color
    ) {
    }

    /**
     * 调度摘要响应。
     *
     * @param plannedTargetPower 计划目标功率指标
     * @param actualPower 实际功率指标
     * @param trackingErrorRate 跟踪误差率指标
     * @param revenueImprovement 收益改善指标
     * @param curtailmentReduction 弃电改善指标
     * @param reserveSatisfactionRate 备用满足率指标
     * @param executionSuccessRate 执行成功率指标
     * @param activePlanId 活动计划标识
     * @param planVersion 计划版本
     */
    public record DispatchSummaryResponse(
            MetricValueResponse plannedTargetPower,
            MetricValueResponse actualPower,
            MetricValueResponse trackingErrorRate,
            MetricValueResponse revenueImprovement,
            MetricValueResponse curtailmentReduction,
            MetricValueResponse reserveSatisfactionRate,
            MetricValueResponse executionSuccessRate,
            String activePlanId,
            String planVersion
    ) {
    }

    /**
     * 通用指标响应。
     *
     * @param label 指标名称
     * @param value 指标值
     * @param unit 单位
     * @param improvementValue 改善值
     * @param improvementRate 改善率
     * @param trendDirection 趋势方向
     * @param higherIsBetter 是否越高越好
     * @param available 是否有数据
     */
    public record MetricValueResponse(
            String label,
            BigDecimal value,
            String unit,
            BigDecimal improvementValue,
            BigDecimal improvementRate,
            String trendDirection,
            Boolean higherIsBetter,
            boolean available
    ) {
    }

    /**
     * 实验状态响应。
     *
     * @param currentRunId 当前运行标识
     * @param currentScenarioCode 当前场景编码
     * @param currentScenarioName 当前场景名称
     * @param running 是否运行中
     * @param durationSeconds 持续时长
     * @param elapsedSeconds 已运行时长
     * @param affectedStationIds 受影响场站
     * @param startedAt 启动时间
     * @param expectedLinkState 目标链路状态
     */
    public record ExperimentStatusResponse(
            String currentRunId,
            String currentScenarioCode,
            String currentScenarioName,
            boolean running,
            Integer durationSeconds,
            Long elapsedSeconds,
            List<String> affectedStationIds,
            OffsetDateTime startedAt,
            LinkState expectedLinkState
    ) {
    }

    /**
     * 实验对比摘要响应。
     *
     * @param runId 对应运行标识
     * @param scenarioCode 对应场景编码
     * @param scenarioName 对应场景名称
     * @param metrics 对比指标集合
     */
    public record ComparisonSummaryResponse(
            String runId,
            String scenarioCode,
            String scenarioName,
            List<ComparisonMetricResponse> metrics
    ) {
    }

    /**
     * 对比指标响应。
     *
     * @param metricCode 指标编码
     * @param metricName 指标名称
     * @param beforeValue 调度前数值
     * @param afterValue 调度后数值
     * @param unit 单位
     * @param improvementValue 改善值
     * @param improvementRate 改善率
     * @param trendDirection 趋势方向
     * @param improved 是否改善
     * @param available 是否有数据
     */
    public record ComparisonMetricResponse(
            String metricCode,
            String metricName,
            BigDecimal beforeValue,
            BigDecimal afterValue,
            String unit,
            BigDecimal improvementValue,
            BigDecimal improvementRate,
            String trendDirection,
            Boolean improved,
            boolean available
    ) {
    }

    /**
     * 智能体裁决摘要响应。
     *
     * @param rows 智能体行集合
     * @param overallDecision 综合裁决
     * @param safetyDecisionLevel 安全门裁决级别
     * @param safetyGatePassed 安全门结果
     * @param ruleFallbackActive 是否启用规则回退
     * @param decisionRunId 决策运行标识
     * @param modeLabel 模式说明
     * @param fallbackReason 回退原因
     */
    public record AgentDecisionSummaryResponse(
            List<AgentDecisionRowResponse> rows,
            String overallDecision,
            String safetyDecisionLevel,
            Boolean safetyGatePassed,
            boolean ruleFallbackActive,
            String decisionRunId,
            String modeLabel,
            String fallbackReason
    ) {
    }

    /**
     * 智能体裁决行响应。
     *
     * @param agentType 智能体类型
     * @param agentName 智能体名称
     * @param available 是否可用
     * @param confidence 置信度
     * @param suggestedAction 建议动作
     * @param finalDecision 最终裁决
     * @param adopted 是否采用
     * @param evidenceCount 证据数量
     * @param fallbackReason 回退原因
     * @param stageStatus 阶段执行状态
     * @param retryCount 已执行重试次数
     * @param errorCategory 错误分类
     * @param inputVersion 输入版本摘要
     * @param outputVersion 输出版本
     */
    public record AgentDecisionRowResponse(
            String agentType,
            String agentName,
            boolean available,
            BigDecimal confidence,
            String suggestedAction,
            String finalDecision,
            boolean adopted,
            int evidenceCount,
            String fallbackReason,
            String stageStatus,
            int retryCount,
            String errorCategory,
            String inputVersion,
            String outputVersion
    ) {
    }

    /**
     * 执行时间线响应。
     *
     * @param runId 关联运行标识
     * @param currentStepCode 当前步骤编码
     * @param steps 步骤集合
     */
    public record ExecutionTimelineResponse(
            String runId,
            String currentStepCode,
            List<ExecutionStepResponse> steps
    ) {
    }

    /**
     * 执行时间线步骤响应。
     *
     * @param stepCode 步骤编码
     * @param stepLabel 步骤名称
     * @param status 步骤状态
     * @param timestamp 发生时间
     * @param description 说明
     * @param relatedEntityId 关联实体标识
     */
    public record ExecutionStepResponse(
            String stepCode,
            String stepLabel,
            String status,
            OffsetDateTime timestamp,
            String description,
            String relatedEntityId
    ) {
    }
}

package com.weaksignal.vpp.backend.api.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * 实验摘要响应。
 *
 * @param runId 运行标识
 * @param scenarioCode 场景编码
 * @param scenarioName 场景名称
 * @param status 运行状态
 * @param startedAt 启动时间
 * @param stoppedAt 停止时间
 * @param durationSeconds 持续秒数
 * @param targetStationIds 目标场站
 * @param sampleCount 遥测样本数量
 * @param eventCount 事件数量
 * @param dominantState 主要链路状态
 * @param orchestrationStatus 闭环编排状态
 * @param orchestrationTriggeredAt 闭环触发时间
 * @param orchestrationCompletedAt 闭环完成时间
 * @param dispatchContextSnapshotId 调度上下文快照标识
 * @param dispatchPlanId 调度计划标识
 * @param agentRunId 智能体运行标识
 * @param planStatus 调度计划状态
 * @param executionBatchId 执行批次标识
 * @param safetyGatePassed 安全门是否通过
 * @param failureReason 闭环失败原因
 * @param controlStrategy 实验控制策略
 * @param randomSeed 随机种子
 * @param controllerType 实际控制器类型
 * @param convergenceStatus 遥测收敛状态
 * @param convergenceRound 收敛轮次
 * @param plannedBalanceErrorKw 计划供需误差
 * @param actualBalanceErrorKw 实际供需误差
 * @param messageSentCount 一致性消息发送数
 * @param messageLostCount 一致性消息丢失数
 * @param constraintViolationCount 约束违规数
 * @param commandTotalCount 控制命令总数
 * @param commandExecutedCount 已执行命令数
 * @param commandFailedCount 明确失败命令数
 * @param commandPendingCount 未进入终态命令数
 * @param localAutonomyEvidence S3 本地自治与 S4 重新入图证据
 */
public record ExperimentSummaryResponse(
        String runId,
        String scenarioCode,
        String scenarioName,
        String status,
        OffsetDateTime startedAt,
        OffsetDateTime stoppedAt,
        long durationSeconds,
        List<String> targetStationIds,
        long sampleCount,
        long eventCount,
        String dominantState,
        String orchestrationStatus,
        OffsetDateTime orchestrationTriggeredAt,
        OffsetDateTime orchestrationCompletedAt,
        String dispatchContextSnapshotId,
        String dispatchPlanId,
        String agentRunId,
        String planStatus,
        String executionBatchId,
        Boolean safetyGatePassed,
        String failureReason,
        String controlStrategy,
        Long randomSeed,
        String controllerType,
        String convergenceStatus,
        Integer convergenceRound,
        Double plannedBalanceErrorKw,
        Double actualBalanceErrorKw,
        Long messageSentCount,
        Long messageLostCount,
        Integer constraintViolationCount,
        int commandTotalCount,
        int commandExecutedCount,
        int commandFailedCount,
        int commandPendingCount,
        Map<String, Object> localAutonomyEvidence
) {
}

package com.weaksignal.vpp.backend.api.dto;

import com.weaksignal.vpp.backend.marketagent.MarketAgentDecision;
import com.weaksignal.vpp.backend.marketagent.MarketSafetyDecision;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 市场调度多智能体运行响应。
 *
 * @param runId 运行标识
 * @param snapshotId 输入快照标识
 * @param dispatchPlanId 关联计划标识
 * @param conservativePlan 是否保守计划
 * @param decisions 各智能体结构化结论
 * @param safetyDecision 安全门裁决
 * @param createdAt 生成时间
 * @param workflowVersion 工作流版本
 * @param agentSetVersion 智能体集合版本
 * @param stageRecordsComplete 是否已记录完整阶段
 * @param legacyRun 是否为历史兼容运行
 */
public record MarketAgentRunResponse(
        String runId,
        String snapshotId,
        String dispatchPlanId,
        boolean conservativePlan,
        List<MarketAgentDecision> decisions,
        MarketSafetyDecision safetyDecision,
        OffsetDateTime createdAt,
        String workflowVersion,
        String agentSetVersion,
        boolean stageRecordsComplete,
        boolean legacyRun
) {

    /**
     * 兼容旧版运行记录构造，旧记录仅用于历史展示。
     *
     * @param runId 运行标识
     * @param snapshotId 输入快照标识
     * @param dispatchPlanId 关联计划标识
     * @param conservativePlan 是否保守计划
     * @param decisions 各智能体结构化结论
     * @param safetyDecision 安全门裁决
     * @param createdAt 生成时间
     */
    public MarketAgentRunResponse(
            String runId,
            String snapshotId,
            String dispatchPlanId,
            boolean conservativePlan,
            List<MarketAgentDecision> decisions,
            MarketSafetyDecision safetyDecision,
            OffsetDateTime createdAt
    ) {
        this(runId, snapshotId, dispatchPlanId, conservativePlan, decisions, safetyDecision, createdAt,
                "legacy", "legacy", false, true);
    }
}

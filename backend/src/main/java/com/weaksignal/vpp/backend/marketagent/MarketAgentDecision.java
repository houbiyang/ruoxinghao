package com.weaksignal.vpp.backend.marketagent;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 市场调度智能体结构化候选结论。
 *
 * @param decisionId 决策标识
 * @param agentType 智能体类型
 * @param snapshotId 输入快照标识
 * @param generatedAt 生成时间
 * @param validUntil 有效期
 * @param confidence 置信度
 * @param evidenceRefs 证据引用
 * @param ruleVersion 规则版本
 * @param modelProvider 模型提供方
 * @param modelVersion 模型版本
 * @param candidateActions 候选动作
 * @param riskLevel 风险等级
 * @param requiresHumanApproval 兼容字段，市场调度闭环固定为 false
 * @param explanation 结构化解释
 * @param fallbackUsed 是否使用规则回退
 * @param stageStatus 阶段执行状态
 * @param retryCount 已执行重试次数
 * @param errorCategory 错误分类
 * @param inputVersion 输入版本摘要
 * @param outputVersion 输出版本
 * @param nextStageAllowed 是否允许进入下一阶段
 * @param providerStatusCode 上游模型服务 HTTP 状态码
 * @param providerErrorCode 上游模型服务错误码或本地传输异常类型
 * @param providerErrorMessage 脱敏后的上游错误摘要
 */
public record MarketAgentDecision(
        String decisionId,
        MarketAgentType agentType,
        String snapshotId,
        OffsetDateTime generatedAt,
        OffsetDateTime validUntil,
        double confidence,
        List<String> evidenceRefs,
        String ruleVersion,
        String modelProvider,
        String modelVersion,
        List<String> candidateActions,
        String riskLevel,
        boolean requiresHumanApproval,
        String explanation,
        boolean fallbackUsed,
        MarketAgentStageStatus stageStatus,
        int retryCount,
        MarketAgentErrorCategory errorCategory,
        String inputVersion,
        String outputVersion,
        boolean nextStageAllowed,
        Integer providerStatusCode,
        String providerErrorCode,
        String providerErrorMessage
) {

    /**
     * 兼容未记录上游调用诊断信息的完整决策构造。
     *
     * @param decisionId 决策标识
     * @param agentType 智能体类型
     * @param snapshotId 输入快照标识
     * @param generatedAt 生成时间
     * @param validUntil 有效期
     * @param confidence 置信度
     * @param evidenceRefs 证据引用
     * @param ruleVersion 规则版本
     * @param modelProvider 模型提供方
     * @param modelVersion 模型版本
     * @param candidateActions 候选动作
     * @param riskLevel 风险等级
     * @param requiresHumanApproval 兼容审批标记
     * @param explanation 结构化解释
     * @param fallbackUsed 是否使用规则回退
     * @param stageStatus 阶段执行状态
     * @param retryCount 已执行重试次数
     * @param errorCategory 错误分类
     * @param inputVersion 输入版本摘要
     * @param outputVersion 输出版本
     * @param nextStageAllowed 是否允许进入下一阶段
     */
    public MarketAgentDecision(
            String decisionId,
            MarketAgentType agentType,
            String snapshotId,
            OffsetDateTime generatedAt,
            OffsetDateTime validUntil,
            double confidence,
            List<String> evidenceRefs,
            String ruleVersion,
            String modelProvider,
            String modelVersion,
            List<String> candidateActions,
            String riskLevel,
            boolean requiresHumanApproval,
            String explanation,
            boolean fallbackUsed,
            MarketAgentStageStatus stageStatus,
            int retryCount,
            MarketAgentErrorCategory errorCategory,
            String inputVersion,
            String outputVersion,
            boolean nextStageAllowed
    ) {
        this(
                decisionId, agentType, snapshotId, generatedAt, validUntil, confidence, evidenceRefs, ruleVersion,
                modelProvider, modelVersion, candidateActions, riskLevel, requiresHumanApproval, explanation, fallbackUsed,
                stageStatus, retryCount, errorCategory, inputVersion, outputVersion, nextStageAllowed,
                null, null, null
        );
    }

    /**
     * 兼容旧版阶段协议缺失的决策构造。
     *
     * @param decisionId 决策标识
     * @param agentType 智能体类型
     * @param snapshotId 输入快照标识
     * @param generatedAt 生成时间
     * @param validUntil 有效期
     * @param confidence 置信度
     * @param evidenceRefs 证据引用
     * @param ruleVersion 规则版本
     * @param modelProvider 模型提供方
     * @param modelVersion 模型版本
     * @param candidateActions 候选动作
     * @param riskLevel 风险等级
     * @param requiresHumanApproval 兼容审批标记
     * @param explanation 结构化解释
     * @param fallbackUsed 是否使用规则回退
     */
    public MarketAgentDecision(
            String decisionId,
            MarketAgentType agentType,
            String snapshotId,
            OffsetDateTime generatedAt,
            OffsetDateTime validUntil,
            double confidence,
            List<String> evidenceRefs,
            String ruleVersion,
            String modelProvider,
            String modelVersion,
            List<String> candidateActions,
            String riskLevel,
            boolean requiresHumanApproval,
            String explanation,
            boolean fallbackUsed
    ) {
        this(
                decisionId, agentType, snapshotId, generatedAt, validUntil, confidence, evidenceRefs, ruleVersion,
                modelProvider, modelVersion, candidateActions, riskLevel, requiresHumanApproval, explanation, fallbackUsed,
                fallbackUsed ? MarketAgentStageStatus.RULE_FALLBACK : MarketAgentStageStatus.SUCCESS,
                0,
                MarketAgentErrorCategory.NONE,
                "legacy-input",
                "legacy-output",
                !fallbackUsed
        );
    }
}

package com.weaksignal.vpp.backend.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/**
 * 市场调度多智能体单条决策实体。
 */
@Entity
@Table(name = "market_agent_decision")
public class MarketAgentDecisionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "decision_id", nullable = false, unique = true, length = 64)
    private String decisionId;

    @Column(name = "run_id", nullable = false, length = 64)
    private String runId;

    @Column(name = "agent_type", nullable = false, length = 64)
    private String agentType;

    @Column(name = "snapshot_id", nullable = false, length = 64)
    private String snapshotId;

    @Column(name = "generated_at", nullable = false)
    private OffsetDateTime generatedAt;

    @Column(name = "valid_until", nullable = false)
    private OffsetDateTime validUntil;

    @Column(nullable = false)
    private double confidence;

    @Column(name = "evidence_refs_json", nullable = false, columnDefinition = "TEXT")
    private String evidenceRefsJson;

    @Column(name = "rule_version", nullable = false, length = 64)
    private String ruleVersion;

    @Column(name = "model_provider", nullable = false, length = 32)
    private String modelProvider;

    @Column(name = "model_version", nullable = false, length = 128)
    private String modelVersion;

    @Column(name = "candidate_actions_json", nullable = false, columnDefinition = "TEXT")
    private String candidateActionsJson;

    @Column(name = "risk_level", nullable = false, length = 16)
    private String riskLevel;

    @Column(name = "requires_human_approval", nullable = false)
    private boolean requiresHumanApproval;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String explanation;

    @Column(name = "fallback_used", nullable = false)
    private boolean fallbackUsed;

    @Column(name = "stage_status", nullable = false, length = 32)
    private String stageStatus;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "error_category", nullable = false, length = 32)
    private String errorCategory;

    @Column(name = "provider_status_code")
    private Integer providerStatusCode;

    @Column(name = "provider_error_code", length = 64)
    private String providerErrorCode;

    @Column(name = "provider_error_message", length = 512)
    private String providerErrorMessage;

    @Column(name = "input_version", nullable = false, columnDefinition = "TEXT")
    private String inputVersion;

    @Column(name = "output_version", nullable = false, length = 128)
    private String outputVersion;

    @Column(name = "next_stage_allowed", nullable = false)
    private boolean nextStageAllowed;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    /**
     * 返回主键。
     *
     * @return 主键
     */
    public Long getId() {
        return id;
    }

    /**
     * 返回决策标识。
     *
     * @return 决策标识
     */
    public String getDecisionId() {
        return decisionId;
    }

    /**
     * 设置决策标识。
     *
     * @param decisionId 决策标识
     */
    public void setDecisionId(String decisionId) {
        this.decisionId = decisionId;
    }

    /**
     * 返回运行标识。
     *
     * @return 运行标识
     */
    public String getRunId() {
        return runId;
    }

    /**
     * 设置运行标识。
     *
     * @param runId 运行标识
     */
    public void setRunId(String runId) {
        this.runId = runId;
    }

    /**
     * 返回智能体类型。
     *
     * @return 智能体类型
     */
    public String getAgentType() {
        return agentType;
    }

    /**
     * 设置智能体类型。
     *
     * @param agentType 智能体类型
     */
    public void setAgentType(String agentType) {
        this.agentType = agentType;
    }

    /**
     * 返回输入快照标识。
     *
     * @return 输入快照标识
     */
    public String getSnapshotId() {
        return snapshotId;
    }

    /**
     * 设置输入快照标识。
     *
     * @param snapshotId 输入快照标识
     */
    public void setSnapshotId(String snapshotId) {
        this.snapshotId = snapshotId;
    }

    /**
     * 返回生成时间。
     *
     * @return 生成时间
     */
    public OffsetDateTime getGeneratedAt() {
        return generatedAt;
    }

    /**
     * 设置生成时间。
     *
     * @param generatedAt 生成时间
     */
    public void setGeneratedAt(OffsetDateTime generatedAt) {
        this.generatedAt = generatedAt;
    }

    /**
     * 返回有效截止时间。
     *
     * @return 有效截止时间
     */
    public OffsetDateTime getValidUntil() {
        return validUntil;
    }

    /**
     * 设置有效截止时间。
     *
     * @param validUntil 有效截止时间
     */
    public void setValidUntil(OffsetDateTime validUntil) {
        this.validUntil = validUntil;
    }

    /**
     * 返回置信度。
     *
     * @return 置信度
     */
    public double getConfidence() {
        return confidence;
    }

    /**
     * 设置置信度。
     *
     * @param confidence 置信度
     */
    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    /**
     * 返回证据引用 JSON。
     *
     * @return 证据引用 JSON
     */
    public String getEvidenceRefsJson() {
        return evidenceRefsJson;
    }

    /**
     * 设置证据引用 JSON。
     *
     * @param evidenceRefsJson 证据引用 JSON
     */
    public void setEvidenceRefsJson(String evidenceRefsJson) {
        this.evidenceRefsJson = evidenceRefsJson;
    }

    /**
     * 返回规则版本。
     *
     * @return 规则版本
     */
    public String getRuleVersion() {
        return ruleVersion;
    }

    /**
     * 设置规则版本。
     *
     * @param ruleVersion 规则版本
     */
    public void setRuleVersion(String ruleVersion) {
        this.ruleVersion = ruleVersion;
    }

    /**
     * 返回模型提供方。
     *
     * @return 模型提供方
     */
    public String getModelProvider() {
        return modelProvider;
    }

    /**
     * 设置模型提供方。
     *
     * @param modelProvider 模型提供方
     */
    public void setModelProvider(String modelProvider) {
        this.modelProvider = modelProvider;
    }

    /**
     * 返回模型版本。
     *
     * @return 模型版本
     */
    public String getModelVersion() {
        return modelVersion;
    }

    /**
     * 设置模型版本。
     *
     * @param modelVersion 模型版本
     */
    public void setModelVersion(String modelVersion) {
        this.modelVersion = modelVersion;
    }

    /**
     * 返回候选动作 JSON。
     *
     * @return 候选动作 JSON
     */
    public String getCandidateActionsJson() {
        return candidateActionsJson;
    }

    /**
     * 设置候选动作 JSON。
     *
     * @param candidateActionsJson 候选动作 JSON
     */
    public void setCandidateActionsJson(String candidateActionsJson) {
        this.candidateActionsJson = candidateActionsJson;
    }

    /**
     * 返回风险等级。
     *
     * @return 风险等级
     */
    public String getRiskLevel() {
        return riskLevel;
    }

    /**
     * 设置风险等级。
     *
     * @param riskLevel 风险等级
     */
    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }

    /**
     * 返回兼容审批标记，市场调度闭环固定为 false。
     *
     * @return 兼容审批标记
     */
    public boolean isRequiresHumanApproval() {
        return requiresHumanApproval;
    }

    /**
     * 设置兼容审批标记。
     *
     * @param requiresHumanApproval 兼容审批标记
     */
    public void setRequiresHumanApproval(boolean requiresHumanApproval) {
        this.requiresHumanApproval = requiresHumanApproval;
    }

    /**
     * 返回解释文本。
     *
     * @return 解释文本
     */
    public String getExplanation() {
        return explanation;
    }

    /**
     * 设置解释文本。
     *
     * @param explanation 解释文本
     */
    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }

    /**
     * 返回是否使用回退。
     *
     * @return 是否使用回退
     */
    public boolean isFallbackUsed() {
        return fallbackUsed;
    }

    /**
     * 设置是否使用回退。
     *
     * @param fallbackUsed 是否使用回退
     */
    public void setFallbackUsed(boolean fallbackUsed) {
        this.fallbackUsed = fallbackUsed;
    }

    /**
     * 返回阶段执行状态。
     *
     * @return 阶段执行状态
     */
    public String getStageStatus() {
        return stageStatus;
    }

    /**
     * 设置阶段执行状态。
     *
     * @param stageStatus 阶段执行状态
     */
    public void setStageStatus(String stageStatus) {
        this.stageStatus = stageStatus;
    }

    /**
     * 返回重试次数。
     *
     * @return 重试次数
     */
    public int getRetryCount() {
        return retryCount;
    }

    /**
     * 设置重试次数。
     *
     * @param retryCount 重试次数
     */
    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    /**
     * 返回错误分类。
     *
     * @return 错误分类
     */
    public String getErrorCategory() {
        return errorCategory;
    }

    /**
     * 设置错误分类。
     *
     * @param errorCategory 错误分类
     */
    public void setErrorCategory(String errorCategory) {
        this.errorCategory = errorCategory;
    }

    /**
     * 返回上游模型服务 HTTP 状态码。
     *
     * @return 上游 HTTP 状态码；本地传输异常时为 null
     */
    public Integer getProviderStatusCode() {
        return providerStatusCode;
    }

    /**
     * 设置上游模型服务 HTTP 状态码。
     *
     * @param providerStatusCode 上游 HTTP 状态码
     */
    public void setProviderStatusCode(Integer providerStatusCode) {
        this.providerStatusCode = providerStatusCode;
    }

    /**
     * 返回上游模型服务错误码或本地传输异常类型。
     *
     * @return 错误码或异常类型
     */
    public String getProviderErrorCode() {
        return providerErrorCode;
    }

    /**
     * 设置上游模型服务错误码或本地传输异常类型。
     *
     * @param providerErrorCode 错误码或异常类型
     */
    public void setProviderErrorCode(String providerErrorCode) {
        this.providerErrorCode = providerErrorCode;
    }

    /**
     * 返回脱敏后的上游错误摘要。
     *
     * @return 错误摘要
     */
    public String getProviderErrorMessage() {
        return providerErrorMessage;
    }

    /**
     * 设置脱敏后的上游错误摘要。
     *
     * @param providerErrorMessage 错误摘要
     */
    public void setProviderErrorMessage(String providerErrorMessage) {
        this.providerErrorMessage = providerErrorMessage;
    }

    /**
     * 返回输入版本摘要。
     *
     * @return 输入版本摘要
     */
    public String getInputVersion() {
        return inputVersion;
    }

    /**
     * 设置输入版本摘要。
     *
     * @param inputVersion 输入版本摘要
     */
    public void setInputVersion(String inputVersion) {
        this.inputVersion = inputVersion;
    }

    /**
     * 返回输出版本。
     *
     * @return 输出版本
     */
    public String getOutputVersion() {
        return outputVersion;
    }

    /**
     * 设置输出版本。
     *
     * @param outputVersion 输出版本
     */
    public void setOutputVersion(String outputVersion) {
        this.outputVersion = outputVersion;
    }

    /**
     * 返回是否允许进入下一阶段。
     *
     * @return 是否允许进入下一阶段
     */
    public boolean isNextStageAllowed() {
        return nextStageAllowed;
    }

    /**
     * 设置是否允许进入下一阶段。
     *
     * @param nextStageAllowed 是否允许进入下一阶段
     */
    public void setNextStageAllowed(boolean nextStageAllowed) {
        this.nextStageAllowed = nextStageAllowed;
    }

    /**
     * 返回创建时间。
     *
     * @return 创建时间
     */
    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    /**
     * 设置创建时间。
     *
     * @param createdAt 创建时间
     */
    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}

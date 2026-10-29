package com.weaksignal.vpp.backend.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/**
 * 市场调度多智能体运行实体。
 */
@Entity
@Table(name = "market_agent_run")
public class MarketAgentRunEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_id", nullable = false, unique = true, length = 64)
    private String runId;

    @Column(name = "snapshot_id", nullable = false, length = 64)
    private String snapshotId;

    @Column(name = "dispatch_plan_id", nullable = false, length = 64)
    private String dispatchPlanId;

    @Column(name = "conservative_plan", nullable = false)
    private boolean conservativePlan;

    @Column(name = "workflow_version", nullable = false, length = 64)
    private String workflowVersion;

    @Column(name = "agent_set_version", nullable = false, length = 64)
    private String agentSetVersion;

    @Column(name = "stage_records_complete", nullable = false)
    private boolean stageRecordsComplete;

    @Column(name = "legacy_run", nullable = false)
    private boolean legacyRun;

    @Column(name = "safety_decision_json", nullable = false, columnDefinition = "TEXT")
    private String safetyDecisionJson;

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
     * 返回关联调度计划标识。
     *
     * @return 调度计划标识
     */
    public String getDispatchPlanId() {
        return dispatchPlanId;
    }

    /**
     * 设置关联调度计划标识。
     *
     * @param dispatchPlanId 调度计划标识
     */
    public void setDispatchPlanId(String dispatchPlanId) {
        this.dispatchPlanId = dispatchPlanId;
    }

    /**
     * 返回是否保守计划。
     *
     * @return 是否保守计划
     */
    public boolean isConservativePlan() {
        return conservativePlan;
    }

    /**
     * 设置是否保守计划。
     *
     * @param conservativePlan 是否保守计划
     */
    public void setConservativePlan(boolean conservativePlan) {
        this.conservativePlan = conservativePlan;
    }

    /**
     * 返回工作流版本。
     *
     * @return 工作流版本
     */
    public String getWorkflowVersion() {
        return workflowVersion;
    }

    /**
     * 设置工作流版本。
     *
     * @param workflowVersion 工作流版本
     */
    public void setWorkflowVersion(String workflowVersion) {
        this.workflowVersion = workflowVersion;
    }

    /**
     * 返回智能体集合版本。
     *
     * @return 智能体集合版本
     */
    public String getAgentSetVersion() {
        return agentSetVersion;
    }

    /**
     * 设置智能体集合版本。
     *
     * @param agentSetVersion 智能体集合版本
     */
    public void setAgentSetVersion(String agentSetVersion) {
        this.agentSetVersion = agentSetVersion;
    }

    /**
     * 返回阶段记录是否完整。
     *
     * @return 阶段记录是否完整
     */
    public boolean isStageRecordsComplete() {
        return stageRecordsComplete;
    }

    /**
     * 设置阶段记录是否完整。
     *
     * @param stageRecordsComplete 阶段记录是否完整
     */
    public void setStageRecordsComplete(boolean stageRecordsComplete) {
        this.stageRecordsComplete = stageRecordsComplete;
    }

    /**
     * 返回是否为历史兼容运行。
     *
     * @return 是否为历史兼容运行
     */
    public boolean isLegacyRun() {
        return legacyRun;
    }

    /**
     * 设置是否为历史兼容运行。
     *
     * @param legacyRun 是否为历史兼容运行
     */
    public void setLegacyRun(boolean legacyRun) {
        this.legacyRun = legacyRun;
    }

    /**
     * 返回安全门裁决 JSON。
     *
     * @return 安全门裁决 JSON
     */
    public String getSafetyDecisionJson() {
        return safetyDecisionJson;
    }

    /**
     * 设置安全门裁决 JSON。
     *
     * @param safetyDecisionJson 安全门裁决 JSON
     */
    public void setSafetyDecisionJson(String safetyDecisionJson) {
        this.safetyDecisionJson = safetyDecisionJson;
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

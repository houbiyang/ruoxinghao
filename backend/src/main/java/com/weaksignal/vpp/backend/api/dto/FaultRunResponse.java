package com.weaksignal.vpp.backend.api.dto;

import com.weaksignal.vpp.backend.fault.AgentStructuredResponse;
import com.weaksignal.vpp.backend.fault.ApprovalStatus;
import com.weaksignal.vpp.backend.fault.FaultRuleDecision;
import com.weaksignal.vpp.backend.fault.FaultRunStatus;
import com.weaksignal.vpp.backend.fault.FaultSafetyDecision;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 故障诊断运行完整响应。
 *
 * @param runId 运行标识
 * @param eventId 事件标识
 * @param stationId 场站标识
 * @param status 运行状态
 * @param approvalStatus 兼容复核状态
 * @param ruleDecision 规则判定
 * @param agentResults 五类智能体意见
 * @param safetyDecision 最终安全裁决
 * @param actions 动作计划
 * @param conflictDetected 是否存在冲突
 * @param fallbackUsed 是否使用规则回退
 * @param createdAt 创建时间
 * @param updatedAt 更新时间
 */
public record FaultRunResponse(
        String runId,
        String eventId,
        String stationId,
        FaultRunStatus status,
        ApprovalStatus approvalStatus,
        FaultRuleDecision ruleDecision,
        List<AgentStructuredResponse> agentResults,
        FaultSafetyDecision safetyDecision,
        List<FaultActionResponse> actions,
        boolean conflictDetected,
        boolean fallbackUsed,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}

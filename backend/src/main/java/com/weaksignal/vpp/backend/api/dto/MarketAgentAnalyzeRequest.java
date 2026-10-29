package com.weaksignal.vpp.backend.api.dto;

/**
 * 市场调度多智能体分析请求。
 *
 * @param dispatchContextSnapshotId 指定输入快照，可为空
 * @param dispatchPlanId 指定计划标识，可为空
 * @param conservativePlan 是否使用保守计划
 */
public record MarketAgentAnalyzeRequest(
        String dispatchContextSnapshotId,
        String dispatchPlanId,
        boolean conservativePlan
) {
}

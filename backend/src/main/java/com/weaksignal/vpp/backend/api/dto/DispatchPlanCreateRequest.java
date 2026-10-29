package com.weaksignal.vpp.backend.api.dto;

/**
 * 调度计划创建请求。
 *
 * @param dispatchContextSnapshotId 调度上下文快照标识
 * @param conservativePlan 是否生成保守计划
 * @param experimentScenarioCode 实时注入实验场景编码，普通调度为空
 */
public record DispatchPlanCreateRequest(
        String dispatchContextSnapshotId,
        boolean conservativePlan,
        String experimentScenarioCode
) {

    /**
     * 创建普通调度计划请求。
     *
     * @param dispatchContextSnapshotId 调度上下文快照标识
     * @param conservativePlan 是否生成保守计划
     */
    public DispatchPlanCreateRequest(String dispatchContextSnapshotId, boolean conservativePlan) {
        this(dispatchContextSnapshotId, conservativePlan, null);
    }
}

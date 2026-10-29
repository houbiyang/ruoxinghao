package com.weaksignal.vpp.backend.api.dto;

import com.weaksignal.vpp.backend.domain.DispatchPlanStatus;
import java.util.List;

/**
 * 调度计划审计链响应。
 *
 * @param planId 计划标识
 * @param version 计划版本
 * @param status 计划状态
 * @param inputSnapshotId 输入快照标识
 * @param commandIds 关联指令标识
 * @param events 审计事件
 */
public record DispatchPlanAuditChainResponse(
        String planId,
        String version,
        DispatchPlanStatus status,
        String inputSnapshotId,
        List<String> commandIds,
        List<String> events
) {
}

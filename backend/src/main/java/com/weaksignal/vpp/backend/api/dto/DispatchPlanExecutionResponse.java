package com.weaksignal.vpp.backend.api.dto;

import com.weaksignal.vpp.backend.domain.DispatchPlanStatus;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 调度计划执行概览响应。
 *
 * @param planId 计划标识
 * @param version 计划版本
 * @param status 计划状态
 * @param activeBatchId 当前批次标识
 * @param commandCount 已生成指令数量
 * @param terminalCommandCount 已终态指令数量
 * @param latestCommandTime 最近指令时间
 * @param commands 指令列表
 */
public record DispatchPlanExecutionResponse(
        String planId,
        String version,
        DispatchPlanStatus status,
        String activeBatchId,
        int commandCount,
        int terminalCommandCount,
        OffsetDateTime latestCommandTime,
        List<DispatchCommandResponse> commands
) {
}

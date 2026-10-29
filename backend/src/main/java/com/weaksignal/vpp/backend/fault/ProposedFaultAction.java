package com.weaksignal.vpp.backend.fault;

import java.util.Map;

/**
 * 候选故障处置动作。
 *
 * @param actionType 白名单动作类型
 * @param stationId 目标场站
 * @param parameters 受限动作参数
 * @param reason 动作原因
 */
public record ProposedFaultAction(
        FaultActionType actionType,
        String stationId,
        Map<String, String> parameters,
        String reason
) {
}

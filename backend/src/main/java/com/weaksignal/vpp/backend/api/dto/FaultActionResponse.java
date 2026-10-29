package com.weaksignal.vpp.backend.api.dto;

import com.weaksignal.vpp.backend.fault.FaultActionType;
import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 故障处置动作响应。
 *
 * @param actionId 动作标识
 * @param stationId 场站标识
 * @param actionType 动作类型
 * @param parameters 受限参数
 * @param riskLevel 风险等级
 * @param status 执行状态
 * @param requiresApproval 是否要求审批
 * @param mqttCommandId MQTT 命令标识
 * @param issuedAt 下发时间
 * @param expireAt 过期时间
 */
public record FaultActionResponse(
        String actionId,
        String stationId,
        FaultActionType actionType,
        Map<String, String> parameters,
        String riskLevel,
        String status,
        boolean requiresApproval,
        String mqttCommandId,
        OffsetDateTime issuedAt,
        OffsetDateTime expireAt
) {
}

package com.weaksignal.vpp.backend.mqtt.dto;

import com.weaksignal.vpp.backend.fault.FaultActionType;
import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 经过安全门构造的故障处置命令。
 *
 * @param actionId 动作标识
 * @param diagnosisRunId 诊断运行标识
 * @param stationId 场站标识
 * @param actionType 白名单动作类型
 * @param parameters 受限参数
 * @param issuedAt 下发时间
 * @param expireAt 过期时间
 */
public record FaultActionCommandMessage(
        String actionId,
        String diagnosisRunId,
        String stationId,
        FaultActionType actionType,
        Map<String, String> parameters,
        OffsetDateTime issuedAt,
        OffsetDateTime expireAt
) {
}

package com.weaksignal.vpp.simulator;

import java.time.OffsetDateTime;

/**
 * 模拟器故障处置命令。
 *
 * @param actionId 动作标识
 * @param diagnosisRunId 诊断运行标识
 * @param stationId 场站标识
 * @param actionType 动作类型
 * @param issuedAt 下发时间
 * @param expireAt 过期时间
 */
public record FaultActionCommand(
        String actionId,
        String diagnosisRunId,
        String stationId,
        FaultActionType actionType,
        OffsetDateTime issuedAt,
        OffsetDateTime expireAt
) {
}

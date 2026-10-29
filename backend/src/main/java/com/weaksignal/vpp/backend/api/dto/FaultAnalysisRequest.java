package com.weaksignal.vpp.backend.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * 多智能体故障分析请求。
 *
 * @param eventId 可选触发事件标识
 * @param stationId 场站标识
 * @param jitter 抖动，单位 ms
 * @param consecutivePacketLoss 连续丢包长度
 * @param heartbeatAge 心跳年龄，单位秒；负值表示使用实时状态推导
 * @param acknowledgementAge 确认年龄，单位秒
 * @param duplicateMessage 是否存在重复消息
 * @param staleSequence 是否存在序列号回退
 * @param timeSkewSeconds 时间偏差，单位秒
 * @param frozenTelemetry 是否遥测冻结
 * @param suspectedAttack 是否疑似攻击
 */
public record FaultAnalysisRequest(
        String eventId,
        @NotBlank(message = "场站标识不能为空") String stationId,
        @Min(value = 0, message = "抖动不能小于零") int jitter,
        @Min(value = 0, message = "连续丢包长度不能小于零") int consecutivePacketLoss,
        long heartbeatAge,
        @Min(value = 0, message = "确认年龄不能小于零") long acknowledgementAge,
        boolean duplicateMessage,
        boolean staleSequence,
        long timeSkewSeconds,
        boolean frozenTelemetry,
        boolean suspectedAttack
) {
}

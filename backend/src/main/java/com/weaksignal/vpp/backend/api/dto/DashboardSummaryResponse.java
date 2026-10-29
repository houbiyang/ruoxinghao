package com.weaksignal.vpp.backend.api.dto;

import com.weaksignal.vpp.backend.domain.ControlMode;
import com.weaksignal.vpp.backend.domain.DemoScenario;

/**
 * 总览摘要响应。
 *
 * @param totalOutputPower 总出力
 * @param adjustableCapacity 可调容量
 * @param onlineStationCount 在线场站数
 * @param alarmCount 告警数
 * @param controlMode 当前控制模式
 * @param activeScenario 当前场景
 */
public record DashboardSummaryResponse(
        double totalOutputPower,
        double adjustableCapacity,
        int onlineStationCount,
        int alarmCount,
        ControlMode controlMode,
        DemoScenario activeScenario
) {
}


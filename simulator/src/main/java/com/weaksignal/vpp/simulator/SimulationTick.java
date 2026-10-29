package com.weaksignal.vpp.simulator;

import java.util.List;

/**
 * 单次仿真输出。
 *
 * @param telemetryPayloads 遥测列表
 * @param heartbeatPayloads 心跳列表
 * @param scenarioReports 场景生命周期报告
 */
public record SimulationTick(
        List<TelemetryPayload> telemetryPayloads,
        List<HeartbeatPayload> heartbeatPayloads,
        List<ScenarioReportPayload> scenarioReports
) {
}

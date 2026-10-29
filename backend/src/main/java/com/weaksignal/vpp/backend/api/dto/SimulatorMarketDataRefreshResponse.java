package com.weaksignal.vpp.backend.api.dto;

/**
 * 由模拟器实时遥测计算得到的市场数据响应。
 *
 * @param marketSignal 市场信号快照
 * @param forecast 短期持平预测快照
 * @param telemetryReceivedAt 本次计算使用的最新遥测接收时间
 * @param formulaVersion 计算公式版本
 */
public record SimulatorMarketDataRefreshResponse(
        MarketSignalResponse marketSignal,
        ForecastSnapshotResponse forecast,
        String telemetryReceivedAt,
        String formulaVersion
) {
}

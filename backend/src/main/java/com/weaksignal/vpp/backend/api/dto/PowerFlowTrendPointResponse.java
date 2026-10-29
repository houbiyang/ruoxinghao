package com.weaksignal.vpp.backend.api.dto;

import java.time.OffsetDateTime;

/**
 * 模拟器功率流趋势点。
 *
 * @param timestamp 遥测聚合时间
 * @param generationPower 发电侧功率，单位 kW
 * @param consumptionPower 用电侧功率，单位 kW
 * @param sampledStationCount 参与聚合的场站数量
 */
public record PowerFlowTrendPointResponse(
        OffsetDateTime timestamp,
        double generationPower,
        double consumptionPower,
        int sampledStationCount
) {
}

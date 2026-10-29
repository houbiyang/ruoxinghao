package com.weaksignal.vpp.backend.api.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 市场计量记录响应。
 *
 * @param meteringId 计量标识
 * @param stationId 场站标识
 * @param intervalStart 时间片开始
 * @param intervalEnd 时间片结束
 * @param averagePower 平均功率
 * @param energyKwh 电量
 * @param sampleCount 样本数量
 */
public record MarketMeteringRecordResponse(
        String meteringId,
        String stationId,
        OffsetDateTime intervalStart,
        OffsetDateTime intervalEnd,
        double averagePower,
        BigDecimal energyKwh,
        int sampleCount
) {
}

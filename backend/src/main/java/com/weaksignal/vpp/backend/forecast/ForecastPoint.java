package com.weaksignal.vpp.backend.forecast;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 单个预测时间片。
 *
 * @param intervalStart 时间片开始
 * @param intervalEnd 时间片结束
 * @param loadP10 负荷 P10
 * @param loadP50 负荷 P50
 * @param loadP90 负荷 P90
 * @param photovoltaicP10 光伏 P10
 * @param photovoltaicP50 光伏 P50
 * @param photovoltaicP90 光伏 P90
 * @param windP10 风电 P10
 * @param windP50 风电 P50
 * @param windP90 风电 P90
 * @param priceP10 价格 P10
 * @param priceP50 价格 P50
 * @param priceP90 价格 P90
 */
public record ForecastPoint(
        OffsetDateTime intervalStart,
        OffsetDateTime intervalEnd,
        double loadP10,
        double loadP50,
        double loadP90,
        double photovoltaicP10,
        double photovoltaicP50,
        double photovoltaicP90,
        double windP10,
        double windP50,
        double windP90,
        BigDecimal priceP10,
        BigDecimal priceP50,
        BigDecimal priceP90
) {
}

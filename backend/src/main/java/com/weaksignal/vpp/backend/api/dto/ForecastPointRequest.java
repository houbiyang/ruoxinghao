package com.weaksignal.vpp.backend.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 预测时间片请求。
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
public record ForecastPointRequest(
        @NotNull(message = "时间片开始不能为空")
        OffsetDateTime intervalStart,
        @NotNull(message = "时间片结束不能为空")
        OffsetDateTime intervalEnd,
        @DecimalMin(value = "0.0", message = "负荷 P10 不能小于零")
        double loadP10,
        @DecimalMin(value = "0.0", message = "负荷 P50 不能小于零")
        double loadP50,
        @DecimalMin(value = "0.0", message = "负荷 P90 不能小于零")
        double loadP90,
        @DecimalMin(value = "0.0", message = "光伏 P10 不能小于零")
        double photovoltaicP10,
        @DecimalMin(value = "0.0", message = "光伏 P50 不能小于零")
        double photovoltaicP50,
        @DecimalMin(value = "0.0", message = "光伏 P90 不能小于零")
        double photovoltaicP90,
        @DecimalMin(value = "0.0", message = "风电 P10 不能小于零")
        double windP10,
        @DecimalMin(value = "0.0", message = "风电 P50 不能小于零")
        double windP50,
        @DecimalMin(value = "0.0", message = "风电 P90 不能小于零")
        double windP90,
        @NotNull(message = "价格 P10 不能为空")
        @DecimalMin(value = "0.0", message = "价格 P10 不能小于零")
        BigDecimal priceP10,
        @NotNull(message = "价格 P50 不能为空")
        @DecimalMin(value = "0.0", message = "价格 P50 不能小于零")
        BigDecimal priceP50,
        @NotNull(message = "价格 P90 不能为空")
        @DecimalMin(value = "0.0", message = "价格 P90 不能小于零")
        BigDecimal priceP90
) {
}

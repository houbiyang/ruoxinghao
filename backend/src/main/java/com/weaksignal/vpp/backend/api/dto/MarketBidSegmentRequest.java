package com.weaksignal.vpp.backend.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * 市场报价分段请求。
 *
 * @param quantity 分段申报功率
 * @param price 分段报价
 */
public record MarketBidSegmentRequest(
        @NotNull(message = "分段申报功率不能为空")
        @DecimalMin(value = "0.0", inclusive = false, message = "分段申报功率必须大于 0")
        Double quantity,
        @NotNull(message = "分段报价不能为空")
        @DecimalMin(value = "0.0", inclusive = false, message = "分段报价必须大于 0")
        BigDecimal price
) {
}

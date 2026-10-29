package com.weaksignal.vpp.backend.api.dto;

import java.math.BigDecimal;

/**
 * 市场报价分段响应。
 *
 * @param segmentOrder 分段序号
 * @param quantity 分段申报功率
 * @param price 分段报价
 */
public record MarketBidSegmentResponse(
        int segmentOrder,
        double quantity,
        BigDecimal price
) {
}

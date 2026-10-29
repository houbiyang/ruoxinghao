package com.weaksignal.vpp.backend.api.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 市场测试出清响应。
 *
 * @param clearingId 出清标识
 * @param bidId 报价标识
 * @param marketType 市场类型
 * @param intervalStart 时间片开始
 * @param intervalEnd 时间片结束
 * @param clearedPower 成交功率
 * @param clearedPrice 成交价格
 * @param ancillaryServicePower 辅助服务容量
 * @param externalTradeId 外部成交标识
 * @param testData 是否测试数据
 * @param createdAt 创建时间
 */
public record MarketClearingResponse(
        String clearingId,
        String bidId,
        String marketType,
        OffsetDateTime intervalStart,
        OffsetDateTime intervalEnd,
        double clearedPower,
        BigDecimal clearedPrice,
        double ancillaryServicePower,
        String externalTradeId,
        boolean testData,
        OffsetDateTime createdAt
) {
}

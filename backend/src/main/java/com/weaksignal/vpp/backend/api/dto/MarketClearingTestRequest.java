package com.weaksignal.vpp.backend.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;

/**
 * 市场测试出清请求。
 *
 * @param bidId 报价标识
 * @param ancillaryServicePower 辅助服务容量
 * @param externalTradeId 外部成交标识
 */
public record MarketClearingTestRequest(
        @NotBlank(message = "报价标识不能为空")
        String bidId,
        @DecimalMin(value = "0.0", inclusive = true, message = "辅助服务容量不能为负数")
        Double ancillaryServicePower,
        String externalTradeId
) {
}

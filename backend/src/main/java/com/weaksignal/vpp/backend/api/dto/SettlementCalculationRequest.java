package com.weaksignal.vpp.backend.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 市场结算计算请求。
 *
 * @param planId 计划标识
 * @param clearingId 出清标识
 */
public record SettlementCalculationRequest(
        @NotBlank(message = "计划标识不能为空")
        String planId,
        @NotBlank(message = "出清标识不能为空")
        String clearingId
) {
}

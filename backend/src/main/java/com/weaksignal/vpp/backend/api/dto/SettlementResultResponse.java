package com.weaksignal.vpp.backend.api.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * 市场结算结果响应。
 *
 * @param settlementId 结算标识
 * @param planId 计划标识
 * @param clearingId 出清标识
 * @param totalEnergyRevenue 电能量收入
 * @param gridImportCost 市电购入成本
 * @param gridExportRevenue 市电外送收入
 * @param deviationCost 偏差费用
 * @param storageCycleCost 储能循环成本
 * @param demandResponseCompensation 需求响应补偿
 * @param renewableCurtailmentCost 弃风弃光成本
 * @param ancillaryServiceRevenue 辅助服务收入
 * @param netRevenue 虚拟电厂净收益
 * @param resourceRevenueAllocation 资源收益分配
 * @param testData 是否测试数据
 * @param createdAt 创建时间
 * @param meteringRecords 计量记录
 */
public record SettlementResultResponse(
        String settlementId,
        String planId,
        String clearingId,
        BigDecimal totalEnergyRevenue,
        BigDecimal gridImportCost,
        BigDecimal gridExportRevenue,
        BigDecimal deviationCost,
        BigDecimal storageCycleCost,
        BigDecimal demandResponseCompensation,
        BigDecimal renewableCurtailmentCost,
        BigDecimal ancillaryServiceRevenue,
        BigDecimal netRevenue,
        Map<String, BigDecimal> resourceRevenueAllocation,
        boolean testData,
        OffsetDateTime createdAt,
        List<MarketMeteringRecordResponse> meteringRecords
) {
}

package com.weaksignal.vpp.backend.marketdispatch;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 调度计划时间片结果。
 *
 * @param itemId 时间片标识
 * @param inputSnapshotId 输入快照标识
 * @param intervalStart 时间片开始
 * @param intervalEnd 时间片结束
 * @param netPowerTarget 净功率目标
 * @param gridImportPower 市电购入功率
 * @param gridExportPower 市电外送功率
 * @param storageChargePower 储能充电功率
 * @param storageDischargePower 储能放电功率
 * @param flexibleLoadAdjustment 柔性负荷调节功率
 * @param upwardReserve 向上备用
 * @param downwardReserve 向下备用
 * @param renewableCurtailment 新能源限发
 * @param expectedCost 预计成本
 * @param expectedRevenue 预计收益
 * @param riskCost 风险成本
 * @param reasonCode 原因码
 * @param itemOrder 顺序号
 */
public record DispatchPlanItem(
        String itemId,
        String inputSnapshotId,
        OffsetDateTime intervalStart,
        OffsetDateTime intervalEnd,
        double netPowerTarget,
        double gridImportPower,
        double gridExportPower,
        double storageChargePower,
        double storageDischargePower,
        double flexibleLoadAdjustment,
        double upwardReserve,
        double downwardReserve,
        double renewableCurtailment,
        BigDecimal expectedCost,
        BigDecimal expectedRevenue,
        BigDecimal riskCost,
        String reasonCode,
        int itemOrder
) {
}

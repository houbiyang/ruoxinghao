package com.weaksignal.vpp.backend.marketdispatch;

import com.weaksignal.vpp.backend.domain.DispatchPlanStatus;
import com.weaksignal.vpp.backend.domain.QualityCode;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 调度计划聚合结果。
 *
 * @param planId 计划标识
 * @param inputSnapshotId 输入快照标识
 * @param intervalMinutes 时间粒度
 * @param conservativePlan 是否保守计划
 * @param status 计划状态
 * @param generatedAt 生成时间
 * @param validFrom 生效时间
 * @param validUntil 失效时间
 * @param source 数据来源
 * @param qualityCode 数据质量
 * @param version 版本号
 * @param receivedAt 接收时间
 * @param items 计划时间片
 */
public record DispatchPlan(
        String planId,
        String inputSnapshotId,
        int intervalMinutes,
        boolean conservativePlan,
        DispatchPlanStatus status,
        OffsetDateTime generatedAt,
        OffsetDateTime validFrom,
        OffsetDateTime validUntil,
        String source,
        QualityCode qualityCode,
        String version,
        OffsetDateTime receivedAt,
        List<DispatchPlanItem> items
) {
}

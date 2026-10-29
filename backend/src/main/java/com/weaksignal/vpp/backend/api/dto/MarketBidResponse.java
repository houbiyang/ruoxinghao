package com.weaksignal.vpp.backend.api.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 市场报价响应。
 *
 * @param bidId 报价标识
 * @param marketType 市场类型
 * @param aggregateObject 聚合对象
 * @param intervalStart 时间片开始
 * @param intervalEnd 时间片结束
 * @param availableCapacity 可申报容量
 * @param totalQuantity 报价总功率
 * @param inputSnapshotId 输入快照标识
 * @param bidVersion 报价版本
 * @param status 报价状态
 * @param validUntil 有效截止时间
 * @param testData 是否测试数据
 * @param createdAt 创建时间
 * @param segments 分段报价
 */
public record MarketBidResponse(
        String bidId,
        String marketType,
        String aggregateObject,
        OffsetDateTime intervalStart,
        OffsetDateTime intervalEnd,
        double availableCapacity,
        double totalQuantity,
        String inputSnapshotId,
        String bidVersion,
        String status,
        OffsetDateTime validUntil,
        boolean testData,
        OffsetDateTime createdAt,
        List<MarketBidSegmentResponse> segments
) {
}

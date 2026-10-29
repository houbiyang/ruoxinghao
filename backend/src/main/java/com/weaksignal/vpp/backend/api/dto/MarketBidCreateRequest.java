package com.weaksignal.vpp.backend.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 市场报价创建请求。
 *
 * @param marketType 市场类型
 * @param aggregateObject 聚合对象
 * @param intervalStart 时间片开始
 * @param intervalEnd 时间片结束
 * @param availableCapacity 可申报容量
 * @param inputSnapshotId 输入快照标识
 * @param bidVersion 报价版本
 * @param validUntil 有效截止时间
 * @param testData 是否测试数据
 * @param segments 分段报价
 */
public record MarketBidCreateRequest(
        @NotBlank(message = "市场类型不能为空")
        String marketType,
        @NotBlank(message = "聚合对象不能为空")
        String aggregateObject,
        @NotNull(message = "时间片开始不能为空")
        OffsetDateTime intervalStart,
        @NotNull(message = "时间片结束不能为空")
        OffsetDateTime intervalEnd,
        @NotNull(message = "可申报容量不能为空")
        @DecimalMin(value = "0.0", inclusive = false, message = "可申报容量必须大于 0")
        Double availableCapacity,
        @NotBlank(message = "输入快照标识不能为空")
        String inputSnapshotId,
        @NotBlank(message = "报价版本不能为空")
        String bidVersion,
        @NotNull(message = "有效截止时间不能为空")
        OffsetDateTime validUntil,
        Boolean testData,
        @NotEmpty(message = "分段报价不能为空")
        List<@Valid MarketBidSegmentRequest> segments
) {
}

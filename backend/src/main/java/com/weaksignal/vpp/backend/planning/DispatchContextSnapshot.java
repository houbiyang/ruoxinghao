package com.weaksignal.vpp.backend.planning;

import com.weaksignal.vpp.backend.capability.ResourceCapabilitySnapshot;
import com.weaksignal.vpp.backend.domain.QualityCode;
import com.weaksignal.vpp.backend.forecast.ForecastSnapshot;
import com.weaksignal.vpp.backend.market.MarketSignalSnapshot;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 调度上下文不可变快照。
 *
 * @param snapshotId 快照标识
 * @param generatedAt 生成时间
 * @param validFrom 生效时间
 * @param validUntil 失效时间
 * @param source 数据来源
 * @param qualityCode 数据质量
 * @param version 版本号
 * @param receivedAt 接收时间
 * @param intervalMinutes 时间粒度，单位分钟
 * @param marketSignalSnapshot 市场信号快照
 * @param forecastSnapshot 预测快照
 * @param resourceCapabilitySnapshots 资源能力快照集合
 */
public record DispatchContextSnapshot(
        String snapshotId,
        OffsetDateTime generatedAt,
        OffsetDateTime validFrom,
        OffsetDateTime validUntil,
        String source,
        QualityCode qualityCode,
        String version,
        OffsetDateTime receivedAt,
        int intervalMinutes,
        MarketSignalSnapshot marketSignalSnapshot,
        ForecastSnapshot forecastSnapshot,
        List<ResourceCapabilitySnapshot> resourceCapabilitySnapshots
) {
}

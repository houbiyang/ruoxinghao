package com.weaksignal.vpp.backend.market;

import com.weaksignal.vpp.backend.domain.QualityCode;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 市场信号不可变快照。
 *
 * @param snapshotId 快照标识
 * @param marketType 市场类型
 * @param intervalStart 时间片起始时间
 * @param intervalEnd 时间片结束时间
 * @param contractPower 合同功率，单位 kW
 * @param contractPrice 合同价格
 * @param dayAheadPrice 日前价格
 * @param realTimePrice 实时价格
 * @param gridImportLimit 市电购入限制，单位 kW
 * @param gridExportLimit 市电外送限制，单位 kW
 * @param reserveUpRequirement 向上备用要求，单位 kW
 * @param reserveDownRequirement 向下备用要求，单位 kW
 * @param generatedAt 生成时间
 * @param validFrom 生效时间
 * @param validUntil 失效时间
 * @param source 数据来源
 * @param qualityCode 数据质量
 * @param version 版本号
 * @param receivedAt 接收时间
 */
public record MarketSignalSnapshot(
        String snapshotId,
        String marketType,
        OffsetDateTime intervalStart,
        OffsetDateTime intervalEnd,
        double contractPower,
        BigDecimal contractPrice,
        BigDecimal dayAheadPrice,
        BigDecimal realTimePrice,
        double gridImportLimit,
        double gridExportLimit,
        double reserveUpRequirement,
        double reserveDownRequirement,
        OffsetDateTime generatedAt,
        OffsetDateTime validFrom,
        OffsetDateTime validUntil,
        String source,
        QualityCode qualityCode,
        String version,
        OffsetDateTime receivedAt
) {
}

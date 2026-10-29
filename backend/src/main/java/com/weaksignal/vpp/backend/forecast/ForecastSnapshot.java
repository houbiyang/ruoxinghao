package com.weaksignal.vpp.backend.forecast;

import com.weaksignal.vpp.backend.domain.QualityCode;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 预测不可变快照。
 *
 * @param snapshotId 快照标识
 * @param intervalMinutes 时间粒度，单位分钟
 * @param modelVersion 预测模型版本
 * @param points 预测点集合
 * @param generatedAt 生成时间
 * @param validFrom 生效时间
 * @param validUntil 失效时间
 * @param source 数据来源
 * @param qualityCode 数据质量
 * @param version 版本号
 * @param receivedAt 接收时间
 */
public record ForecastSnapshot(
        String snapshotId,
        int intervalMinutes,
        String modelVersion,
        List<ForecastPoint> points,
        OffsetDateTime generatedAt,
        OffsetDateTime validFrom,
        OffsetDateTime validUntil,
        String source,
        QualityCode qualityCode,
        String version,
        OffsetDateTime receivedAt
) {
}

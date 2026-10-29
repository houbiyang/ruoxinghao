package com.weaksignal.vpp.backend.api.dto;

import com.weaksignal.vpp.backend.domain.QualityCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 预测快照创建请求。
 *
 * @param intervalMinutes 时间粒度，单位分钟
 * @param modelVersion 模型版本
 * @param points 预测点集合
 * @param generatedAt 生成时间
 * @param validFrom 生效时间
 * @param validUntil 失效时间
 * @param source 数据来源
 * @param qualityCode 数据质量
 * @param version 版本号
 * @param receivedAt 接收时间
 */
public record ForecastSnapshotCreateRequest(
        @Min(value = 1, message = "时间粒度必须大于零")
        int intervalMinutes,
        @NotBlank(message = "模型版本不能为空")
        String modelVersion,
        @NotEmpty(message = "预测点不能为空")
        List<@Valid ForecastPointRequest> points,
        @NotNull(message = "生成时间不能为空")
        OffsetDateTime generatedAt,
        @NotNull(message = "生效时间不能为空")
        OffsetDateTime validFrom,
        @NotNull(message = "失效时间不能为空")
        OffsetDateTime validUntil,
        @NotBlank(message = "数据来源不能为空")
        String source,
        @NotNull(message = "数据质量不能为空")
        QualityCode qualityCode,
        @NotBlank(message = "版本号不能为空")
        String version,
        @NotNull(message = "接收时间不能为空")
        OffsetDateTime receivedAt
) {
}

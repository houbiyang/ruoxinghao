package com.weaksignal.vpp.backend.api.dto;

import com.weaksignal.vpp.backend.domain.QualityCode;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 市场信号创建请求。
 *
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
public record MarketSignalCreateRequest(
        @NotBlank(message = "市场类型不能为空")
        String marketType,
        @NotNull(message = "时间片起始时间不能为空")
        OffsetDateTime intervalStart,
        @NotNull(message = "时间片结束时间不能为空")
        OffsetDateTime intervalEnd,
        @DecimalMin(value = "0.0", message = "合同功率不能小于零")
        double contractPower,
        @NotNull(message = "合同价格不能为空")
        @DecimalMin(value = "0.0", message = "合同价格不能小于零")
        BigDecimal contractPrice,
        @NotNull(message = "日前价格不能为空")
        @DecimalMin(value = "0.0", message = "日前价格不能小于零")
        BigDecimal dayAheadPrice,
        @NotNull(message = "实时价格不能为空")
        @DecimalMin(value = "0.0", message = "实时价格不能小于零")
        BigDecimal realTimePrice,
        @DecimalMin(value = "0.0", message = "市电购入限制不能小于零")
        double gridImportLimit,
        @DecimalMin(value = "0.0", message = "市电外送限制不能小于零")
        double gridExportLimit,
        @DecimalMin(value = "0.0", message = "向上备用要求不能小于零")
        double reserveUpRequirement,
        @DecimalMin(value = "0.0", message = "向下备用要求不能小于零")
        double reserveDownRequirement,
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

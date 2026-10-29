package com.weaksignal.vpp.backend.api.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 实验前后对比响应。
 *
 * @param run 实验运行
 * @param targetStationIds 目标场站
 * @param before 实验前指标
 * @param during 实验中指标
 * @param derivedMetrics 基于前后窗口计算出的衍生对比指标
 */
public record ExperimentComparisonResponse(
        ExperimentRunResponse run,
        List<String> targetStationIds,
        ExperimentMetricSnapshotResponse before,
        ExperimentMetricSnapshotResponse during,
        List<DerivedMetricResponse> derivedMetrics
) {

    /**
     * 实验衍生对比指标。
     *
     * @param metricCode 指标编码
     * @param metricName 指标名称
     * @param beforeValue 实验前数值
     * @param duringValue 实验中数值
     * @param unit 单位
     * @param improvementValue 改善值
     * @param improvementRate 改善率
     * @param higherIsBetter 是否越高越好
     * @param available 是否具备完整对比数据
     */
    public record DerivedMetricResponse(
            String metricCode,
            String metricName,
            BigDecimal beforeValue,
            BigDecimal duringValue,
            String unit,
            BigDecimal improvementValue,
            BigDecimal improvementRate,
            boolean higherIsBetter,
            boolean available
    ) {
    }
}

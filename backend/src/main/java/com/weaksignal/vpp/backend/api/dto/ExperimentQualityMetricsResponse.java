package com.weaksignal.vpp.backend.api.dto;

import java.util.List;

/**
 * 实验通信质量指标响应。
 *
 * @param before 实验前质量指标
 * @param during 实验中质量指标
 * @param trends 通信质量趋势
 * @param stateDistribution 链路状态分布
 * @param stationRanking 场站通信质量排名
 */
public record ExperimentQualityMetricsResponse(
        ExperimentMetricSnapshotResponse before,
        ExperimentMetricSnapshotResponse during,
        List<ExperimentQualityTrendPointResponse> trends,
        List<ExperimentStateDistributionResponse> stateDistribution,
        List<ExperimentStationQualityRankResponse> stationRanking
) {
}

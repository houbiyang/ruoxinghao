package com.weaksignal.vpp.backend.api.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 市场与电价页面聚合响应。
 *
 * @param summary 顶部摘要
 * @param summarySparklines 指标趋势线
 * @param priceTrend 电价曲线
 * @param distribution 价格分布
 * @param timeOfUsePrices 分时电价表
 * @param predictionAnalysis 电价预测分析
 * @param recommendations 智能调度建议
 * @param news 市场新闻
 * @param dataSourceStatus 数据源状态
 */
public record MarketDashboardOverviewResponse(
        Summary summary,
        SummarySparklines summarySparklines,
        PriceTrend priceTrend,
        List<DistributionItem> distribution,
        List<TimeOfUsePriceItem> timeOfUsePrices,
        PredictionAnalysis predictionAnalysis,
        List<RecommendationItem> recommendations,
        List<NewsItem> news,
        List<DataSourceStatusItem> dataSourceStatus
) {

    /**
     * 顶部摘要信息。
     *
     * @param currentRealTimePrice 当前实时电价
     * @param peakForecastPrice 预计高峰电价
     * @param peakForecastWindow 高峰时间窗
     * @param contractEnergyMwh 合同电量
     * @param executedEnergyMwh 已执行电量
     * @param contractProgressPercent 执行进度
     * @param averageForecastPrice 日内预测平均电价
     * @param todayTradeVolumeMwh 今日市场交易量
     * @param marketName 市场名称
     * @param settlementCycle 结算周期
     * @param currentTimeWindow 当前时段
     * @param currentPhase 当前阶段
     * @param clearingStatus 出清状态
     */
    public record Summary(
            BigDecimal currentRealTimePrice,
            BigDecimal peakForecastPrice,
            String peakForecastWindow,
            BigDecimal contractEnergyMwh,
            BigDecimal executedEnergyMwh,
            BigDecimal contractProgressPercent,
            BigDecimal averageForecastPrice,
            BigDecimal todayTradeVolumeMwh,
            String marketName,
            String settlementCycle,
            String currentTimeWindow,
            String currentPhase,
            String clearingStatus
    ) {
    }

    /**
     * 顶部指标趋势线集合。
     *
     * @param realTimePriceTrend 实时电价趋势线
     * @param peakForecastTrend 高峰预测趋势线
     * @param averageForecastTrend 平均预测趋势线
     * @param tradeVolumeTrend 交易量趋势线
     */
    public record SummarySparklines(
            List<BigDecimal> realTimePriceTrend,
            List<BigDecimal> peakForecastTrend,
            List<BigDecimal> averageForecastTrend,
            List<BigDecimal> tradeVolumeTrend
    ) {
    }

    /**
     * 电价曲线响应。
     *
     * @param range 时间范围
     * @param points 曲线点集合
     */
    public record PriceTrend(
            String range,
            List<PriceTrendPoint> points
    ) {
    }

    /**
     * 电价曲线点。
     *
     * @param time 时间标签
     * @param realTimePrice 实时电价
     * @param dayAheadPrice 日前电价
     * @param forecastTodayPrice 预测电价今日线
     * @param forecastTomorrowPrice 预测电价明日线
     */
    public record PriceTrendPoint(
            String time,
            BigDecimal realTimePrice,
            BigDecimal dayAheadPrice,
            BigDecimal forecastTodayPrice,
            BigDecimal forecastTomorrowPrice
    ) {
    }

    /**
     * 实时价格分布项。
     *
     * @param bucket 价格区间
     * @param percentage 占比百分比
     */
    public record DistributionItem(
            String bucket,
            BigDecimal percentage
    ) {
    }

    /**
     * 分时电价行。
     *
     * @param period 时段
     * @param realTimePrice 实时电价
     * @param dayAheadPrice 日前电价
     * @param forecastTodayPrice 预测电价今日线
     * @param forecastTomorrowPrice 预测电价明日线
     */
    public record TimeOfUsePriceItem(
            String period,
            BigDecimal realTimePrice,
            BigDecimal dayAheadPrice,
            BigDecimal forecastTodayPrice,
            BigDecimal forecastTomorrowPrice
    ) {
    }

    /**
     * 电价预测分析。
     *
     * @param todayTrend 今日预测趋势
     * @param tomorrowTrend 明日预测趋势
     * @param conclusion 规则化预测结论
     */
    public record PredictionAnalysis(
            List<PredictionPoint> todayTrend,
            List<PredictionPoint> tomorrowTrend,
            String conclusion
    ) {
    }

    /**
     * 预测曲线点。
     *
     * @param time 时间标签
     * @param price 价格
     */
    public record PredictionPoint(
            String time,
            BigDecimal price
    ) {
    }

    /**
     * 智能调度建议项。
     *
     * @param type 建议类型
     * @param title 标题
     * @param description 描述
     * @param valueLabel 价值标签
     * @param value 金额
     * @param tone 颜色语义
     */
    public record RecommendationItem(
            String type,
            String title,
            String description,
            String valueLabel,
            BigDecimal value,
            String tone
    ) {
    }

    /**
     * 新闻项。
     *
     * @param publishedAt 发布时间
     * @param title 标题
     */
    public record NewsItem(
            OffsetDateTime publishedAt,
            String title
    ) {
    }

    /**
     * 数据源状态项。
     *
     * @param label 名称
     * @param online 是否在线
     */
    public record DataSourceStatusItem(
            String label,
            boolean online
    ) {
    }
}

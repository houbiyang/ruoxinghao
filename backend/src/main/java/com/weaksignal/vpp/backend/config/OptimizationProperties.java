package com.weaksignal.vpp.backend.config;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * OR-Tools MILP 求解器和目标函数权重配置。
 *
 * @param optimizerType 优化器类型，仅支持 OR_TOOLS_MILP
 * @param solverTimeLimitSeconds 单次求解最大耗时，单位秒
 * @param relativeMipGap 相对 MIP 最优间隙
 * @param enableRuleFallback 是否允许规则优化器降级
 * @param diagnosticRelaxationEnabled 是否启用仅诊断用松弛模型
 * @param gridImportCostWeight 市电购入成本权重，单位成本/千瓦时
 * @param thermalOperationCostWeight 火电运行成本权重，单位成本/千瓦时
 * @param thermalStartupCostWeight 火电启动成本权重，单位成本/次
 * @param storageCycleCostWeight 储能循环损耗权重，单位成本/千瓦时
 * @param curtailmentCostWeight 弃风弃光成本权重，单位成本/千瓦时
 * @param flexibleLoadCostWeight 柔性负荷调节成本权重，单位成本/千瓦时
 * @param targetChangeCostWeight 相邻 MPC 目标变化成本权重，单位成本/千瓦
 * @param trackingErrorCostWeight 目标跟踪误差成本权重，单位成本/千瓦
 * @param thermalMinimumUpMinutes 火电最小开机时间，单位分钟
 * @param thermalMinimumDownMinutes 火电最小停机时间，单位分钟
 */
@Validated
@ConfigurationProperties(prefix = "app.planning.optimizer")
public record OptimizationProperties(
        String optimizerType,
        @Min(1) int solverTimeLimitSeconds,
        @DecimalMin("0.0") double relativeMipGap,
        boolean enableRuleFallback,
        boolean diagnosticRelaxationEnabled,
        @DecimalMin("0.0") double gridImportCostWeight,
        @DecimalMin("0.0") double thermalOperationCostWeight,
        @DecimalMin("0.0") double thermalStartupCostWeight,
        @DecimalMin("0.0") double storageCycleCostWeight,
        @DecimalMin("0.0") double curtailmentCostWeight,
        @DecimalMin("0.0") double flexibleLoadCostWeight,
        @DecimalMin("0.0") double targetChangeCostWeight,
        @DecimalMin("0.0") double trackingErrorCostWeight,
        @Min(1) int thermalMinimumUpMinutes,
        @Min(1) int thermalMinimumDownMinutes
) { }

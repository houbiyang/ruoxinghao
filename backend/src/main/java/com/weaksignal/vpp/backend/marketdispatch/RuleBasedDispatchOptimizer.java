package com.weaksignal.vpp.backend.marketdispatch;

import com.weaksignal.vpp.backend.api.ApiException;
import com.weaksignal.vpp.backend.capability.ResourceCapabilitySnapshot;
import com.weaksignal.vpp.backend.capability.StorageConstraint;
import com.weaksignal.vpp.backend.domain.ResourceType;
import com.weaksignal.vpp.backend.forecast.ForecastPoint;
import com.weaksignal.vpp.backend.forecast.ForecastSnapshot;
import com.weaksignal.vpp.backend.market.MarketSignalSnapshot;
import com.weaksignal.vpp.backend.planning.DispatchContextSnapshot;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * 规则型确定性经济调度优化器。
 */
@Component
public class RuleBasedDispatchOptimizer implements DispatchOptimizer {

    private static final BigDecimal FLEXIBLE_LOAD_COMPENSATION = new BigDecimal("0.050000");
    private static final BigDecimal CURTAILMENT_COST = new BigDecimal("0.080000");
    private static final BigDecimal RISK_FACTOR = new BigDecimal("0.010000");
    private static final String WIND_CALM_SCENARIO = "ENV_WIND_CALM";
    private static final double THERMAL_SUPPORT_RATIO = 0.15;
    private static final double STORAGE_SUPPORT_RATIO = 0.10;
    private static final double SUPPORT_INCREASE_RATIO = 1.15;

    /**
     * 基于统一调度上下文生成确定性计划。
     *
     * @param optimizationContext 优化上下文
     * @return 优化结果
     */
    @Override
    public OptimizationResult optimize(OptimizationContext optimizationContext) {
        DispatchContextSnapshot snapshot = optimizationContext.dispatchContextSnapshot();
        ForecastSnapshot forecastSnapshot = snapshot.forecastSnapshot();
        MarketSignalSnapshot marketSignalSnapshot = snapshot.marketSignalSnapshot();
        CapabilityView capabilityView = buildCapabilityView(snapshot.resourceCapabilitySnapshots(), snapshot.intervalMinutes());

        double intervalHours = snapshot.intervalMinutes() / 60.0;
        double currentSoc = capabilityView.storageSoc;
        List<DispatchPlanItem> items = forecastSnapshot.points().stream()
                .map(point -> computePlanItem(
                        snapshot.snapshotId(),
                        marketSignalSnapshot,
                        capabilityView,
                        point,
                        intervalHours,
                        optimizationContext.conservativePlan(),
                        currentSoc,
                        forecastSnapshot.points().indexOf(point)
                ))
                .toList();

        Map<String, List<ResourceDispatchTarget>> targetsByItemId = new LinkedHashMap<>();
        Map<String, String> targetSummaryHashesByItemId = new LinkedHashMap<>();
        for (DispatchPlanItem item : items) {
            List<ResourceDispatchTarget> targets = buildTargets(snapshot, item, optimizationContext.experimentScenarioCode());
            targetsByItemId.put(item.itemId(), targets);
            targetSummaryHashesByItemId.put(item.itemId(), ResourceDispatchTarget.summaryHash(targets));
        }
        return new OptimizationResult(items, "RULE_BASED", "N/A", OptimizationStatus.RULE_FALLBACK, null, 0, null,
                0, snapshot.snapshotId(), null, Map.of(), null, targetsByItemId, targetSummaryHashesByItemId);
    }

    private List<ResourceDispatchTarget> buildTargets(
            DispatchContextSnapshot snapshot,
            DispatchPlanItem item,
            String experimentScenarioCode
    ) {
        List<ResourceDispatchTarget> targets = new java.util.ArrayList<>(snapshot.resourceCapabilitySnapshots().stream()
                .filter(capability -> capability.linkState() != com.weaksignal.vpp.backend.domain.LinkState.S3)
                .filter(ResourceCapabilitySnapshot::dataTrusted)
                .map(capability -> new ResourceDispatchTarget(
                        capability.stationId(),
                        capability.resourceType(),
                        resolveTargetPower(snapshot, item, capability, experimentScenarioCode),
                        directionOf(resolveTargetPower(snapshot, item, capability, experimentScenarioCode)),
                        OptimizationStatus.RULE_FALLBACK,
                        0,
                        snapshot.snapshotId(),
                        Map.of("optimizer", "RULE_BASED", "reasonCode", item.reasonCode()),
                        snapshot.generatedAt()
                ))
                .sorted(java.util.Comparator.comparing(ResourceDispatchTarget::stationId))
                .toList());
        reconcileRoundingResidual(snapshot, item, targets);
        return targets.stream().sorted(java.util.Comparator.comparing(ResourceDispatchTarget::stationId)).toList();
    }

    private double resolveTargetPower(
            DispatchContextSnapshot snapshot,
            DispatchPlanItem item,
            ResourceCapabilitySnapshot capability,
            String experimentScenarioCode
    ) {
        boolean windFaultCoordination = WIND_CALM_SCENARIO.equals(experimentScenarioCode);
        double requestedTargetPower = switch (capability.resourceType()) {
            case PHOTOVOLTAIC -> resolveRenewableTarget(snapshot, item, ResourceType.PHOTOVOLTAIC);
            case WIND -> resolveRenewableTarget(snapshot, item, ResourceType.WIND);
            case THERMAL -> windFaultCoordination
                    ? supportTarget(capability.currentPower(), capability.maximumPower(), THERMAL_SUPPORT_RATIO)
                    : capability.currentPower();
            case STORAGE -> windFaultCoordination
                    ? supportTarget(
                            capability.currentPower(),
                            capability.storageConstraint() == null
                                    ? capability.maximumPower()
                                    : capability.storageConstraint().maximumDischargePowerKW(),
                            STORAGE_SUPPORT_RATIO
                    )
                    : item.storageDischargePower() - item.storageChargePower();
            case LOAD, CHARGING -> resolveLoadTarget(snapshot, item, capability);
            case GRID -> item.gridImportPower() - item.gridExportPower();
        };
        double effectiveMaximum = capability.resourceType() == ResourceType.LOAD || capability.resourceType() == ResourceType.CHARGING
                ? Math.max(capability.maximumPower(), capability.currentPower()) : capability.maximumPower();
        double boundedTargetPower = Math.max(capability.minimumPower(), Math.min(requestedTargetPower, effectiveMaximum));
        return roundPower(ensureNonZeroTarget(capability, boundedTargetPower, effectiveMaximum));
    }

    /**
     * 为健康且具备调节能力的资源保留非零运行功率。
     *
     * @param capability 资源能力
     * @param targetPower 原始目标功率
     * @param effectiveMaximum 有效最大功率
     * @return 满足安全边界的非零目标；无可用能力时保持零
     */
    private double ensureNonZeroTarget(
            ResourceCapabilitySnapshot capability,
            double targetPower,
            double effectiveMaximum
    ) {
        if (Math.abs(targetPower) >= 0.1 || effectiveMaximum > 0 && capability.resourceType() != ResourceType.STORAGE) {
            if (Math.abs(targetPower) >= 0.1) {
                return targetPower;
            }
            return Math.min(effectiveMaximum, Math.max(0.1, effectiveMaximum * 0.05));
        }
        if (capability.resourceType() != ResourceType.STORAGE || capability.storageConstraint() == null) {
            return targetPower;
        }
        StorageConstraint storageConstraint = capability.storageConstraint();
        if (storageConstraint.maximumDischargePowerKW() > 0) {
            return Math.max(0.1, storageConstraint.maximumDischargePowerKW() * 0.05);
        }
        if (storageConstraint.maximumChargePowerKW() > 0) {
            return -Math.max(0.1, storageConstraint.maximumChargePowerKW() * 0.05);
        }
        return 0;
    }

    /**
     * 计算故障协同支撑目标，保证健康可控资源不会被置零并产生可观测的调节量。
     *
     * @param currentPower 当前功率
     * @param maximumPower 最大可用功率
     * @param minimumSupportRatio 最低支撑比例
     * @return 支撑目标功率
     */
    private double supportTarget(double currentPower, double maximumPower, double minimumSupportRatio) {
        if (maximumPower <= 0) {
            return 0;
        }
        double increasedCurrentPower = Math.max(0, currentPower) * SUPPORT_INCREASE_RATIO;
        double minimumSupportPower = maximumPower * minimumSupportRatio;
        return Math.min(maximumPower, Math.max(increasedCurrentPower, minimumSupportPower));
    }

    private double resolveLoadTarget(DispatchContextSnapshot snapshot, DispatchPlanItem item, ResourceCapabilitySnapshot capability) {
        double totalLoadTarget = snapshot.forecastSnapshot().points().stream()
                .filter(point -> point.intervalStart().equals(item.intervalStart()))
                .findFirst()
                .map(point -> Math.max(0, point.loadP50() - item.flexibleLoadAdjustment()))
                .orElse(0.0);
        List<ResourceCapabilitySnapshot> loadResources = snapshot.resourceCapabilitySnapshots().stream()
                .filter(resource -> resource.resourceType() == ResourceType.LOAD || resource.resourceType() == ResourceType.CHARGING)
                .filter(resource -> resource.linkState() != com.weaksignal.vpp.backend.domain.LinkState.S3)
                .filter(ResourceCapabilitySnapshot::dataTrusted)
                .toList();
        double currentTotal = loadResources.stream().mapToDouble(ResourceCapabilitySnapshot::currentPower).sum();
        double capacityTotal = loadResources.stream().mapToDouble(ResourceCapabilitySnapshot::maximumPower).sum();
        double share = currentTotal > 0 ? capability.currentPower() / currentTotal
                : capacityTotal > 0 ? capability.maximumPower() / capacityTotal : 0;
        return totalLoadTarget * share;
    }

    private double resolveRenewableTarget(DispatchContextSnapshot snapshot, DispatchPlanItem item, ResourceType resourceType) {
        double photovoltaicForecast = forecastPower(snapshot, ResourceType.PHOTOVOLTAIC, item);
        double windForecast = forecastPower(snapshot, ResourceType.WIND, item);
        double totalForecast = photovoltaicForecast + windForecast;
        if (totalForecast <= 0) return 0;
        double availableAfterCurtailment = Math.max(0, totalForecast - item.renewableCurtailment());
        double resourceForecast = resourceType == ResourceType.PHOTOVOLTAIC ? photovoltaicForecast : windForecast;
        return availableAfterCurtailment * resourceForecast / totalForecast;
    }

    private void reconcileRoundingResidual(DispatchContextSnapshot snapshot, DispatchPlanItem item, List<ResourceDispatchTarget> targets) {
        double signedTargetSum = targets.stream().mapToDouble(target -> signedPower(target.resourceType(), target.targetPower())).sum();
        double residual = roundPower(item.netPowerTarget() - signedTargetSum);
        if (Math.abs(residual) < 0.001) return;
        for (int index = 0; index < targets.size(); index++) {
            ResourceDispatchTarget target = targets.get(index);
            if (target.resourceType() != ResourceType.GRID) continue;
            ResourceCapabilitySnapshot capability = snapshot.resourceCapabilitySnapshots().stream()
                    .filter(resource -> resource.stationId().equals(target.stationId())).findFirst().orElseThrow();
            double corrected = roundPower(target.targetPower() + residual);
            double minimumGridPower = -snapshot.marketSignalSnapshot().gridExportLimit();
            double maximumGridPower = Math.min(capability.maximumPower(), snapshot.marketSignalSnapshot().gridImportLimit());
            if (corrected < minimumGridPower || corrected > maximumGridPower) {
                throw new ApiException(HttpStatus.CONFLICT, "RULE_FALLBACK_BALANCE_UNSATISFIED",
                        "规则回退无法在资源边界内修正逐资源平衡", Map.of("residualKw", residual));
            }
            targets.set(index, new ResourceDispatchTarget(target.stationId(), target.resourceType(), corrected,
                    directionOf(corrected), target.optimizationStatus(), target.optimizationRound(), target.snapshotId(),
                    target.constraintEvidence(), target.generatedAt()));
            return;
        }
        throw new ApiException(HttpStatus.CONFLICT, "RULE_FALLBACK_BALANCE_UNSATISFIED",
                "规则回退缺少可用于修正逐资源平衡的电网资源", Map.of("residualKw", residual));
    }

    private double signedPower(ResourceType resourceType, double power) {
        return switch (resourceType) {
            case PHOTOVOLTAIC, WIND, THERMAL, GRID, STORAGE -> power;
            case LOAD, CHARGING -> -Math.abs(power);
        };
    }

    private double forecastPower(DispatchContextSnapshot snapshot, ResourceType resourceType, DispatchPlanItem item) {
        return snapshot.forecastSnapshot().points().stream()
                .filter(point -> point.intervalStart().equals(item.intervalStart()))
                .findFirst()
                .map(point -> resourceType == ResourceType.PHOTOVOLTAIC ? point.photovoltaicP50() : point.windP50())
                .orElse(0.0);
    }

    private DispatchPlanItem computePlanItem(
            String inputSnapshotId,
            MarketSignalSnapshot marketSignalSnapshot,
            CapabilityView capabilityView,
            ForecastPoint point,
            double intervalHours,
            boolean conservativePlan,
            double openingSoc,
            int itemOrder
    ) {
        double photovoltaicPower = point.photovoltaicP50();
        double windPower = point.windP50();
        double renewableGeneration = photovoltaicPower + windPower;
        double loadDemand = point.loadP50();
        double netSurplus = roundPower(renewableGeneration - loadDemand);

        double storageChargePower = 0.0;
        double storageDischargePower = 0.0;
        double flexibleLoadAdjustment = 0.0;
        double gridImportPower = 0.0;
        double gridExportPower = 0.0;
        double renewableCurtailment = 0.0;
        String reasonCode = "BALANCED";

        double availableChargePower = conservativePlan ? capabilityView.storageChargePower * 0.7 : capabilityView.storageChargePower;
        double availableDischargePower = conservativePlan ? capabilityView.storageDischargePower * 0.7 : capabilityView.storageDischargePower;
        double availableFlexibleUp = conservativePlan ? capabilityView.flexibleLoadIncrease * 0.6 : capabilityView.flexibleLoadIncrease;
        double availableFlexibleDown = conservativePlan ? capabilityView.flexibleLoadReduction * 0.6 : capabilityView.flexibleLoadReduction;

        if (netSurplus > 0) {
            storageChargePower = Math.min(netSurplus, availableChargePower);
            netSurplus = roundPower(netSurplus - storageChargePower);

            if (netSurplus > 0) {
                flexibleLoadAdjustment = -Math.min(netSurplus, availableFlexibleUp);
                netSurplus = roundPower(netSurplus - Math.abs(flexibleLoadAdjustment));
                reasonCode = "SURPLUS_FLEX_UP";
            }
            if (netSurplus > 0) {
                gridExportPower = Math.min(netSurplus, marketSignalSnapshot.gridExportLimit());
                netSurplus = roundPower(netSurplus - gridExportPower);
                reasonCode = "SURPLUS_EXPORT";
            }
            if (netSurplus > 0) {
                renewableCurtailment = netSurplus;
                netSurplus = 0.0;
                reasonCode = "SURPLUS_CURTAIL";
            }
            if (storageChargePower > 0 && "BALANCED".equals(reasonCode)) {
                reasonCode = "SURPLUS_CHARGE";
            }
        } else if (netSurplus < 0) {
            double deficit = Math.abs(netSurplus);
            storageDischargePower = Math.min(deficit, availableDischargePower);
            deficit = roundPower(deficit - storageDischargePower);

            if (deficit > 0) {
                flexibleLoadAdjustment = Math.min(deficit, availableFlexibleDown);
                deficit = roundPower(deficit - flexibleLoadAdjustment);
                reasonCode = "DEFICIT_FLEX_DOWN";
            }
            if (deficit > 0) {
                gridImportPower = Math.min(deficit, marketSignalSnapshot.gridImportLimit());
                deficit = roundPower(deficit - gridImportPower);
                reasonCode = "DEFICIT_IMPORT";
            }
            if (deficit > 0.001) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "POWER_BALANCE_UNSATISFIED",
                        "当前资源能力无法满足时间片功率平衡",
                        Map.of(
                                "intervalStart", point.intervalStart(),
                                "remainingDeficit", deficit
                        )
                );
            }
            if (storageDischargePower > 0 && "BALANCED".equals(reasonCode)) {
                reasonCode = "DEFICIT_DISCHARGE";
            }
        }

        double closingSoc = updateSoc(
                openingSoc,
                storageChargePower,
                storageDischargePower,
                capabilityView.storageConstraint,
                intervalHours
        );
        validateSoc(closingSoc, capabilityView.storageConstraint);

        double upwardReserve = Math.min(capabilityView.upwardReserve, marketSignalSnapshot.reserveUpRequirement());
        double downwardReserve = Math.min(capabilityView.downwardReserve, marketSignalSnapshot.reserveDownRequirement());
        double netPowerTarget = roundPower(
                renewableGeneration
                        + storageDischargePower
                        + gridImportPower
                        - storageChargePower
                        - gridExportPower
                        - loadDemand
                        + flexibleLoadAdjustment
                        - renewableCurtailment
        );

        BigDecimal expectedCost = BigDecimal.valueOf(gridImportPower)
                .multiply(marketSignalSnapshot.realTimePrice())
                .add(BigDecimal.valueOf(storageChargePower).multiply(capabilityView.storageCycleCost))
                .add(BigDecimal.valueOf(Math.max(flexibleLoadAdjustment, 0.0)).multiply(FLEXIBLE_LOAD_COMPENSATION))
                .setScale(6, RoundingMode.HALF_UP);
        BigDecimal expectedRevenue = BigDecimal.valueOf(gridExportPower)
                .multiply(point.priceP50())
                .setScale(6, RoundingMode.HALF_UP);
        BigDecimal riskCost = BigDecimal.valueOf(
                        storageDischargePower
                                + storageChargePower
                                + renewableCurtailment
                                + upwardReserve
                                + downwardReserve
                )
                .multiply(RISK_FACTOR)
                .setScale(6, RoundingMode.HALF_UP);

        return new DispatchPlanItem(
                "dispatch-plan-item-" + UUID.randomUUID(),
                inputSnapshotId,
                point.intervalStart(),
                point.intervalEnd(),
                netPowerTarget,
                roundPower(gridImportPower),
                roundPower(gridExportPower),
                roundPower(storageChargePower),
                roundPower(storageDischargePower),
                roundPower(flexibleLoadAdjustment),
                roundPower(upwardReserve),
                roundPower(downwardReserve),
                roundPower(renewableCurtailment),
                expectedCost,
                expectedRevenue,
                riskCost,
                reasonCode,
                itemOrder
        );
    }

    private CapabilityView buildCapabilityView(List<ResourceCapabilitySnapshot> snapshots, int intervalMinutes) {
        ResourceCapabilitySnapshot storageSnapshot = findByResourceType(snapshots, ResourceType.STORAGE);
        ResourceCapabilitySnapshot gridSnapshot = findByResourceType(snapshots, ResourceType.GRID);
        ResourceCapabilitySnapshot loadSnapshot = findByResourceType(snapshots, ResourceType.LOAD);
        ResourceCapabilitySnapshot chargingSnapshot = findByResourceType(snapshots, ResourceType.CHARGING);

        StorageConstraint storageConstraint = storageSnapshot.storageConstraint();
        double intervalHours = intervalMinutes / 60.0;
        double storageChargePower = storageSnapshot.dataTrusted()
                ? Math.max(0.0, Math.abs(storageSnapshot.minimumPower()))
                : 0.0;
        double storageDischargePower = storageSnapshot.dataTrusted()
                ? Math.max(0.0, storageSnapshot.maximumPower())
                : 0.0;

        if (storageConstraint != null) {
            double socChargeLimit = ((storageConstraint.maximumSoc() - storageConstraint.soc()) / 100.0
                    * storageConstraint.energyCapacityKWh()) / intervalHours;
            double socDischargeLimit = ((storageConstraint.soc() - storageConstraint.minimumSoc()) / 100.0
                    * storageConstraint.energyCapacityKWh()) / intervalHours;
            storageChargePower = Math.min(storageChargePower, Math.max(0.0, socChargeLimit));
            storageDischargePower = Math.min(storageDischargePower, Math.max(0.0, socDischargeLimit));
        }

        return new CapabilityView(
                storageConstraint == null ? 50.0 : storageConstraint.soc(),
                storageConstraint,
                storageChargePower,
                storageDischargePower,
                gridSnapshot.dataTrusted() ? gridSnapshot.maximumPower() : 0.0,
                gridSnapshot.dataTrusted() ? gridSnapshot.availableCapacity() : 0.0,
                loadSnapshot.dataTrusted() ? loadSnapshot.availableCapacity() * 0.35 : 0.0,
                chargingSnapshot.dataTrusted() ? chargingSnapshot.availableCapacity() * 0.25 : 0.0,
                storageConstraint == null ? BigDecimal.ZERO : storageConstraint.cycleCost(),
                storageConstraint == null ? 0.0 : storageConstraint.reservedUpPower(),
                storageConstraint == null ? 0.0 : storageConstraint.reservedDownPower()
        );
    }

    private ResourceCapabilitySnapshot findByResourceType(List<ResourceCapabilitySnapshot> snapshots, ResourceType resourceType) {
        return snapshots.stream()
                .filter(snapshot -> snapshot.resourceType() == resourceType)
                .findFirst()
                .orElseThrow(() -> new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "RESOURCE_CAPABILITY_MISSING",
                        "调度上下文缺少资源能力快照",
                        Map.of("resourceType", resourceType.getValue())
                ));
    }

    private double updateSoc(
            double currentSoc,
            double storageChargePower,
            double storageDischargePower,
            StorageConstraint storageConstraint,
            double intervalHours
    ) {
        if (storageConstraint == null) {
            return currentSoc;
        }
        double energyDelta = storageChargePower * storageConstraint.chargeEfficiency() * intervalHours
                - storageDischargePower / storageConstraint.dischargeEfficiency() * intervalHours;
        return currentSoc + energyDelta / storageConstraint.energyCapacityKWh() * 100.0;
    }

    private void validateSoc(double soc, StorageConstraint storageConstraint) {
        if (storageConstraint == null) {
            return;
        }
        if (soc < storageConstraint.minimumSoc() - 0.001 || soc > storageConstraint.maximumSoc() + 0.001) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "STORAGE_SOC_OUT_OF_RANGE",
                    "储能 SOC 约束被破坏",
                    Map.of("soc", roundPower(soc))
            );
        }
    }

    private double roundPower(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    private PowerDirection directionOf(double targetPower) {
        return targetPower > 0 ? PowerDirection.POSITIVE : targetPower < 0 ? PowerDirection.NEGATIVE : PowerDirection.NEUTRAL;
    }

    /**
     * 资源能力视图。
     *
     * @param storageSoc 储能 SOC
     * @param storageConstraint 储能约束
     * @param storageChargePower 储能可充功率
     * @param storageDischargePower 储能可放功率
     * @param gridImportLimit 市电可购功率
     * @param gridExportLimit 市电可售功率
     * @param flexibleLoadReduction 柔性负荷可下调功率
     * @param flexibleLoadIncrease 柔性负荷可上调功率
     * @param storageCycleCost 储能循环成本
     * @param upwardReserve 向上备用
     * @param downwardReserve 向下备用
     */
    private record CapabilityView(
            double storageSoc,
            StorageConstraint storageConstraint,
            double storageChargePower,
            double storageDischargePower,
            double gridImportLimit,
            double gridExportLimit,
            double flexibleLoadReduction,
            double flexibleLoadIncrease,
            BigDecimal storageCycleCost,
            double upwardReserve,
            double downwardReserve
    ) {
    }
}

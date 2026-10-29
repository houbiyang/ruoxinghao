package com.weaksignal.vpp.backend.capability;

import com.weaksignal.vpp.backend.domain.LinkState;
import com.weaksignal.vpp.backend.domain.QualityCode;
import com.weaksignal.vpp.backend.domain.ResourceType;
import com.weaksignal.vpp.backend.environment.EnvironmentAssessmentResult;
import java.time.OffsetDateTime;

/**
 * 资源能力不可变快照。
 *
 * @param capabilityId 能力快照标识
 * @param snapshotGroupId 能力快照分组标识
 * @param stationId 场站标识
 * @param resourceType 资源类型
 * @param currentPower 当前功率，单位 kW
 * @param availableCapacity 可调容量，单位 kW
 * @param minimumPower 最小功率边界，单位 kW
 * @param maximumPower 最大功率边界，单位 kW
 * @param maximumRampUp 最大上升爬坡率，单位 kW
 * @param maximumRampDown 最大下降爬坡率，单位 kW
 * @param availableEnergyKwh 当前可用能量，单位 kWh
 * @param storageConstraint 储能约束
 * @param environmentAssessment 环境感知评估结果
 * @param linkState 链路状态
 * @param dataAge 数据年龄，单位秒
 * @param capabilityConfidence 能力可信度
 * @param dataTrusted 数据是否可信
 * @param unavailableReason 不可用原因
 * @param generatedAt 生成时间
 * @param validFrom 生效时间
 * @param validUntil 失效时间
 * @param source 数据来源
 * @param qualityCode 数据质量
 * @param version 版本号
 * @param receivedAt 接收时间
 */
public record ResourceCapabilitySnapshot(
        String capabilityId,
        String snapshotGroupId,
        String stationId,
        ResourceType resourceType,
        double currentPower,
        double availableCapacity,
        double minimumPower,
        double maximumPower,
        double maximumRampUp,
        double maximumRampDown,
        double availableEnergyKwh,
        StorageConstraint storageConstraint,
        EnvironmentAssessmentResult environmentAssessment,
        LinkState linkState,
        long dataAge,
        double capabilityConfidence,
        boolean dataTrusted,
        String unavailableReason,
        OffsetDateTime generatedAt,
        OffsetDateTime validFrom,
        OffsetDateTime validUntil,
        String source,
        QualityCode qualityCode,
        String version,
        OffsetDateTime receivedAt
) {
    /**
     * 兼容旧版资源能力快照构造。
     *
     * @param capabilityId 能力快照标识
     * @param snapshotGroupId 能力快照分组标识
     * @param stationId 场站标识
     * @param resourceType 资源类型
     * @param currentPower 当前功率
     * @param availableCapacity 可调容量
     * @param minimumPower 最小功率边界
     * @param maximumPower 最大功率边界
     * @param maximumRampUp 最大上升爬坡率
     * @param maximumRampDown 最大下降爬坡率
     * @param availableEnergyKwh 当前可用能量
     * @param storageConstraint 储能约束
     * @param linkState 链路状态
     * @param dataAge 数据年龄
     * @param capabilityConfidence 能力可信度
     * @param dataTrusted 数据是否可信
     * @param unavailableReason 不可用原因
     * @param generatedAt 生成时间
     * @param validFrom 生效时间
     * @param validUntil 失效时间
     * @param source 数据来源
     * @param qualityCode 数据质量
     * @param version 版本号
     * @param receivedAt 接收时间
     */
    public ResourceCapabilitySnapshot(
            String capabilityId,
            String snapshotGroupId,
            String stationId,
            ResourceType resourceType,
            double currentPower,
            double availableCapacity,
            double minimumPower,
            double maximumPower,
            double maximumRampUp,
            double maximumRampDown,
            double availableEnergyKwh,
            StorageConstraint storageConstraint,
            LinkState linkState,
            long dataAge,
            double capabilityConfidence,
            boolean dataTrusted,
            String unavailableReason,
            OffsetDateTime generatedAt,
            OffsetDateTime validFrom,
            OffsetDateTime validUntil,
            String source,
            QualityCode qualityCode,
            String version,
            OffsetDateTime receivedAt
    ) {
        this(
                capabilityId,
                snapshotGroupId,
                stationId,
                resourceType,
                currentPower,
                availableCapacity,
                minimumPower,
                maximumPower,
                maximumRampUp,
                maximumRampDown,
                availableEnergyKwh,
                storageConstraint,
                null,
                linkState,
                dataAge,
                capabilityConfidence,
                dataTrusted,
                unavailableReason,
                generatedAt,
                validFrom,
                validUntil,
                source,
                qualityCode,
                version,
                receivedAt
        );
    }
}

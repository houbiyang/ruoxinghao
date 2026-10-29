package com.weaksignal.vpp.backend.marketdispatch;

import com.weaksignal.vpp.backend.domain.ResourceType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 不可变的逐资源优化目标，是计划、安全门和控制命令的唯一功率来源。
 *
 * @param stationId 场站标识
 * @param resourceType 资源类型
 * @param targetPower 目标功率，单位 kW
 * @param powerDirection 目标功率方向
 * @param optimizationStatus 优化状态
 * @param optimizationRound 优化轮次
 * @param snapshotId 输入快照标识
 * @param constraintEvidence 该目标的约束证据
 * @param generatedAt 目标生成时间
 */
public record ResourceDispatchTarget(
        String stationId,
        ResourceType resourceType,
        double targetPower,
        PowerDirection powerDirection,
        OptimizationStatus optimizationStatus,
        int optimizationRound,
        String snapshotId,
        Map<String, Object> constraintEvidence,
        OffsetDateTime generatedAt
) {
    /**
     * 复制约束证据，防止调用方在构造后更改目标内容。
     */
    public ResourceDispatchTarget {
        if (stationId == null || stationId.isBlank() || resourceType == null || snapshotId == null || snapshotId.isBlank()
                || !Double.isFinite(targetPower)) {
            throw new IllegalArgumentException("逐资源目标字段非法");
        }
        constraintEvidence = Map.copyOf(constraintEvidence == null ? Map.of() : constraintEvidence);
    }

    /**
     * 为稳定排序后的逐资源目标计算 SHA-256 摘要。
     *
     * @param targets 逐资源目标集合
     * @return 十六进制摘要
     */
    public static String summaryHash(List<ResourceDispatchTarget> targets) {
        String canonicalTargets = targets.stream()
                .sorted(Comparator.comparing(ResourceDispatchTarget::stationId))
                .map(target -> target.stationId() + "|" + target.resourceType().name() + "|"
                        + Double.toString(target.targetPower()) + "|" + String.valueOf(target.powerDirection()) + "|"
                        + String.valueOf(target.optimizationStatus()) + "|" + target.optimizationRound() + "|" + target.snapshotId()
                        + "|" + (target.generatedAt() == null ? "" : target.generatedAt().toInstant()))
                .reduce((left, right) -> left + "\\n" + right)
                .orElse("");
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonicalTargets.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                result.append(String.format("%02x", value));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 算法不可用", exception);
        }
    }
}

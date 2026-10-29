package com.weaksignal.vpp.backend.fault;

import java.util.List;

/**
 * 安全门最终裁决。
 *
 * @param approvedActions 通过白名单与状态校验的动作
 * @param requiresHumanApproval 兼容字段，自动裁决模式正常为 false
 * @param conflictDetected 是否存在智能体意见冲突
 * @param fallbackUsed 是否使用保守回退
 * @param explanation 裁决说明
 */
public record FaultSafetyDecision(
        List<ProposedFaultAction> approvedActions,
        boolean requiresHumanApproval,
        boolean conflictDetected,
        boolean fallbackUsed,
        String explanation
) {
}

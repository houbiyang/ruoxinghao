package com.weaksignal.vpp.backend.marketagent;

import java.util.List;

/**
 * 市场调度智能体安全门裁决。
 *
 * @param decisionLevel 裁决级别，取值为 PASSED、DEGRADED 或 REJECTED
 * @param passed 是否通过安全门
 * @param fallbackUsed 是否使用回退策略
 * @param approvedActions 允许进入下一步的候选动作
 * @param explanation 安全门说明
 */
public record MarketSafetyDecision(
        String decisionLevel,
        boolean passed,
        boolean fallbackUsed,
        List<String> approvedActions,
        String explanation
) {
}

package com.weaksignal.vpp.backend.marketagent;

/**
 * Sequential 工作流阶段执行状态。
 */
public enum MarketAgentStageStatus {
    SUCCESS,
    RETRYING,
    RULE_FALLBACK,
    BLOCKED,
    FAILED
}

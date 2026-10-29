package com.weaksignal.vpp.backend.marketagent;

/**
 * Sequential 工作流阶段错误分类。
 */
public enum MarketAgentErrorCategory {
    NONE,
    TIMEOUT,
    RATE_LIMIT,
    PROVIDER_5XX,
    PROVIDER_TRANSPORT_ERROR,
    PROVIDER_ENDPOINT_NOT_FOUND,
    AUTH_ERROR,
    INVALID_SCHEMA,
    INVALID_ACTION,
    STALE_INPUT,
    CONSTRAINT_VIOLATION
}

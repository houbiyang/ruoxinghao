package com.weaksignal.vpp.backend.domain;

/**
 * 市场调度计划状态。
 */
public enum DispatchPlanStatus {
    DRAFT,
    VALIDATED,
    PENDING_APPROVAL,
    APPROVED,
    ACTIVE,
    SUPERSEDED,
    EXPIRED,
    REJECTED,
    COMPLETED
}

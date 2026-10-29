package com.weaksignal.vpp.backend.fault;

/**
 * 故障诊断运行状态。
 */
public enum FaultRunStatus {
    ANALYZING,
    PENDING_APPROVAL,
    READY,
    EXECUTING,
    EXECUTED,
    REJECTED,
    FAILED
}

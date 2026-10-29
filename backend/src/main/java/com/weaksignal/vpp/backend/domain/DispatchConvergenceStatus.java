package com.weaksignal.vpp.backend.domain;

/**
 * 执行后遥测收敛跟踪状态。
 */
public enum DispatchConvergenceStatus {
    WAITING_ACK,
    WAITING_TELEMETRY,
    TRACKING,
    TARGET_REACHED,
    REOPTIMIZATION_REQUIRED,
    CONVERGENCE_TIMEOUT,
    FAILED
}

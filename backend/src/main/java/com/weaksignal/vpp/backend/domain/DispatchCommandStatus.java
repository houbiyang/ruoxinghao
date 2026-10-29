package com.weaksignal.vpp.backend.domain;

/**
 * 调度控制指令状态。
 */
public enum DispatchCommandStatus {
    CREATED,
    SENT,
    ACCEPTED,
    EXECUTED,
    REJECTED,
    TIMEOUT,
    FAILED;

    /**
     * 判断当前状态是否已经结束。
     *
     * @return 是否为终态
     */
    public boolean isTerminal() {
        return this == EXECUTED || this == REJECTED || this == TIMEOUT || this == FAILED;
    }
}

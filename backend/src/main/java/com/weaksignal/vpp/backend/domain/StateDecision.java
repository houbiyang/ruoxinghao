package com.weaksignal.vpp.backend.domain;

/**
 * 状态判定结果。
 *
 * @param nextState 下一状态
 * @param changed 是否发生变化
 * @param reason 变化原因
 */
public record StateDecision(
        LinkState nextState,
        boolean changed,
        String reason
) {
}


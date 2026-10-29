package com.weaksignal.vpp.simulator;

/**
 * 调度控制指令校验结果。
 *
 * @param accepted 是否接受
 * @param message 中文结果说明
 */
public record DispatchControlResult(
        boolean accepted,
        String message
) {
}

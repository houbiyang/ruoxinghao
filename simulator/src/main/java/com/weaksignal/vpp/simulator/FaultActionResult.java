package com.weaksignal.vpp.simulator;

/**
 * 故障处置动作执行结果。
 *
 * @param accepted 是否接受
 * @param message 执行说明
 */
public record FaultActionResult(
        boolean accepted,
        String message
) {
}

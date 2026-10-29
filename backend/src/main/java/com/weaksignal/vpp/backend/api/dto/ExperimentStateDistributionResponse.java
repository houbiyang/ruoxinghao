package com.weaksignal.vpp.backend.api.dto;

/**
 * 链路状态分布响应。
 *
 * @param linkState 链路状态
 * @param count 出现次数
 */
public record ExperimentStateDistributionResponse(
        String linkState,
        long count
) {
}

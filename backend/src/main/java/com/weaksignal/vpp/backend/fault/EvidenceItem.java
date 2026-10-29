package com.weaksignal.vpp.backend.fault;

/**
 * 故障诊断证据。
 *
 * @param metric 指标名称
 * @param value 指标值
 * @param unit 单位
 * @param description 证据说明
 */
public record EvidenceItem(
        String metric,
        String value,
        String unit,
        String description
) {
}

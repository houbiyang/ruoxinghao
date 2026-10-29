package com.weaksignal.vpp.backend.domain;

/**
 * 实验故障参数定义。
 *
 * @param name 参数名称
 * @param unit 参数单位
 * @param defaultValue 默认值
 * @param minimum 最小值
 * @param maximum 最大值
 * @param step 步长
 */
public record ExperimentParameterDefinition(
        String name,
        String unit,
        double defaultValue,
        double minimum,
        double maximum,
        double step
) {
}

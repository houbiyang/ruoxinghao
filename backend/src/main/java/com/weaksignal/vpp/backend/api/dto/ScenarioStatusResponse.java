package com.weaksignal.vpp.backend.api.dto;

import com.weaksignal.vpp.backend.domain.ControlMode;
import com.weaksignal.vpp.backend.domain.DemoScenario;

/**
 * 当前场景响应。
 *
 * @param scenario 当前场景
 * @param controlMode 当前控制模式
 */
public record ScenarioStatusResponse(
        DemoScenario scenario,
        ControlMode controlMode
) {
}


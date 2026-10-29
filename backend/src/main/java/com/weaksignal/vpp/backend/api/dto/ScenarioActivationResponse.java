package com.weaksignal.vpp.backend.api.dto;

import com.weaksignal.vpp.backend.domain.ControlMode;
import com.weaksignal.vpp.backend.domain.DemoScenario;
import java.time.OffsetDateTime;

/**
 * 场景激活响应。
 *
 * @param scenario 激活场景
 * @param controlMode 控制模式
 * @param requestedAt 请求时间
 * @param commandId 命令标识
 * @param status 执行状态
 */
public record ScenarioActivationResponse(
        DemoScenario scenario,
        ControlMode controlMode,
        OffsetDateTime requestedAt,
        String commandId,
        String status
) {
}


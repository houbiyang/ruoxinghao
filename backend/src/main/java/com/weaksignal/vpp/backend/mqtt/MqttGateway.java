package com.weaksignal.vpp.backend.mqtt;

import com.weaksignal.vpp.backend.mqtt.dto.ScenarioApplyCommandMessage;
import com.weaksignal.vpp.backend.mqtt.dto.DispatchControlMessage;
import com.weaksignal.vpp.backend.mqtt.dto.DispatchControlReleaseMessage;
import com.weaksignal.vpp.backend.mqtt.dto.FaultActionCommandMessage;

/**
 * MQTT 发布网关。
 */
public interface MqttGateway {

    /**
     * 发布全局场景切换命令。
     *
     * @param message 场景命令
     */
    void publishGlobalScenario(ScenarioApplyCommandMessage message);

    /**
     * 向指定场站发布功率控制指令。
     *
     * @param message 控制指令
     * @return 是否成功提交到 MQTT 客户端
     */
    boolean publishDispatchControl(DispatchControlMessage message);

    /**
     * 向指定场站发布调度控制释放消息。
     *
     * @param message 调度控制释放消息
     * @return 是否成功提交到 MQTT 客户端
     */
    default boolean publishDispatchControlRelease(DispatchControlReleaseMessage message) {
        return false;
    }

    /**
     * 发布经过安全门批准的故障处置动作。
     *
     * @param message 故障动作
     * @return 是否成功提交到 MQTT
     */
    boolean publishFaultAction(FaultActionCommandMessage message);
}

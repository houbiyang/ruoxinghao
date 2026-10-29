package com.weaksignal.vpp.backend.api.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 系统配置脱敏响应。
 *
 * @param sourceName 配置来源名称
 * @param writable 配置文件是否可写
 * @param pendingRestart 配置文件与当前进程环境是否存在差异
 * @param lastModifiedAt 配置文件最近修改时间
 * @param mqtt MQTT 配置
 * @param ai AI 配置
 * @param simulation 模拟器配置
 */
public record SystemConfigurationResponse(
        String sourceName,
        boolean writable,
        boolean pendingRestart,
        OffsetDateTime lastModifiedAt,
        MqttConfiguration mqtt,
        AiConfiguration ai,
        SimulationConfiguration simulation
) {

    /**
     * MQTT 配置。
     *
     * @param host Broker 主机
     * @param port Broker 端口
     * @param username 用户名
     * @param passwordConfigured 是否已配置密码
     * @param clientId 后端客户端标识
     * @param reconnectDelayMillis 重连间隔毫秒数
     */
    public record MqttConfiguration(
            String host,
            int port,
            String username,
            boolean passwordConfigured,
            String clientId,
            long reconnectDelayMillis
    ) {
    }

    /**
     * AI 配置。
     *
     * @param globallyEnabled AI 总开关
     * @param maximumContextCharacters 最大上下文字符数
     * @param providerCode Provider 编码
     * @param providerEnabled Provider 开关
     * @param baseUrl API 基础地址
     * @param apiKeyConfigured 是否已配置 API Key
     * @param apiKeyMasked 脱敏后的 API Key 提示
     * @param model 模型名称
     * @param timeoutSeconds 请求超时秒数
     */
    public record AiConfiguration(
            boolean globallyEnabled,
            int maximumContextCharacters,
            String providerCode,
            boolean providerEnabled,
            String baseUrl,
            boolean apiKeyConfigured,
            String apiKeyMasked,
            String model,
            int timeoutSeconds
    ) {
    }

    /**
     * 模拟器配置。
     *
     * @param intervalMillis 遥测发布周期毫秒数
     * @param randomSeed 随机种子
     * @param initialScenario 初始场景
     * @param stationIds 启用场站标识
     */
    public record SimulationConfiguration(
            long intervalMillis,
            long randomSeed,
            String initialScenario,
            List<String> stationIds
    ) {
    }
}

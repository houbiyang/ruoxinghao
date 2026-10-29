package com.weaksignal.vpp.backend.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 系统配置更新请求。
 *
 * @param mqtt MQTT 配置
 * @param ai AI 配置
 * @param simulation 模拟器配置
 */
public record SystemConfigurationUpdateRequest(
        @NotNull @Valid MqttConfiguration mqtt,
        @NotNull @Valid AiConfiguration ai,
        @NotNull @Valid SimulationConfiguration simulation
) {

    /**
     * MQTT 配置更新内容。
     *
     * @param host Broker 主机
     * @param port Broker 端口
     * @param username 用户名
     * @param password 新密码，留空表示保留原值
     * @param clientId 后端客户端标识
     * @param reconnectDelayMillis 重连间隔毫秒数
     */
    public record MqttConfiguration(
            @NotBlank @Size(max = 255) String host,
            @Min(1) @Max(65535) int port,
            @Size(max = 255) String username,
            @Size(max = 4096) String password,
            @NotBlank @Size(max = 255) String clientId,
            @Min(1000) @Max(300000) long reconnectDelayMillis
    ) {
    }

    /**
     * AI 配置更新内容。
     *
     * @param globallyEnabled AI 总开关
     * @param maximumContextCharacters 最大上下文字符数
     * @param providerEnabled DeepSeek Provider 开关
     * @param baseUrl API 基础地址
     * @param apiKey 新 API Key，留空表示保留原值
     * @param model 模型名称
     * @param timeoutSeconds 请求超时秒数
     */
    public record AiConfiguration(
            boolean globallyEnabled,
            @Min(1000) @Max(100000) int maximumContextCharacters,
            boolean providerEnabled,
            @NotBlank @Size(max = 2048) String baseUrl,
            @Size(max = 4096) String apiKey,
            @NotBlank @Size(max = 255) String model,
            @Min(1) @Max(300) int timeoutSeconds
    ) {
    }

    /**
     * 模拟器配置更新内容。
     *
     * @param intervalMillis 遥测发布周期毫秒数
     * @param randomSeed 随机种子
     * @param initialScenario 初始场景
     * @param stationIds 启用场站标识
     */
    public record SimulationConfiguration(
            @Min(100) @Max(60000) long intervalMillis,
            long randomSeed,
            @NotBlank
            @Pattern(regexp = "stable|degraded|lost|recovering", message = "初始场景不受支持")
            String initialScenario,
            @NotNull @Size(max = 100) List<@Pattern(regexp = "[A-Za-z0-9._-]+") String> stationIds
    ) {
    }
}

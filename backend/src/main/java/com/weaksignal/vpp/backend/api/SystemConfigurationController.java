package com.weaksignal.vpp.backend.api;

import com.weaksignal.vpp.backend.api.dto.AiConnectionTestRequest;
import com.weaksignal.vpp.backend.api.dto.ConfigurationTestResponse;
import com.weaksignal.vpp.backend.api.dto.MqttConnectionTestRequest;
import com.weaksignal.vpp.backend.api.dto.SystemConfigurationResponse;
import com.weaksignal.vpp.backend.api.dto.SystemConfigurationUpdateRequest;
import com.weaksignal.vpp.backend.configuration.SystemConfigurationService;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统配置接口控制器。
 */
@RestController
@RequestMapping("/api/v1/settings")
public class SystemConfigurationController {

    private final SystemConfigurationService systemConfigurationService;
    private final Clock clock;

    /**
     * 注入系统配置服务。
     *
     * @param systemConfigurationService 系统配置服务
     * @param clock 系统时钟
     */
    public SystemConfigurationController(SystemConfigurationService systemConfigurationService, Clock clock) {
        this.systemConfigurationService = systemConfigurationService;
        this.clock = clock;
    }

    /**
     * 查询项目环境配置，敏感字段不会返回明文。
     *
     * @return 脱敏系统配置
     */
    @GetMapping
    public ApiResponse<SystemConfigurationResponse> getConfiguration() {
        return ApiResponse.success(systemConfigurationService.getConfiguration(), OffsetDateTime.now(clock));
    }

    /**
     * 保存 MQTT、AI 和模拟器配置到项目根目录 {@code .env}。
     *
     * @param request 配置更新请求
     * @return 保存后的脱敏系统配置
     */
    @PutMapping
    public ApiResponse<SystemConfigurationResponse> updateConfiguration(
            @Valid @RequestBody SystemConfigurationUpdateRequest request
    ) {
        return ApiResponse.success(systemConfigurationService.updateConfiguration(request), OffsetDateTime.now(clock));
    }

    /**
     * 测试 MQTT Broker 连通性及账号密码。
     *
     * @param request MQTT 测试请求
     * @return MQTT 测试结果
     */
    @PostMapping("/mqtt/test")
    public ApiResponse<ConfigurationTestResponse> testMqttConnection(
            @Valid @RequestBody MqttConnectionTestRequest request
    ) {
        return ApiResponse.success(systemConfigurationService.testMqttConnection(request), OffsetDateTime.now(clock));
    }

    /**
     * 测试 AI Provider 地址和 API Key。
     *
     * @param request AI Provider 测试请求
     * @return AI Provider 测试结果
     */
    @PostMapping("/ai/test")
    public ApiResponse<ConfigurationTestResponse> testAiConnection(
            @Valid @RequestBody AiConnectionTestRequest request
    ) {
        return ApiResponse.success(systemConfigurationService.testAiConnection(request), OffsetDateTime.now(clock));
    }
}

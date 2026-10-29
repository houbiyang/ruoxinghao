package com.weaksignal.vpp.backend.configuration;

import com.weaksignal.vpp.backend.api.ApiException;
import com.weaksignal.vpp.backend.api.dto.AiConnectionTestRequest;
import com.weaksignal.vpp.backend.api.dto.ConfigurationTestResponse;
import com.weaksignal.vpp.backend.api.dto.MqttConnectionTestRequest;
import com.weaksignal.vpp.backend.api.dto.SystemConfigurationResponse;
import com.weaksignal.vpp.backend.api.dto.SystemConfigurationUpdateRequest;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.eclipse.paho.mqttv5.client.MqttAsyncClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 系统配置查询、保存与连通性测试服务。
 */
@Service
public class SystemConfigurationService {

    private static final Set<String> TRACKED_KEYS = Set.of(
            "MQTT_HOST",
            "MQTT_PORT",
            "EMQX_USERNAME",
            "EMQX_PASSWORD",
            "BACKEND_MQTT_CLIENT_ID",
            "BACKEND_MQTT_RECONNECT_DELAY_MS",
            "VPP_AI_ENABLED",
            "VPP_AI_MAX_CONTEXT_CHARACTERS",
            "DEEPSEEK_ENABLED",
            "DEEPSEEK_BASE_URL",
            "DEEPSEEK_API_KEY",
            "DEEPSEEK_MODEL",
            "DEEPSEEK_TIMEOUT_SECONDS",
            "SIMULATION_INTERVAL_MS",
            "SIMULATION_RANDOM_SEED",
            "SIMULATION_INITIAL_SCENARIO",
            "SIMULATION_STATION_IDS"
    );

    private final EnvironmentFileStore environmentFileStore;
    private final Clock clock;

    /**
     * 注入环境文件和系统时钟。
     *
     * @param environmentFileStore 环境文件存储
     * @param clock 系统时钟
     */
    public SystemConfigurationService(EnvironmentFileStore environmentFileStore, Clock clock) {
        this.environmentFileStore = environmentFileStore;
        this.clock = clock;
    }

    /**
     * 查询脱敏后的配置文件内容。
     *
     * @return 系统配置
     */
    public SystemConfigurationResponse getConfiguration() {
        Map<String, String> values = readValues();
        return toResponse(values);
    }

    /**
     * 保存白名单内的配置到项目根目录 {@code .env} 文件。
     *
     * @param request 配置更新请求
     * @return 保存后的脱敏配置
     */
    public SystemConfigurationResponse updateConfiguration(SystemConfigurationUpdateRequest request) {
        Map<String, String> currentValues = readValues();
        validateUpdateRequest(request, currentValues);

        Map<String, String> updates = new LinkedHashMap<>();
        updates.put("MQTT_HOST", request.mqtt().host().trim());
        updates.put("MQTT_PORT", Integer.toString(request.mqtt().port()));
        updates.put("EMQX_USERNAME", request.mqtt().username().trim());
        updates.put("BACKEND_MQTT_CLIENT_ID", request.mqtt().clientId().trim());
        updates.put("BACKEND_MQTT_RECONNECT_DELAY_MS", Long.toString(request.mqtt().reconnectDelayMillis()));
        updates.put("VPP_AI_ENABLED", Boolean.toString(request.ai().globallyEnabled()));
        updates.put("VPP_AI_MAX_CONTEXT_CHARACTERS", Integer.toString(request.ai().maximumContextCharacters()));
        updates.put("DEEPSEEK_ENABLED", Boolean.toString(request.ai().providerEnabled()));
        updates.put("DEEPSEEK_BASE_URL", request.ai().baseUrl().trim());
        updates.put("DEEPSEEK_MODEL", request.ai().model().trim());
        updates.put("DEEPSEEK_TIMEOUT_SECONDS", Integer.toString(request.ai().timeoutSeconds()));
        updates.put("SIMULATION_INTERVAL_MS", Long.toString(request.simulation().intervalMillis()));
        updates.put("SIMULATION_RANDOM_SEED", Long.toString(request.simulation().randomSeed()));
        updates.put("SIMULATION_INITIAL_SCENARIO", request.simulation().initialScenario());
        updates.put("SIMULATION_STATION_IDS", String.join(",", request.simulation().stationIds()));

        if (hasText(request.mqtt().password())) {
            updates.put("EMQX_PASSWORD", request.mqtt().password());
        }
        if (hasText(request.ai().apiKey())) {
            updates.put("DEEPSEEK_API_KEY", request.ai().apiKey());
        }

        for (Map.Entry<String, String> update : updates.entrySet()) {
            validateEnvironmentValue(update.getKey(), update.getValue());
        }

        try {
            environmentFileStore.updateValues(updates);
            return toResponse(environmentFileStore.readValues());
        } catch (IOException exception) {
            throw configurationFileException(exception);
        }
    }

    /**
     * 使用输入值或已保存密码验证 MQTT 连接和鉴权。
     *
     * @param request MQTT 连接测试请求
     * @return 测试结果
     */
    public ConfigurationTestResponse testMqttConnection(MqttConnectionTestRequest request) {
        Map<String, String> values = readValues();
        String password = hasText(request.password()) ? request.password() : value(values, "EMQX_PASSWORD", "");
        String target = request.host().trim() + ":" + request.port();
        OffsetDateTime checkedAt = OffsetDateTime.now(clock);

        MqttAsyncClient client = null;
        try {
            client = new MqttAsyncClient(
                    "tcp://" + target,
                    "vpp-config-check-" + UUID.randomUUID(),
                    new MemoryPersistence()
            );
            MqttConnectionOptions options = new MqttConnectionOptions();
            options.setUserName(request.username().trim());
            options.setPassword(password.getBytes(StandardCharsets.UTF_8));
            options.setConnectionTimeout(5);
            options.setAutomaticReconnect(false);
            options.setCleanStart(true);
            client.connect(options).waitForCompletion(6000L);
            return new ConfigurationTestResponse(true, target, "MQTT Broker 连接和账号校验成功", null, checkedAt);
        } catch (MqttException exception) {
            return new ConfigurationTestResponse(false, target, "MQTT 连接或账号校验失败", null, checkedAt);
        } finally {
            closeMqttClient(client);
        }
    }

    /**
     * 使用输入值或已保存 API Key 验证 AI Provider 可访问性。
     *
     * @param request AI Provider 连接测试请求
     * @return 测试结果
     */
    public ConfigurationTestResponse testAiConnection(AiConnectionTestRequest request) {
        Map<String, String> values = readValues();
        String apiKey = hasText(request.apiKey()) ? request.apiKey() : value(values, "DEEPSEEK_API_KEY", "");
        OffsetDateTime checkedAt = OffsetDateTime.now(clock);
        if (!hasText(apiKey) || "replace_me".equals(apiKey)) {
            return new ConfigurationTestResponse(false, request.baseUrl().trim(), "请先输入有效的 API Key", null, checkedAt);
        }

        URI endpoint;
        try {
            endpoint = resolveModelsEndpoint(request.baseUrl());
        } catch (IllegalArgumentException exception) {
            return new ConfigurationTestResponse(false, request.baseUrl().trim(), "API 地址格式不正确", null, checkedAt);
        }

        try {
            HttpClient httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(request.timeoutSeconds()))
                    .build();
            HttpRequest httpRequest = HttpRequest.newBuilder(endpoint)
                    .timeout(Duration.ofSeconds(request.timeoutSeconds()))
                    .header("Authorization", "Bearer " + apiKey)
                    .GET()
                    .build();
            HttpResponse<Void> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.discarding());
            boolean success = response.statusCode() >= 200 && response.statusCode() < 300;
            return new ConfigurationTestResponse(
                    success,
                    endpoint.toString(),
                    success ? "API Key 校验成功，Provider 可访问" : "Provider 返回 HTTP " + response.statusCode(),
                    response.statusCode(),
                    checkedAt
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new ConfigurationTestResponse(false, endpoint.toString(), "API Key 测试被中断", null, checkedAt);
        } catch (Exception exception) {
            return new ConfigurationTestResponse(false, endpoint.toString(), "无法连接 AI Provider，请检查地址、网络和密钥", null, checkedAt);
        }
    }

    private SystemConfigurationResponse toResponse(Map<String, String> values) {
        try {
            return new SystemConfigurationResponse(
                    "项目根目录 .env",
                    environmentFileStore.isWritable(),
                    hasPendingRestart(values),
                    environmentFileStore.lastModifiedAt(clock.getZone()),
                    new SystemConfigurationResponse.MqttConfiguration(
                            value(values, "MQTT_HOST", "127.0.0.1"),
                            intValue(values, "MQTT_PORT", 1883),
                            value(values, "EMQX_USERNAME", "vpp_client"),
                            isSecretConfigured(value(values, "EMQX_PASSWORD", "")),
                            value(values, "BACKEND_MQTT_CLIENT_ID", "vpp-backend-core"),
                            longValue(values, "BACKEND_MQTT_RECONNECT_DELAY_MS", 3000L)
                    ),
                    new SystemConfigurationResponse.AiConfiguration(
                            booleanValue(values, "VPP_AI_ENABLED", false),
                            intValue(values, "VPP_AI_MAX_CONTEXT_CHARACTERS", 6000),
                            "deepseek",
                            booleanValue(values, "DEEPSEEK_ENABLED", false),
                            value(values, "DEEPSEEK_BASE_URL", "https://api.deepseek.com"),
                            isSecretConfigured(value(values, "DEEPSEEK_API_KEY", "")),
                            maskSecret(value(values, "DEEPSEEK_API_KEY", "")),
                            value(values, "DEEPSEEK_MODEL", "deepseek-v4-flash"),
                            intValue(values, "DEEPSEEK_TIMEOUT_SECONDS", 30)
                    ),
                    new SystemConfigurationResponse.SimulationConfiguration(
                            longValue(values, "SIMULATION_INTERVAL_MS", 1000L),
                            longValue(values, "SIMULATION_RANDOM_SEED", 20260629L),
                            value(values, "SIMULATION_INITIAL_SCENARIO", "stable"),
                            splitStationIds(value(values, "SIMULATION_STATION_IDS", ""))
                    )
            );
        } catch (IOException exception) {
            throw configurationFileException(exception);
        }
    }

    private void validateUpdateRequest(SystemConfigurationUpdateRequest request, Map<String, String> currentValues) {
        validateHost(request.mqtt().host());
        validateHttpUrl(request.ai().baseUrl());
        if (request.ai().globallyEnabled() && request.ai().providerEnabled()
                && !hasText(request.ai().apiKey())
                && !isSecretConfigured(value(currentValues, "DEEPSEEK_API_KEY", ""))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AI_API_KEY_REQUIRED", "启用 AI 分析前请配置 API Key", Map.of());
        }
    }

    private void validateHost(String host) {
        if (host.contains("://") || host.contains("/") || host.contains("\\")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_MQTT_HOST", "MQTT 主机仅支持主机名或 IP 地址", Map.of());
        }
    }

    private void validateHttpUrl(String baseUrl) {
        try {
            URI uri = URI.create(baseUrl.trim());
            if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
                throw new IllegalArgumentException("协议不支持");
            }
            if (!hasText(uri.getHost())) {
                throw new IllegalArgumentException("主机为空");
            }
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_AI_BASE_URL", "AI API 地址格式不正确", Map.of());
        }
    }

    private void validateEnvironmentValue(String key, String value) {
        if (value.contains("\r") || value.contains("\n")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CONFIGURATION_VALUE", key + " 不允许包含换行符", Map.of());
        }
    }

    private URI resolveModelsEndpoint(String baseUrl) {
        validateHttpUrl(baseUrl);
        String normalized = baseUrl.trim().replaceAll("/+$", "");
        if (normalized.endsWith("/chat/completions")) {
            normalized = normalized.substring(0, normalized.length() - "/chat/completions".length());
        }
        return URI.create(normalized + "/models");
    }

    private boolean hasPendingRestart(Map<String, String> values) {
        return TRACKED_KEYS.stream().anyMatch(key -> !Objects.equals(
                value(values, key, ""),
                value(System.getenv(), key, "")
        ));
    }

    private Map<String, String> readValues() {
        try {
            return environmentFileStore.readValues();
        } catch (IOException exception) {
            throw configurationFileException(exception);
        }
    }

    private ApiException configurationFileException(IOException exception) {
        return new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "ENV_FILE_UNAVAILABLE", "无法读取或保存项目 .env 配置文件", Map.of());
    }

    private void closeMqttClient(MqttAsyncClient client) {
        if (client == null) {
            return;
        }
        try {
            if (client.isConnected()) {
                client.disconnect().waitForCompletion(3000L);
            }
            client.close();
        } catch (MqttException exception) {
            // 测试完成后的连接释放失败不影响已返回的连接结果。
        }
    }

    private List<String> splitStationIds(String stationIds) {
        if (!hasText(stationIds)) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (String stationId : stationIds.split(",")) {
            String trimmedStationId = stationId.trim();
            if (hasText(trimmedStationId)) {
                values.add(trimmedStationId);
            }
        }
        return List.copyOf(values);
    }

    private boolean booleanValue(Map<String, String> values, String key, boolean defaultValue) {
        return Boolean.parseBoolean(value(values, key, Boolean.toString(defaultValue)));
    }

    private int intValue(Map<String, String> values, String key, int defaultValue) {
        try {
            return Integer.parseInt(value(values, key, Integer.toString(defaultValue)));
        } catch (NumberFormatException exception) {
            return defaultValue;
        }
    }

    private long longValue(Map<String, String> values, String key, long defaultValue) {
        try {
            return Long.parseLong(value(values, key, Long.toString(defaultValue)));
        } catch (NumberFormatException exception) {
            return defaultValue;
        }
    }

    private String value(Map<String, String> values, String key, String defaultValue) {
        String configuredValue = values.get(key);
        return configuredValue == null || configuredValue.isBlank() ? defaultValue : configuredValue;
    }

    private boolean isSecretConfigured(String secret) {
        return hasText(secret) && !"replace_me".equals(secret);
    }

    private String maskSecret(String secret) {
        if (!isSecretConfigured(secret)) {
            return "未配置";
        }
        int visibleLength = Math.min(4, secret.length());
        return "已配置（末尾 " + secret.substring(secret.length() - visibleLength) + "）";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

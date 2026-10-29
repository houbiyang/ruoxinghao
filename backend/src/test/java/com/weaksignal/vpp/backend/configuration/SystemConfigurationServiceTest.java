package com.weaksignal.vpp.backend.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.weaksignal.vpp.backend.api.dto.SystemConfigurationResponse;
import com.weaksignal.vpp.backend.api.dto.SystemConfigurationUpdateRequest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link SystemConfigurationService} 测试。
 */
class SystemConfigurationServiceTest {

    @TempDir
    Path temporaryDirectory;

    /**
     * 验证查询会脱敏密钥，保存会保留空白敏感字段并更新白名单配置。
     *
     * @throws Exception 文件操作异常
     */
    @Test
    void shouldMaskSecretsAndPreserveBlankSecretUpdates() throws Exception {
        Path environmentFile = temporaryDirectory.resolve(".env");
        Files.writeString(environmentFile, environmentFileContent(), StandardCharsets.UTF_8);
        SystemConfigurationService service = createService(environmentFile);

        SystemConfigurationResponse initialConfiguration = service.getConfiguration();

        assertTrue(initialConfiguration.mqtt().passwordConfigured());
        assertTrue(initialConfiguration.ai().apiKeyConfigured());
        assertTrue(initialConfiguration.ai().apiKeyMasked().contains("7890"));
        assertFalse(initialConfiguration.ai().apiKeyMasked().contains("secret-key"));

        SystemConfigurationResponse savedConfiguration = service.updateConfiguration(new SystemConfigurationUpdateRequest(
                new SystemConfigurationUpdateRequest.MqttConfiguration(
                        "10.0.0.12", 2883, "updated-user", "", "vpp-backend-updated", 5000L
                ),
                new SystemConfigurationUpdateRequest.AiConfiguration(
                        true, 8000, true, "https://api.deepseek.com", "", "deepseek-chat", 45
                ),
                new SystemConfigurationUpdateRequest.SimulationConfiguration(
                        2000L, 42L, "degraded", List.of("pv-gobi-01", "wind-ridge-02")
                )
        ));

        String savedContent = Files.readString(environmentFile, StandardCharsets.UTF_8);
        assertTrue(savedContent.contains("MQTT_HOST=10.0.0.12"));
        assertTrue(savedContent.contains("DEEPSEEK_API_KEY=secret-key-7890"));
        assertTrue(savedContent.contains("SIMULATION_STATION_IDS=pv-gobi-01,wind-ridge-02"));
        assertEquals("deepseek-chat", savedConfiguration.ai().model());
        assertEquals(2000L, savedConfiguration.simulation().intervalMillis());
    }

    private SystemConfigurationService createService(Path environmentFile) {
        Clock clock = Clock.fixed(Instant.parse("2026-08-28T02:30:00Z"), ZoneId.of("Asia/Shanghai"));
        return new SystemConfigurationService(new EnvironmentFileStore(environmentFile), clock);
    }

    private String environmentFileContent() {
        return """
                MQTT_HOST=127.0.0.1
                MQTT_PORT=1883
                EMQX_USERNAME=vpp_client
                EMQX_PASSWORD=mqtt-password
                BACKEND_MQTT_CLIENT_ID=vpp-backend-core
                BACKEND_MQTT_RECONNECT_DELAY_MS=3000
                VPP_AI_ENABLED=false
                VPP_AI_MAX_CONTEXT_CHARACTERS=6000
                DEEPSEEK_ENABLED=false
                DEEPSEEK_BASE_URL=https://api.deepseek.com
                DEEPSEEK_API_KEY=secret-key-7890
                DEEPSEEK_MODEL=deepseek-v4-flash
                DEEPSEEK_TIMEOUT_SECONDS=30
                SIMULATION_INTERVAL_MS=1000
                SIMULATION_RANDOM_SEED=20260629
                SIMULATION_INITIAL_SCENARIO=stable
                SIMULATION_STATION_IDS=
                KIMI_API_KEY=unrelated-key
                """;
    }
}

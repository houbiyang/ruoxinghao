package com.weaksignal.vpp.backend.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link EnvironmentFileStore} 测试。
 */
class EnvironmentFileStoreTest {

    @TempDir
    Path temporaryDirectory;

    /**
     * 验证更新时保留注释、未修改配置和等号后的完整内容。
     *
     * @throws Exception 文件操作异常
     */
    @Test
    void shouldUpdateOnlySpecifiedEnvironmentVariables() throws Exception {
        Path environmentFile = temporaryDirectory.resolve(".env");
        Files.writeString(
                environmentFile,
                "# 本地配置\nMQTT_HOST=127.0.0.1\nDEEPSEEK_API_KEY=old=key\nUNRELATED_VALUE=keep\n",
                StandardCharsets.UTF_8
        );
        EnvironmentFileStore store = new EnvironmentFileStore(environmentFile);
        Map<String, String> updates = new LinkedHashMap<>();
        updates.put("MQTT_HOST", "10.0.0.8");
        updates.put("DEEPSEEK_API_KEY", "new-key");
        updates.put("SIMULATION_INTERVAL_MS", "2000");

        store.updateValues(updates);

        String content = Files.readString(environmentFile, StandardCharsets.UTF_8);
        assertTrue(content.contains("# 本地配置"));
        assertTrue(content.contains("MQTT_HOST=10.0.0.8"));
        assertTrue(content.contains("DEEPSEEK_API_KEY=new-key"));
        assertTrue(content.contains("UNRELATED_VALUE=keep"));
        assertTrue(content.contains("SIMULATION_INTERVAL_MS=2000"));
        assertFalse(content.contains("DEEPSEEK_API_KEY=old=key"));
        assertEquals("new-key", store.readValues().get("DEEPSEEK_API_KEY"));
    }
}

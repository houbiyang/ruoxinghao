package com.weaksignal.vpp.backend.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weaksignal.vpp.backend.ai.ChatCompletionsUrlResolver;
import com.weaksignal.vpp.backend.config.AiProperties;
import com.weaksignal.vpp.backend.fault.AgentStructuredResponse;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 使用 Java 21 HttpClient 的 OpenAI Chat Completions 兼容 Provider。
 */
public class OpenAiCompatibleProvider implements AiProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenAiCompatibleProvider.class);

    private final String providerCode;
    private final boolean globallyEnabled;
    private final int maximumContextCharacters;
    private final AiProperties.ProviderProperties properties;
    private final ObjectMapper objectMapper;
    private final AgentResponseValidator validator;
    private final HttpClient httpClient;
    private final Clock clock;
    private volatile OffsetDateTime lastErrorAt;

    /**
     * 创建 OpenAI 兼容 Provider。
     *
     * @param providerCode Provider 编码
     * @param globallyEnabled 全局 AI 开关
     * @param maximumContextCharacters 最大上下文字符数
     * @param properties Provider 配置
     * @param objectMapper JSON 组件
     * @param validator 结构化响应校验器
     * @param clock 系统时钟
     */
    public OpenAiCompatibleProvider(
            String providerCode,
            boolean globallyEnabled,
            int maximumContextCharacters,
            AiProperties.ProviderProperties properties,
            ObjectMapper objectMapper,
            AgentResponseValidator validator,
            Clock clock
    ) {
        this.providerCode = providerCode;
        this.globallyEnabled = globallyEnabled;
        this.maximumContextCharacters = maximumContextCharacters;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.validator = validator;
        this.clock = clock;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(1, properties.timeoutSeconds())))
                .build();
    }

    @Override
    public Optional<AgentStructuredResponse> analyze(AgentRequest request) {
        if (!isAvailable()) {
            return Optional.empty();
        }
        try {
            String contextJson = objectMapper.writeValueAsString(request);
            if (contextJson.length() > maximumContextCharacters) {
                contextJson = contextJson.substring(0, maximumContextCharacters);
            }
            String systemPrompt = buildSystemPrompt(request);
            Map<String, Object> requestBody = Map.of(
                    "model", properties.model(),
                    "temperature", 0,
                    "response_format", Map.of("type", "json_object"),
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", contextJson)
                    )
            );
            HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(ChatCompletionsUrlResolver.resolve(properties.baseUrl())))
                    .timeout(Duration.ofSeconds(Math.max(1, properties.timeoutSeconds())))
                    .header("Authorization", "Bearer " + properties.apiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            objectMapper.writeValueAsString(requestBody),
                            StandardCharsets.UTF_8
                    ))
                    .build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                markFailure("HTTP 状态 " + response.statusCode());
                return Optional.empty();
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode contentNode = root.path("choices").path(0).path("message").path("content");
            if (!contentNode.isTextual()) {
                markFailure("响应缺少 choices[0].message.content");
                return Optional.empty();
            }
            AgentStructuredResponse structuredResponse = objectMapper.readValue(
                    contentNode.asText(),
                    AgentStructuredResponse.class
            );
            if (structuredResponse.agentType() != request.agentType() || !validator.isValid(structuredResponse)) {
                markFailure("结构化响应未通过白名单校验");
                return Optional.empty();
            }
            return Optional.of(structuredResponse);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            markFailure("请求被中断");
            return Optional.empty();
        } catch (Exception exception) {
            markFailure(exception.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    @Override
    public String providerCode() {
        return providerCode;
    }

    @Override
    public boolean isAvailable() {
        return isEnabled()
                && ChatCompletionsUrlResolver.isValid(properties.baseUrl())
                && hasText(properties.apiKey())
                && !"replace_me".equals(properties.apiKey())
                && hasText(properties.model());
    }

    @Override
    public boolean isEnabled() {
        return globallyEnabled && properties.enabled();
    }

    @Override
    public Optional<OffsetDateTime> lastErrorAt() {
        return Optional.ofNullable(lastErrorAt);
    }

    private void markFailure(String reason) {
        lastErrorAt = OffsetDateTime.now(clock);
        LOGGER.warn("AI Provider 调用失败，已切换规则型保守策略。provider={}, reason={}", providerCode, reason);
    }

    /**
     * 构建严格的结构化输出约束，避免模型自行扩展字段或枚举。
     *
     * @param request 智能体请求
     * @return 系统提示词
     */
    private String buildSystemPrompt(AgentRequest request) {
        return """
                你是虚拟电厂故障诊断逻辑组件，只输出一个 JSON 对象，不要使用 Markdown。
                必须严格使用以下结构，所有字段都必须存在：
                {
                  "agentType": "%s",
                  "provider": "%s",
                  "faultCode": "%s",
                  "faultType": "字符串",
                  "affectedStationIds": ["已存在的场站标识"],
                  "severity": "LOW|MEDIUM|HIGH|CRITICAL",
                  "confidence": 0.0,
                  "evidence": [{"metric":"字符串","value":"字符串","unit":"字符串","description":"字符串"}],
                  "recommendedMode": "central-optimization|edge-coordination|local-autonomy",
                  "proposedActions": [{
                    "actionType": "MARK_DATA_UNTRUSTED|FREEZE_EXPIRED_COMMANDS|REDUCE_TELEMETRY_RATE|LIMIT_CONTROL_RAMP|EXCLUDE_OFFLINE_CAPACITY|ENTER_LOCAL_AUTONOMY|ENABLE_CONSERVATIVE_ESTIMATE|START_RATE_LIMITED_RECOVERY|ISOLATE_STATION|SWITCH_CONTROL_AUTHORITY|REDISTRIBUTE_AGGREGATE_POWER|RESTORE_CENTRAL_CONTROL",
                    "stationId": "已存在的场站标识",
                    "parameters": {},
                    "reason": "字符串"
                  }],
                  "requiresHumanApproval": false,
                  "riskExplanation": "字符串"
                }
                agentType、provider 和 faultCode 必须分别保持为上面给定的固定值。
                confidence 必须在 0 到 1 之间；没有证据或动作时返回空数组。
                不得输出 MQTT Topic、URL、SQL、代码、思维过程或白名单外动作。
                """
                .formatted(
                        request.agentType().name(),
                        providerCode,
                        request.ruleDecision().faultCode()
                );
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

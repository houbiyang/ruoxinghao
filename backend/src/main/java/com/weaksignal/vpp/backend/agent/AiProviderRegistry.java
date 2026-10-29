package com.weaksignal.vpp.backend.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weaksignal.vpp.backend.config.AiProperties;
import com.weaksignal.vpp.backend.fault.AgentStructuredResponse;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * DeepSeek Provider 注册表。
 */
@Component
public class AiProviderRegistry {

    private final List<AiProvider> providers;

    /**
     * 根据环境配置创建 DeepSeek OpenAI 兼容 Provider。
     *
     * @param properties AI 配置
     * @param objectMapper JSON 组件
     * @param validator 响应校验器
     * @param clock 系统时钟
     */
    public AiProviderRegistry(
            AiProperties properties,
            ObjectMapper objectMapper,
            AgentResponseValidator validator,
            Clock clock
    ) {
        this.providers = List.of(
                new OpenAiCompatibleProvider(
                        "deepseek",
                        properties.enabled(),
                        properties.maximumContextCharacters(),
                        properties.deepseek(),
                        objectMapper,
                        validator,
                        clock
                )
        );
    }

    /**
     * 使用首选 Provider 执行一次分析；首选不可用时只尝试一个可用替代。
     *
     * @param preferredCode 首选 Provider
     * @param request 智能体请求
     * @return 结构化响应
     */
    public Optional<AgentStructuredResponse> analyzeOnce(String preferredCode, AgentRequest request) {
        return providers.stream()
                .filter(AiProvider::isAvailable)
                .sorted((left, right) -> Boolean.compare(
                        preferredCode.equals(right.providerCode()),
                        preferredCode.equals(left.providerCode())
                ))
                .findFirst()
                .flatMap(provider -> provider.analyze(request));
    }

    /**
     * 查询所有 Provider。
     *
     * @return Provider 只读列表
     */
    public List<AiProvider> providers() {
        return List.copyOf(providers);
    }
}

package com.weaksignal.vpp.backend.agent;

import com.weaksignal.vpp.backend.fault.AgentStructuredResponse;
import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * AI 分析 Provider 边界。
 */
public interface AiProvider {

    /**
     * 执行一次结构化分析，不进行重试。
     *
     * @param request 智能体请求
     * @return 通过解析的结构化响应
     */
    Optional<AgentStructuredResponse> analyze(AgentRequest request);

    /**
     * 获取 Provider 编码。
     *
     * @return Provider 编码
     */
    String providerCode();

    /**
     * 判断 Provider 是否被配置开关启用。
     *
     * @return 是否启用
     */
    boolean isEnabled();

    /**
     * 判断 Provider 是否配置完整且可调用。
     *
     * @return 是否可用
     */
    boolean isAvailable();

    /**
     * 获取最近错误时间。
     *
     * @return 最近错误时间
     */
    Optional<OffsetDateTime> lastErrorAt();
}

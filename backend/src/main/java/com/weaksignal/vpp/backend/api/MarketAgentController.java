package com.weaksignal.vpp.backend.api;

import com.weaksignal.vpp.backend.api.dto.MarketAgentAnalyzeRequest;
import com.weaksignal.vpp.backend.api.dto.MarketAgentRunResponse;
import com.weaksignal.vpp.backend.application.MarketAgentOrchestrationService;
import com.weaksignal.vpp.backend.marketagent.MarketAgentDecision;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 市场调度多智能体控制器。
 */
@RestController
@RequestMapping("/api/v1/agents")
public class MarketAgentController {

    private final MarketAgentOrchestrationService marketAgentOrchestrationService;
    private final Clock clock;

    /**
     * 注入市场调度多智能体控制器依赖。
     *
     * @param marketAgentOrchestrationService 市场调度多智能体服务
     * @param clock 系统时钟
     */
    public MarketAgentController(MarketAgentOrchestrationService marketAgentOrchestrationService, Clock clock) {
        this.marketAgentOrchestrationService = marketAgentOrchestrationService;
        this.clock = clock;
    }

    /**
     * 发起市场调度多智能体分析。
     *
     * @param request 分析请求
     * @return 运行结果
     */
    @PostMapping("/market-dispatch/analyze")
    public ApiResponse<MarketAgentRunResponse> analyze(@RequestBody(required = false) MarketAgentAnalyzeRequest request) {
        MarketAgentAnalyzeRequest effectiveRequest = request == null ? new MarketAgentAnalyzeRequest(null, null, false) : request;
        return ApiResponse.success(marketAgentOrchestrationService.analyze(effectiveRequest), OffsetDateTime.now(clock));
    }

    /**
     * 查询市场调度智能体运行。
     *
     * @param runId 运行标识
     * @return 运行结果
     */
    @GetMapping("/runs/{runId}")
    public ApiResponse<MarketAgentRunResponse> run(@PathVariable String runId) {
        return ApiResponse.success(marketAgentOrchestrationService.getRun(runId), OffsetDateTime.now(clock));
    }

    /**
     * 查询最近一次完整的市场调度智能体运行。
     *
     * <p>没有可用运行记录时返回 {@code null}，调用方不得以默认值替代。</p>
     *
     * @return 最近一次市场调度智能体运行
     */
    @GetMapping("/market-dispatch/latest")
    public ApiResponse<MarketAgentRunResponse> latestRun() {
        return ApiResponse.success(marketAgentOrchestrationService.getLatestRun(), OffsetDateTime.now(clock));
    }

    /**
     * 查询市场调度智能体决策列表。
     *
     * @param runId 运行标识
     * @return 决策列表
     */
    @GetMapping("/runs/{runId}/decisions")
    public ApiResponse<List<MarketAgentDecision>> decisions(@PathVariable String runId) {
        return ApiResponse.success(marketAgentOrchestrationService.getDecisions(runId), OffsetDateTime.now(clock));
    }

    /**
     * 查询 DeepSeek 状态。
     *
     * @return Provider 状态
     */
    @GetMapping("/deepseek/status")
    public ApiResponse<Map<String, Object>> deepSeekStatus() {
        return ApiResponse.success(marketAgentOrchestrationService.getDeepSeekStatus(), OffsetDateTime.now(clock));
    }
}

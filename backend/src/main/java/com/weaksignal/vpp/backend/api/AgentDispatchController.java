package com.weaksignal.vpp.backend.api;

import com.weaksignal.vpp.backend.api.dto.AgentDispatchOverviewResponse;
import com.weaksignal.vpp.backend.api.dto.ExperimentStartResponse;
import com.weaksignal.vpp.backend.api.dto.ExperimentStopResponse;
import com.weaksignal.vpp.backend.application.AgentDispatchOverviewService;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 多智能体调度中心聚合接口控制器。
 */
@RestController
@RequestMapping("/api/v1/agent-dispatch")
public class AgentDispatchController {

    private final AgentDispatchOverviewService agentDispatchOverviewService;
    private final Clock clock;

    /**
     * 注入多智能体调度中心控制器依赖。
     *
     * @param agentDispatchOverviewService 聚合服务
     * @param clock 系统时钟
     */
    public AgentDispatchController(AgentDispatchOverviewService agentDispatchOverviewService, Clock clock) {
        this.agentDispatchOverviewService = agentDispatchOverviewService;
        this.clock = clock;
    }

    /**
     * 查询多智能体调度中心总览。
     *
     * @return 聚合总览
     */
    @GetMapping("/overview")
    public ApiResponse<AgentDispatchOverviewResponse> overview() {
        return ApiResponse.success(agentDispatchOverviewService.getOverview(), OffsetDateTime.now(clock));
    }

    /**
     * 代理启动实验。
     *
     * @param scenarioCode 场景编码
     * @return 启动结果
     */
    @PostMapping("/experiments/{scenarioCode}/start")
    public ApiResponse<ExperimentStartResponse> startExperiment(@PathVariable String scenarioCode) {
        return ApiResponse.success(agentDispatchOverviewService.startExperiment(scenarioCode), OffsetDateTime.now(clock));
    }

    /**
     * 代理停止当前实验。
     *
     * @return 停止结果
     */
    @PostMapping("/experiments/current/stop")
    public ApiResponse<ExperimentStopResponse> stopCurrentExperiment() {
        return ApiResponse.success(agentDispatchOverviewService.stopCurrentExperiment(), OffsetDateTime.now(clock));
    }
}

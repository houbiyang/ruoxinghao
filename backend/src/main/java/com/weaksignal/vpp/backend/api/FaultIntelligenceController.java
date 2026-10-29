package com.weaksignal.vpp.backend.api;

import com.weaksignal.vpp.backend.api.dto.AiProviderStatusResponse;
import com.weaksignal.vpp.backend.api.dto.FaultActionResponse;
import com.weaksignal.vpp.backend.api.dto.FaultAnalysisRequest;
import com.weaksignal.vpp.backend.api.dto.FaultReviewRequest;
import com.weaksignal.vpp.backend.api.dto.FaultRunResponse;
import com.weaksignal.vpp.backend.application.FaultIntelligenceService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 多智能体故障诊断与处置接口。
 */
@Validated
@RestController
@RequestMapping("/api/v1/fault-intelligence")
public class FaultIntelligenceController {

    private final FaultIntelligenceService service;
    private final Clock clock;

    /**
     * 注入故障智能服务。
     *
     * @param service 故障智能服务
     * @param clock 系统时钟
     */
    public FaultIntelligenceController(FaultIntelligenceService service, Clock clock) {
        this.service = service;
        this.clock = clock;
    }

    /**
     * 发起多智能体诊断。
     *
     * @param request 分析请求
     * @return 诊断运行
     */
    @PostMapping("/analyze")
    public ApiResponse<FaultRunResponse> analyze(@Valid @RequestBody FaultAnalysisRequest request) {
        return ApiResponse.success(service.analyze(request), OffsetDateTime.now(clock));
    }

    /**
     * 查询最近诊断。
     *
     * @param limit 查询条数
     * @return 诊断列表
     */
    @GetMapping("/runs/latest")
    public ApiResponse<List<FaultRunResponse>> latest(
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit
    ) {
        return ApiResponse.success(service.getLatestRuns(limit), OffsetDateTime.now(clock));
    }

    /**
     * 查询诊断详情。
     *
     * @param runId 运行标识
     * @return 诊断详情
     */
    @GetMapping("/runs/{runId}")
    public ApiResponse<FaultRunResponse> detail(@PathVariable String runId) {
        return ApiResponse.success(service.getRun(runId), OffsetDateTime.now(clock));
    }

    /**
     * 查询最终动作计划。
     *
     * @param runId 运行标识
     * @return 动作计划
     */
    @GetMapping("/runs/{runId}/plan")
    public ApiResponse<List<FaultActionResponse>> plan(@PathVariable String runId) {
        return ApiResponse.success(service.getPlan(runId), OffsetDateTime.now(clock));
    }

    /**
     * 兼容旧版复核通过入口。
     *
     * @param runId 运行标识
     * @param request 复核请求
     * @return 更新后的诊断
     */
    @PostMapping("/runs/{runId}/approve")
    public ApiResponse<FaultRunResponse> approve(
            @PathVariable String runId,
            @Valid @RequestBody FaultReviewRequest request
    ) {
        return ApiResponse.success(service.approve(runId, request.reason()), OffsetDateTime.now(clock));
    }

    /**
     * 兼容旧版复核拒绝入口。
     *
     * @param runId 运行标识
     * @param request 复核请求
     * @return 更新后的诊断
     */
    @PostMapping("/runs/{runId}/reject")
    public ApiResponse<FaultRunResponse> reject(
            @PathVariable String runId,
            @Valid @RequestBody FaultReviewRequest request
    ) {
        return ApiResponse.success(service.reject(runId, request.reason()), OffsetDateTime.now(clock));
    }

    /**
     * 执行安全门已自动放行的方案。
     *
     * @param runId 运行标识
     * @return 更新后的诊断
     */
    @PostMapping("/runs/{runId}/execute")
    public ApiResponse<FaultRunResponse> execute(@PathVariable String runId) {
        return ApiResponse.success(service.execute(runId), OffsetDateTime.now(clock));
    }

    /**
     * 查询脱敏 Provider 状态。
     *
     * @return Provider 状态
     */
    @GetMapping("/providers/status")
    public ApiResponse<List<AiProviderStatusResponse>> providers() {
        return ApiResponse.success(service.getProviderStatuses(), OffsetDateTime.now(clock));
    }
}

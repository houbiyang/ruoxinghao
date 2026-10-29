package com.weaksignal.vpp.backend.api;

import com.weaksignal.vpp.backend.api.dto.DispatchPlanCreateRequest;
import com.weaksignal.vpp.backend.api.dto.DispatchPlanAuditChainResponse;
import com.weaksignal.vpp.backend.api.dto.DispatchPlanExecutionResponse;
import com.weaksignal.vpp.backend.api.dto.DispatchPlanResponse;
import com.weaksignal.vpp.backend.api.dto.DispatchPlanTrackingResponse;
import com.weaksignal.vpp.backend.application.MarketDispatchPlanningService;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 市场调度计划控制器。
 */
@RestController
@RequestMapping("/api/v1/market-dispatch/plans")
public class MarketDispatchController {

    private final MarketDispatchPlanningService marketDispatchPlanningService;
    private final Clock clock;

    /**
     * 注入市场调度控制器依赖。
     *
     * @param marketDispatchPlanningService 市场调度计划服务
     * @param clock 系统时钟
     */
    public MarketDispatchController(MarketDispatchPlanningService marketDispatchPlanningService, Clock clock) {
        this.marketDispatchPlanningService = marketDispatchPlanningService;
        this.clock = clock;
    }

    /**
     * 预览调度计划。
     *
     * @param request 计划请求
     * @return 调度计划
     */
    @PostMapping("/preview")
    public ApiResponse<DispatchPlanResponse> preview(@Valid @RequestBody(required = false) DispatchPlanCreateRequest request) {
        DispatchPlanCreateRequest effectiveRequest = request == null ? new DispatchPlanCreateRequest(null, false) : request;
        return ApiResponse.success(marketDispatchPlanningService.preview(effectiveRequest), OffsetDateTime.now(clock));
    }

    /**
     * 创建调度计划。
     *
     * @param request 计划请求
     * @return 调度计划
     */
    @PostMapping
    public ApiResponse<DispatchPlanResponse> create(@Valid @RequestBody(required = false) DispatchPlanCreateRequest request) {
        DispatchPlanCreateRequest effectiveRequest = request == null ? new DispatchPlanCreateRequest(null, false) : request;
        return ApiResponse.success(marketDispatchPlanningService.create(effectiveRequest), OffsetDateTime.now(clock));
    }

    /**
     * 查询指定计划。
     *
     * @param planId 计划标识
     * @return 调度计划
     */
    @GetMapping("/{planId}")
    public ApiResponse<DispatchPlanResponse> getPlan(@PathVariable String planId) {
        return ApiResponse.success(marketDispatchPlanningService.getPlan(planId), OffsetDateTime.now(clock));
    }

    /**
     * 查询最新计划。
     *
     * @return 调度计划
     */
    @GetMapping("/latest")
    public ApiResponse<DispatchPlanResponse> latest() {
        return ApiResponse.success(marketDispatchPlanningService.getLatestPlan(), OffsetDateTime.now(clock));
    }

    /**
     * 校验计划。
     *
     * @param planId 计划标识
     * @return 调度计划
     */
    @PostMapping("/{planId}/validate")
    public ApiResponse<DispatchPlanResponse> validate(@PathVariable String planId) {
        return ApiResponse.success(marketDispatchPlanningService.validate(planId), OffsetDateTime.now(clock));
    }

    /**
     * 自动放行计划。
     *
     * @param planId 计划标识
     * @return 调度计划
     */
    @PostMapping("/{planId}/approve")
    public ApiResponse<DispatchPlanResponse> approve(@PathVariable String planId) {
        return ApiResponse.success(marketDispatchPlanningService.approve(planId), OffsetDateTime.now(clock));
    }

    /**
     * 拒绝计划。
     *
     * @param planId 计划标识
     * @return 调度计划
     */
    @PostMapping("/{planId}/reject")
    public ApiResponse<DispatchPlanResponse> reject(@PathVariable String planId) {
        return ApiResponse.success(marketDispatchPlanningService.reject(planId), OffsetDateTime.now(clock));
    }

    /**
     * 激活计划。
     *
     * @param planId 计划标识
     * @return 调度计划
     */
    @PostMapping("/{planId}/activate")
    public ApiResponse<DispatchPlanResponse> activate(@PathVariable String planId) {
        return ApiResponse.success(marketDispatchPlanningService.activate(planId), OffsetDateTime.now(clock));
    }

    /**
     * 执行下一个时间片。
     *
     * @param planId 计划标识
     * @return 执行概览
     */
    @PostMapping("/{planId}/execute-next")
    public ApiResponse<DispatchPlanExecutionResponse> executeNext(@PathVariable String planId) {
        return ApiResponse.success(marketDispatchPlanningService.executeNext(planId), OffsetDateTime.now(clock));
    }

    /**
     * 废弃计划。
     *
     * @param planId 计划标识
     * @return 调度计划
     */
    @PostMapping("/{planId}/supersede")
    public ApiResponse<DispatchPlanResponse> supersede(@PathVariable String planId) {
        return ApiResponse.success(marketDispatchPlanningService.supersede(planId), OffsetDateTime.now(clock));
    }

    /**
     * 查询计划执行概览。
     *
     * @param planId 计划标识
     * @return 执行概览
     */
    @GetMapping("/{planId}/execution")
    public ApiResponse<DispatchPlanExecutionResponse> execution(@PathVariable String planId) {
        return ApiResponse.success(marketDispatchPlanningService.getExecution(planId), OffsetDateTime.now(clock));
    }

    /**
     * 查询计划跟踪结果。
     *
     * @param planId 计划标识
     * @return 跟踪结果
     */
    @GetMapping("/{planId}/tracking")
    public ApiResponse<DispatchPlanTrackingResponse> tracking(@PathVariable String planId) {
        return ApiResponse.success(marketDispatchPlanningService.getTracking(planId), OffsetDateTime.now(clock));
    }

    /**
     * 查询计划审计链。
     *
     * @param planId 计划标识
     * @return 审计链
     */
    @GetMapping("/{planId}/audit-chain")
    public ApiResponse<DispatchPlanAuditChainResponse> auditChain(@PathVariable String planId) {
        return ApiResponse.success(marketDispatchPlanningService.getAuditChain(planId), OffsetDateTime.now(clock));
    }
}

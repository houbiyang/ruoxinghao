package com.weaksignal.vpp.backend.api;

import com.weaksignal.vpp.backend.api.dto.DispatchCommandResponse;
import com.weaksignal.vpp.backend.api.dto.DispatchExecuteResponse;
import com.weaksignal.vpp.backend.api.dto.DispatchPlanPointResponse;
import com.weaksignal.vpp.backend.api.dto.DispatchPreviewResponse;
import com.weaksignal.vpp.backend.api.dto.DispatchRequest;
import com.weaksignal.vpp.backend.api.dto.DispatchStatusResponse;
import com.weaksignal.vpp.backend.application.DispatchService;
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
 * 规则型调度与控制接口。
 */
@Validated
@RestController
@RequestMapping("/api/v1/dispatch")
public class DispatchController {

    private final DispatchService dispatchService;
    private final Clock clock;

    /**
     * 注入调度接口依赖。
     *
     * @param dispatchService 调度应用服务
     * @param clock 系统时钟
     */
    public DispatchController(DispatchService dispatchService, Clock clock) {
        this.dispatchService = dispatchService;
        this.clock = clock;
    }

    /**
     * 查询未来 24 小时日内计划。
     *
     * @return 日内计划
     */
    @GetMapping("/plan")
    public ApiResponse<List<DispatchPlanPointResponse>> plan() {
        return ApiResponse.success(dispatchService.generatePlan(), OffsetDateTime.now(clock));
    }

    /**
     * 预览调度分配。
     *
     * @param request 调度请求
     * @return 分配预览
     */
    @PostMapping("/preview")
    public ApiResponse<DispatchPreviewResponse> preview(@Valid @RequestBody DispatchRequest request) {
        return ApiResponse.success(dispatchService.preview(request), OffsetDateTime.now(clock));
    }

    /**
     * 下发调度指令。
     *
     * @param request 调度请求
     * @return 执行结果
     */
    @PostMapping("/execute")
    public ApiResponse<DispatchExecuteResponse> execute(@Valid @RequestBody DispatchRequest request) {
        return ApiResponse.success(dispatchService.execute(request), OffsetDateTime.now(clock));
    }

    /**
     * 查询最近调度指令。
     *
     * @param limit 查询条数
     * @return 指令列表
     */
    @GetMapping("/commands/latest")
    public ApiResponse<List<DispatchCommandResponse>> latestCommands(
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit
    ) {
        return ApiResponse.success(dispatchService.getLatestCommands(limit), OffsetDateTime.now(clock));
    }

    /**
     * 查询单条调度指令。
     *
     * @param commandId 指令标识
     * @return 指令详情
     */
    @GetMapping("/commands/{commandId}")
    public ApiResponse<DispatchCommandResponse> command(@PathVariable String commandId) {
        return ApiResponse.success(dispatchService.getCommand(commandId), OffsetDateTime.now(clock));
    }

    /**
     * 查询调度执行状态。
     *
     * @return 执行状态
     */
    @GetMapping("/status")
    public ApiResponse<DispatchStatusResponse> status() {
        return ApiResponse.success(dispatchService.getStatus(), OffsetDateTime.now(clock));
    }
}

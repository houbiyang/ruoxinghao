package com.weaksignal.vpp.backend.api;

import com.weaksignal.vpp.backend.api.dto.ExperimentComparisonResponse;
import com.weaksignal.vpp.backend.api.dto.ExperimentDeleteResponse;
import com.weaksignal.vpp.backend.api.dto.ExperimentQualityMetricsResponse;
import com.weaksignal.vpp.backend.api.dto.ExperimentReplayPointResponse;
import com.weaksignal.vpp.backend.api.dto.ExperimentRunListItemResponse;
import com.weaksignal.vpp.backend.api.dto.ExperimentRunResponse;
import com.weaksignal.vpp.backend.api.dto.ExperimentScenarioResponse;
import com.weaksignal.vpp.backend.api.dto.ExperimentStartRequest;
import com.weaksignal.vpp.backend.api.dto.ExperimentStartResponse;
import com.weaksignal.vpp.backend.api.dto.ExperimentStopResponse;
import com.weaksignal.vpp.backend.api.dto.ExperimentSummaryResponse;
import com.weaksignal.vpp.backend.api.dto.ExperimentTimelinePointResponse;
import com.weaksignal.vpp.backend.application.ExperimentAnalysisService;
import com.weaksignal.vpp.backend.application.ExperimentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 故障注入实验控制器。
 */
@Validated
@RestController
@RequestMapping("/api/v1/experiments")
public class ExperimentController {

    private final ExperimentService experimentService;
    private final ExperimentAnalysisService experimentAnalysisService;
    private final Clock clock;

    /**
     * 注入实验接口依赖。
     *
     * @param experimentService 实验服务
     * @param experimentAnalysisService 实验分析服务
     * @param clock 系统时钟
     */
    public ExperimentController(
            ExperimentService experimentService,
            ExperimentAnalysisService experimentAnalysisService,
            Clock clock
    ) {
        this.experimentService = experimentService;
        this.experimentAnalysisService = experimentAnalysisService;
        this.clock = clock;
    }

    /**
     * 查询实验场景清单。
     *
     * @return 实验场景清单
     */
    @GetMapping("/scenarios")
    public ApiResponse<List<ExperimentScenarioResponse>> scenarios() {
        return ApiResponse.success(experimentService.listScenarios(), OffsetDateTime.now(clock));
    }

    /**
     * 查询最近实验运行。
     *
     * @param limit 返回数量
     * @return 实验运行列表
     */
    @GetMapping("/runs/latest")
    public ApiResponse<List<ExperimentRunResponse>> latestRuns(@RequestParam(defaultValue = "10") @Min(1) @Max(200) int limit) {
        return ApiResponse.success(experimentService.listLatestRuns(limit), OffsetDateTime.now(clock));
    }

    /**
     * 查询最近实验运行的轻量列表。
     *
     * @param limit 返回数量
     * @return 不含结果摘要的实验运行列表
     */
    @GetMapping("/runs/latest/items")
    public ApiResponse<List<ExperimentRunListItemResponse>> latestRunItems(
            @RequestParam(defaultValue = "10") @Min(1) @Max(200) int limit
    ) {
        return ApiResponse.success(experimentService.listLatestRunItems(limit), OffsetDateTime.now(clock));
    }

    /**
     * 查询当前运行实验。
     *
     * @return 当前运行实验
     */
    @GetMapping("/runs/current")
    public ApiResponse<ExperimentRunResponse> currentRun() {
        return ApiResponse.success(experimentService.getCurrentRun(), OffsetDateTime.now(clock));
    }

    /**
     * 按运行标识查询实验。
     *
     * @param runId 实验运行标识
     * @return 实验运行记录
     */
    @GetMapping("/runs/{runId}")
    public ApiResponse<ExperimentRunResponse> run(@PathVariable String runId) {
        return ApiResponse.success(experimentService.getRun(runId), OffsetDateTime.now(clock));
    }

    /**
     * 启动实验。
     *
     * @param scenarioCode 场景编码
     * @param request 启动请求
     * @return 启动结果
     */
    @PostMapping("/scenarios/{scenarioCode}/start")
    public ApiResponse<ExperimentStartResponse> start(
            @PathVariable String scenarioCode,
            @Valid @RequestBody(required = false) ExperimentStartRequest request
    ) {
        ExperimentStartRequest startRequest = request == null
                ? new ExperimentStartRequest(null, null, null, null, null, null)
                : request;
        return ApiResponse.success(experimentService.startExperiment(scenarioCode, startRequest), OffsetDateTime.now(clock));
    }

    /**
     * 停止实验。
     *
     * @param runId 运行标识
     * @return 停止结果
     */
    @PostMapping("/runs/{runId}/stop")
    public ApiResponse<ExperimentStopResponse> stop(@PathVariable String runId) {
        return ApiResponse.success(experimentService.stopExperiment(runId), OffsetDateTime.now(clock));
    }

    /**
     * 删除已停止的实验运行记录。
     *
     * @param runId 实验运行标识
     * @return 删除结果
     */
    @DeleteMapping("/runs/{runId}")
    public ApiResponse<ExperimentDeleteResponse> delete(@PathVariable String runId) {
        return ApiResponse.success(experimentService.deleteExperiment(runId), OffsetDateTime.now(clock));
    }

    /**
     * 查询单次实验摘要。
     *
     * @param runId 运行标识
     * @return 实验摘要
     */
    @GetMapping("/runs/{runId}/summary")
    public ApiResponse<ExperimentSummaryResponse> summary(@PathVariable String runId) {
        return ApiResponse.success(experimentAnalysisService.getSummary(runId), OffsetDateTime.now(clock));
    }

    /**
     * 查询实验前后指标对比。
     *
     * @param runId 运行标识
     * @return 实验前后指标对比
     */
    @GetMapping("/runs/{runId}/comparison")
    public ApiResponse<ExperimentComparisonResponse> comparison(@PathVariable String runId) {
        return ApiResponse.success(experimentAnalysisService.getComparison(runId), OffsetDateTime.now(clock));
    }

    /**
     * 查询实验链路状态时间线。
     *
     * @param runId 运行标识
     * @return 链路状态时间线
     */
    @GetMapping("/runs/{runId}/timeline")
    public ApiResponse<List<ExperimentTimelinePointResponse>> timeline(@PathVariable String runId) {
        return ApiResponse.success(experimentAnalysisService.getTimeline(runId), OffsetDateTime.now(clock));
    }

    /**
     * 查询实验遥测回放数据。
     *
     * @param runId 运行标识
     * @param stationId 场站标识
     * @param allStations 是否查询全系统场站
     * @param includeBaseline 是否包含实验开始前的基线数据
     * @param from 起始时间
     * @param to 截止时间
     * @param limit 返回数量
     * @return 遥测回放点
     */
    @GetMapping("/runs/{runId}/replay")
    public ApiResponse<List<ExperimentReplayPointResponse>> replay(
            @PathVariable String runId,
            @RequestParam(required = false) String stationId,
            @RequestParam(defaultValue = "false") boolean allStations,
            @RequestParam(defaultValue = "false") boolean includeBaseline,
            @RequestParam(required = false) OffsetDateTime from,
            @RequestParam(required = false) OffsetDateTime to,
            @RequestParam(defaultValue = "300") @Min(1) @Max(1000) int limit
    ) {
        return ApiResponse.success(
                experimentAnalysisService.getReplay(runId, stationId, allStations, includeBaseline, from, to, limit),
                OffsetDateTime.now(clock)
        );
    }

    /**
     * 查询实验通信质量指标。
     *
     * @param runId 运行标识
     * @return 通信质量指标
     */
    @GetMapping("/runs/{runId}/quality-metrics")
    public ApiResponse<ExperimentQualityMetricsResponse> qualityMetrics(@PathVariable String runId) {
        return ApiResponse.success(experimentAnalysisService.getQualityMetrics(runId), OffsetDateTime.now(clock));
    }
}

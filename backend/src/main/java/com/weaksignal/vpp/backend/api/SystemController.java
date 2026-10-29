package com.weaksignal.vpp.backend.api;

import com.weaksignal.vpp.backend.api.dto.DashboardSummaryResponse;
import com.weaksignal.vpp.backend.api.dto.EventResponse;
import com.weaksignal.vpp.backend.api.dto.ExperimentReplayPointResponse;
import com.weaksignal.vpp.backend.api.dto.PowerTrendPointResponse;
import com.weaksignal.vpp.backend.api.dto.PowerFlowTrendPointResponse;
import com.weaksignal.vpp.backend.api.dto.ScenarioActivationResponse;
import com.weaksignal.vpp.backend.api.dto.ScenarioStatusResponse;
import com.weaksignal.vpp.backend.api.dto.StationDeviceDetailResponse;
import com.weaksignal.vpp.backend.api.dto.StationDevicePowerTrendResponse;
import com.weaksignal.vpp.backend.api.dto.StationDeviceQualityTrendPointResponse;
import com.weaksignal.vpp.backend.api.dto.StationDeviceSnapshotResponse;
import com.weaksignal.vpp.backend.api.dto.StationDetailResponse;
import com.weaksignal.vpp.backend.api.dto.StationStateResponse;
import com.weaksignal.vpp.backend.application.DashboardQueryService;
import com.weaksignal.vpp.backend.application.ScenarioService;
import com.weaksignal.vpp.backend.application.StationDeviceQueryService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统接口控制器。
 */
@Validated
@RestController
@RequestMapping("/api/v1")
public class SystemController {

    private final DashboardQueryService dashboardQueryService;
    private final StationDeviceQueryService stationDeviceQueryService;
    private final ScenarioService scenarioService;
    private final Clock clock;

    /**
     * 注入系统接口依赖。
     *
     * @param dashboardQueryService 查询服务
     * @param stationDeviceQueryService 子设备查询服务
     * @param scenarioService 场景服务
     * @param clock 系统时钟
     */
    public SystemController(
            DashboardQueryService dashboardQueryService,
            StationDeviceQueryService stationDeviceQueryService,
            ScenarioService scenarioService,
            Clock clock
    ) {
        this.dashboardQueryService = dashboardQueryService;
        this.stationDeviceQueryService = stationDeviceQueryService;
        this.scenarioService = scenarioService;
        this.clock = clock;
    }

    /**
     * 健康检查。
     *
     * @return 健康状态
     */
    @GetMapping("/health")
    public ApiResponse<Map<String, String>> health() {
        return ApiResponse.success(Map.of("status", "UP"), OffsetDateTime.now(clock));
    }

    /**
     * 查询场站列表。
     *
     * @return 场站列表
     */
    @GetMapping("/stations")
    public ApiResponse<List<StationStateResponse>> stations() {
        return ApiResponse.success(dashboardQueryService.getStations(), OffsetDateTime.now(clock));
    }

    /**
     * 查询场站详情。
     *
     * @param stationId 场站标识
     * @return 场站详情
     */
    @GetMapping("/stations/{stationId}")
    public ApiResponse<StationDetailResponse> stationDetail(@PathVariable String stationId) {
        return ApiResponse.success(dashboardQueryService.getStationDetail(stationId), OffsetDateTime.now(clock));
    }

    /**
     * 查询场站子设备列表。
     *
     * @param stationId 场站标识
     * @return 子设备列表
     */
    @GetMapping("/stations/{stationId}/devices")
    public ApiResponse<List<StationDeviceSnapshotResponse>> stationDevices(@PathVariable String stationId) {
        return ApiResponse.success(stationDeviceQueryService.getDevices(stationId), OffsetDateTime.now(clock));
    }

    /**
     * 查询场站子设备详情。
     *
     * @param stationId 场站标识
     * @param deviceId 设备标识
     * @return 子设备详情
     */
    @GetMapping("/stations/{stationId}/devices/{deviceId}")
    public ApiResponse<StationDeviceDetailResponse> stationDeviceDetail(
            @PathVariable String stationId,
            @PathVariable String deviceId
    ) {
        return ApiResponse.success(stationDeviceQueryService.getDeviceDetail(stationId, deviceId), OffsetDateTime.now(clock));
    }

    /**
     * 查询子设备功率或环境趋势。
     *
     * @param stationId 场站标识
     * @param deviceId 设备标识
     * @param from 起始时间
     * @param to 截止时间
     * @param limit 限制数量
     * @return 趋势结果
     */
    @GetMapping("/stations/{stationId}/devices/{deviceId}/power-trend")
    public ApiResponse<StationDevicePowerTrendResponse> stationDevicePowerTrend(
            @PathVariable String stationId,
            @PathVariable String deviceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(defaultValue = "120") @Min(1) @Max(240) int limit
    ) {
        OffsetDateTime queryTo = to == null ? OffsetDateTime.now(clock) : to;
        OffsetDateTime queryFrom = from == null ? queryTo.minusHours(2) : from;
        return ApiResponse.success(
                stationDeviceQueryService.getPowerTrend(stationId, deviceId, queryFrom, queryTo, limit),
                OffsetDateTime.now(clock)
        );
    }

    /**
     * 查询子设备链路质量趋势。
     *
     * @param stationId 场站标识
     * @param deviceId 设备标识
     * @param from 起始时间
     * @param to 截止时间
     * @param limit 限制数量
     * @return 链路质量趋势
     */
    @GetMapping("/stations/{stationId}/devices/{deviceId}/quality-trend")
    public ApiResponse<List<StationDeviceQualityTrendPointResponse>> stationDeviceQualityTrend(
            @PathVariable String stationId,
            @PathVariable String deviceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(defaultValue = "120") @Min(1) @Max(240) int limit
    ) {
        OffsetDateTime queryTo = to == null ? OffsetDateTime.now(clock) : to;
        OffsetDateTime queryFrom = from == null ? queryTo.minusHours(2) : from;
        return ApiResponse.success(
                stationDeviceQueryService.getQualityTrend(stationId, deviceId, queryFrom, queryTo, limit),
                OffsetDateTime.now(clock)
        );
    }

    /**
     * 查询总览摘要。
     *
     * @return 总览摘要
     */
    @GetMapping("/dashboard/summary")
    public ApiResponse<DashboardSummaryResponse> dashboardSummary() {
        return ApiResponse.success(dashboardQueryService.getDashboardSummary(), OffsetDateTime.now(clock));
    }

    /**
     * 查询功率趋势。
     *
     * @param from 起始时间
     * @param to 截止时间
     * @param limit 限制数量
     * @return 趋势点列表
     */
    @GetMapping("/dashboard/power-trend")
    public ApiResponse<List<PowerTrendPointResponse>> powerTrend(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(defaultValue = "24") @Min(1) @Max(240) int limit
    ) {
        OffsetDateTime queryTo = to == null ? OffsetDateTime.now(clock) : to;
        OffsetDateTime queryFrom = from == null ? queryTo.minusHours(1) : from;
        return ApiResponse.success(dashboardQueryService.getPowerTrend(queryFrom, queryTo, limit), OffsetDateTime.now(clock));
    }

    /**
     * 查询模拟器逐场站实时功率遥测。
     *
     * @param from 起始时间
     * @param to 截止时间
     * @param limit 最大返回数量
     * @param bucketSeconds 按场站聚合的时间窗秒数，0 表示不聚合
     * @return 连续的真实功率遥测点
     */
    @GetMapping("/dashboard/realtime-power-telemetry")
    public ApiResponse<List<ExperimentReplayPointResponse>> realtimePowerTelemetry(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(defaultValue = "10000") @Min(1) @Max(20000) int limit,
            @RequestParam(defaultValue = "0") @Min(0) @Max(300) int bucketSeconds
    ) {
        OffsetDateTime queryTo = to == null ? OffsetDateTime.now(clock) : to;
        OffsetDateTime queryFrom = from == null ? queryTo.minusMinutes(20) : from;
        return ApiResponse.success(
                dashboardQueryService.getRealtimePowerTelemetry(queryFrom, queryTo, limit, bucketSeconds),
                OffsetDateTime.now(clock)
        );
    }

    /**
     * 查询模拟器发电与用电功率流趋势。
     *
     * @param from 起始时间
     * @param to 截止时间
     * @param limit 限制数量
     * @return 功率流趋势点列表
     */
    @GetMapping("/dashboard/power-flow-trend")
    public ApiResponse<List<PowerFlowTrendPointResponse>> powerFlowTrend(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(defaultValue = "240") @Min(1) @Max(240) int limit
    ) {
        OffsetDateTime queryTo = to == null ? OffsetDateTime.now(clock) : to;
        OffsetDateTime queryFrom = from == null ? queryTo.minusHours(2) : from;
        return ApiResponse.success(dashboardQueryService.getPowerFlowTrend(queryFrom, queryTo, limit), OffsetDateTime.now(clock));
    }

    /**
     * 查询最近事件。
     *
     * @param limit 限制数量
     * @return 最近事件
     */
    @GetMapping("/events/latest")
    public ApiResponse<List<EventResponse>> latestEvents(@RequestParam(defaultValue = "6") @Min(1) @Max(20) int limit) {
        return ApiResponse.success(dashboardQueryService.getLatestEvents(limit), OffsetDateTime.now(clock));
    }

    /**
     * 查询当前场景。
     *
     * @return 当前场景
     */
    @GetMapping("/scenarios/current")
    public ApiResponse<ScenarioStatusResponse> currentScenario() {
        return ApiResponse.success(scenarioService.getCurrentScenario(), OffsetDateTime.now(clock));
    }

    /**
     * 激活指定场景。
     *
     * @param scenario 场景编码
     * @return 激活结果
     */
    @PostMapping("/scenarios/{scenario}/activate")
    public ApiResponse<ScenarioActivationResponse> activateScenario(@PathVariable String scenario) {
        return ApiResponse.success(scenarioService.activateScenario(scenario), OffsetDateTime.now(clock));
    }
}

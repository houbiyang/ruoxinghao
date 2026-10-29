package com.weaksignal.vpp.backend.api;

import com.weaksignal.vpp.backend.api.dto.DispatchContextSnapshotResponse;
import com.weaksignal.vpp.backend.api.dto.ForecastSnapshotCreateRequest;
import com.weaksignal.vpp.backend.api.dto.ForecastSnapshotResponse;
import com.weaksignal.vpp.backend.api.dto.MarketSignalCreateRequest;
import com.weaksignal.vpp.backend.api.dto.MarketSignalResponse;
import com.weaksignal.vpp.backend.api.dto.ResourceCapabilityResponse;
import com.weaksignal.vpp.backend.api.dto.SimulatorMarketDataRefreshResponse;
import com.weaksignal.vpp.backend.capability.ResourceCapabilityService;
import com.weaksignal.vpp.backend.forecast.ForecastService;
import com.weaksignal.vpp.backend.market.MarketSignalService;
import com.weaksignal.vpp.backend.market.SimulatorMarketDataService;
import com.weaksignal.vpp.backend.planning.DispatchContextSnapshotService;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 市场调度基础接口控制器。
 */
@Validated
@RestController
@RequestMapping("/api/v1")
public class MarketPlanningController {

    private final MarketSignalService marketSignalService;
    private final ForecastService forecastService;
    private final ResourceCapabilityService resourceCapabilityService;
    private final DispatchContextSnapshotService dispatchContextSnapshotService;
    private final SimulatorMarketDataService simulatorMarketDataService;
    private final Clock clock;

    /**
     * 注入市场调度基础依赖。
     *
     * @param marketSignalService 市场信号服务
     * @param forecastService 预测服务
     * @param resourceCapabilityService 资源能力服务
     * @param dispatchContextSnapshotService 调度上下文服务
     * @param simulatorMarketDataService 模拟器市场数据服务
     * @param clock 系统时钟
     */
    public MarketPlanningController(
            MarketSignalService marketSignalService,
            ForecastService forecastService,
            ResourceCapabilityService resourceCapabilityService,
            DispatchContextSnapshotService dispatchContextSnapshotService,
            SimulatorMarketDataService simulatorMarketDataService,
            Clock clock
    ) {
        this.marketSignalService = marketSignalService;
        this.forecastService = forecastService;
        this.resourceCapabilityService = resourceCapabilityService;
        this.dispatchContextSnapshotService = dispatchContextSnapshotService;
        this.simulatorMarketDataService = simulatorMarketDataService;
        this.clock = clock;
    }

    /**
     * 创建市场信号快照。
     *
     * @param request 创建请求
     * @return 市场信号快照
     */
    @PostMapping("/market/signals")
    public ApiResponse<MarketSignalResponse> createMarketSignal(@Valid @RequestBody MarketSignalCreateRequest request) {
        return ApiResponse.success(marketSignalService.createSnapshot(request), OffsetDateTime.now(clock));
    }

    /**
     * 使用模拟器最新遥测计算本轮市场信号和短期预测。
     *
     * @return 模拟器派生的市场数据
     */
    @PostMapping("/market/simulator-data/refresh")
    public ApiResponse<SimulatorMarketDataRefreshResponse> refreshSimulatorMarketData() {
        return ApiResponse.success(simulatorMarketDataService.refresh(), OffsetDateTime.now(clock));
    }

    /**
     * 查询最新市场信号快照。
     *
     * @return 市场信号快照
     */
    @GetMapping("/market/signals/latest")
    public ApiResponse<MarketSignalResponse> latestMarketSignal() {
        return ApiResponse.success(marketSignalService.getLatestSnapshot(), OffsetDateTime.now(clock));
    }

    /**
     * 创建预测快照。
     *
     * @param request 创建请求
     * @return 预测快照
     */
    @PostMapping("/forecasts/snapshots")
    public ApiResponse<ForecastSnapshotResponse> createForecastSnapshot(@Valid @RequestBody ForecastSnapshotCreateRequest request) {
        return ApiResponse.success(forecastService.createSnapshot(request), OffsetDateTime.now(clock));
    }

    /**
     * 查询最新预测快照。
     *
     * @return 预测快照
     */
    @GetMapping("/forecasts/snapshots/latest")
    public ApiResponse<ForecastSnapshotResponse> latestForecastSnapshot() {
        return ApiResponse.success(forecastService.getLatestSnapshot(), OffsetDateTime.now(clock));
    }

    /**
     * 查询最新资源能力快照。
     *
     * @return 资源能力集合
     */
    @GetMapping("/capabilities/latest")
    public ApiResponse<List<ResourceCapabilityResponse>> latestCapabilities() {
        return ApiResponse.success(resourceCapabilityService.getLatestCapabilities(), OffsetDateTime.now(clock));
    }

    /**
     * 创建调度上下文快照。
     *
     * @return 调度上下文快照
     */
    @PostMapping("/dispatch-context/snapshots")
    public ApiResponse<DispatchContextSnapshotResponse> createDispatchContextSnapshot() {
        return ApiResponse.success(dispatchContextSnapshotService.createSnapshot(), OffsetDateTime.now(clock));
    }

    /**
     * 查询调度上下文快照。
     *
     * @param snapshotId 快照标识
     * @return 调度上下文快照
     */
    @GetMapping("/dispatch-context/snapshots/{snapshotId}")
    public ApiResponse<DispatchContextSnapshotResponse> getDispatchContextSnapshot(@PathVariable String snapshotId) {
        return ApiResponse.success(dispatchContextSnapshotService.getSnapshot(snapshotId), OffsetDateTime.now(clock));
    }
}

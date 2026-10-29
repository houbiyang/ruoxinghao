package com.weaksignal.vpp.backend.api;

import com.weaksignal.vpp.backend.api.dto.MarketDashboardOverviewResponse;
import com.weaksignal.vpp.backend.application.MarketDashboardQueryService;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 市场与电价页面聚合查询控制器。
 */
@RestController
@RequestMapping("/api/v1/market-dashboard")
public class MarketDashboardController {

    private final MarketDashboardQueryService marketDashboardQueryService;
    private final Clock clock;

    /**
     * 注入市场与电价页面控制器依赖。
     *
     * @param marketDashboardQueryService 市场页面查询服务
     * @param clock 系统时钟
     */
    public MarketDashboardController(MarketDashboardQueryService marketDashboardQueryService, Clock clock) {
        this.marketDashboardQueryService = marketDashboardQueryService;
        this.clock = clock;
    }

    /**
     * 查询市场与电价页面整屏数据。
     *
     * @param range 时间范围，支持 24H、48H、7D
     * @param newsLimit 新闻条数
     * @return 页面聚合响应
     */
    @GetMapping("/overview")
    public ApiResponse<MarketDashboardOverviewResponse> getOverview(
            @RequestParam(defaultValue = "24H") String range,
            @RequestParam(defaultValue = "5") int newsLimit
    ) {
        return ApiResponse.success(
                marketDashboardQueryService.getOverview(range, newsLimit),
                OffsetDateTime.now(clock)
        );
    }
}

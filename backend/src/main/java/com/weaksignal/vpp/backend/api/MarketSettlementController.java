package com.weaksignal.vpp.backend.api;

import com.weaksignal.vpp.backend.api.dto.MarketBidCreateRequest;
import com.weaksignal.vpp.backend.api.dto.MarketBidResponse;
import com.weaksignal.vpp.backend.api.dto.MarketClearingResponse;
import com.weaksignal.vpp.backend.api.dto.MarketClearingTestRequest;
import com.weaksignal.vpp.backend.api.dto.SettlementCalculationRequest;
import com.weaksignal.vpp.backend.api.dto.SettlementResultResponse;
import com.weaksignal.vpp.backend.application.MarketSettlementService;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 测试市场报价、出清与结算接口控制器。
 */
@Validated
@RestController
@RequestMapping("/api/v1")
public class MarketSettlementController {

    private final MarketSettlementService marketSettlementService;
    private final Clock clock;

    /**
     * 注入测试市场结算接口依赖。
     *
     * @param marketSettlementService 测试市场结算服务
     * @param clock 系统时钟
     */
    public MarketSettlementController(MarketSettlementService marketSettlementService, Clock clock) {
        this.marketSettlementService = marketSettlementService;
        this.clock = clock;
    }

    /**
     * 创建测试市场报价。
     *
     * @param request 报价请求
     * @return 报价响应
     */
    @PostMapping("/market/bids")
    public ApiResponse<MarketBidResponse> createBid(@Valid @RequestBody MarketBidCreateRequest request) {
        return ApiResponse.success(marketSettlementService.createBid(request), OffsetDateTime.now(clock));
    }

    /**
     * 查询报价详情。
     *
     * @param bidId 报价标识
     * @return 报价响应
     */
    @GetMapping("/market/bids/{bidId}")
    public ApiResponse<MarketBidResponse> getBid(@PathVariable String bidId) {
        return ApiResponse.success(marketSettlementService.getBid(bidId), OffsetDateTime.now(clock));
    }

    /**
     * 创建测试出清结果。
     *
     * @param request 测试出清请求
     * @return 出清响应
     */
    @PostMapping("/market/clearings/test")
    public ApiResponse<MarketClearingResponse> createTestClearing(@Valid @RequestBody MarketClearingTestRequest request) {
        return ApiResponse.success(marketSettlementService.createTestClearing(request), OffsetDateTime.now(clock));
    }

    /**
     * 查询测试出清详情。
     *
     * @param clearingId 出清标识
     * @return 出清响应
     */
    @GetMapping("/market/clearings/{clearingId}")
    public ApiResponse<MarketClearingResponse> getClearing(@PathVariable String clearingId) {
        return ApiResponse.success(marketSettlementService.getClearing(clearingId), OffsetDateTime.now(clock));
    }

    /**
     * 计算测试市场结算结果。
     *
     * @param request 结算计算请求
     * @return 结算响应
     */
    @PostMapping("/settlements/calculate")
    public ApiResponse<SettlementResultResponse> calculateSettlement(@Valid @RequestBody SettlementCalculationRequest request) {
        return ApiResponse.success(marketSettlementService.calculateSettlement(request), OffsetDateTime.now(clock));
    }

    /**
     * 查询结算详情。
     *
     * @param settlementId 结算标识
     * @return 结算响应
     */
    @GetMapping("/settlements/{settlementId}")
    public ApiResponse<SettlementResultResponse> getSettlement(@PathVariable String settlementId) {
        return ApiResponse.success(marketSettlementService.getSettlement(settlementId), OffsetDateTime.now(clock));
    }

    /**
     * 查询最新结算结果。
     *
     * @return 结算响应
     */
    @GetMapping("/settlements/latest")
    public ApiResponse<SettlementResultResponse> getLatestSettlement() {
        return ApiResponse.success(marketSettlementService.getLatestSettlement(), OffsetDateTime.now(clock));
    }
}

package com.weaksignal.vpp.backend.api;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.weaksignal.vpp.backend.api.dto.MarketDashboardOverviewResponse;
import com.weaksignal.vpp.backend.application.MarketDashboardQueryService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * 市场与电价页面聚合接口控制器测试。
 */
class MarketDashboardControllerTest {

    @Test
    void shouldReturnOverviewPayload() throws Exception {
        MarketDashboardQueryService service = mock(MarketDashboardQueryService.class);
        when(service.getOverview("48H", 3)).thenReturn(new MarketDashboardOverviewResponse(
                new MarketDashboardOverviewResponse.Summary(
                        new BigDecimal("0.82"),
                        new BigDecimal("1.18"),
                        "18:00-21:00",
                        new BigDecimal("120.00"),
                        new BigDecimal("68.35"),
                        new BigDecimal("56.96"),
                        new BigDecimal("0.91"),
                        new BigDecimal("2845.00"),
                        "西北电力市场",
                        "60分钟",
                        "16:00 - 17:00",
                        "实时运行",
                        "已出清"
                ),
                new MarketDashboardOverviewResponse.SummarySparklines(
                        List.of(new BigDecimal("0.44")),
                        List.of(new BigDecimal("0.56")),
                        List.of(new BigDecimal("0.51")),
                        List.of(new BigDecimal("0.45"))
                ),
                new MarketDashboardOverviewResponse.PriceTrend(
                        "48H",
                        List.of(new MarketDashboardOverviewResponse.PriceTrendPoint(
                                "07-10 16:00",
                                new BigDecimal("0.82"),
                                new BigDecimal("0.78"),
                                new BigDecimal("0.91"),
                                new BigDecimal("0.95")
                        ))
                ),
                List.of(new MarketDashboardOverviewResponse.DistributionItem("0.8 - 1.2 元/kWh", new BigDecimal("38.7"))),
                List.of(new MarketDashboardOverviewResponse.TimeOfUsePriceItem(
                        "16:00 - 17:00",
                        new BigDecimal("0.82"),
                        new BigDecimal("0.78"),
                        new BigDecimal("0.91"),
                        new BigDecimal("0.95")
                )),
                new MarketDashboardOverviewResponse.PredictionAnalysis(
                        List.of(new MarketDashboardOverviewResponse.PredictionPoint("16:00", new BigDecimal("0.91"))),
                        List.of(new MarketDashboardOverviewResponse.PredictionPoint("16:00", new BigDecimal("0.95"))),
                        "预计18:00-21:00为高价窗口。"
                ),
                List.of(new MarketDashboardOverviewResponse.RecommendationItem(
                        "storage-charge",
                        "储能充电建议",
                        "建议在低价窗口充电。",
                        "预计可节省成本",
                        new BigDecimal("2345.00"),
                        "green"
                )),
                List.of(new MarketDashboardOverviewResponse.NewsItem(
                        OffsetDateTime.parse("2026-07-10T15:30:00+08:00"),
                        "市场公告"
                )),
                List.of(new MarketDashboardOverviewResponse.DataSourceStatusItem("市场数据", true))
        ));

        Clock clock = Clock.fixed(Instant.parse("2026-07-10T08:00:00Z"), ZoneId.of("Asia/Shanghai"));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new MarketDashboardController(service, clock)).build();

        mockMvc.perform(get("/api/v1/market-dashboard/overview")
                        .param("range", "48H")
                        .param("newsLimit", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.summary.marketName").value("西北电力市场"))
                .andExpect(jsonPath("$.data.priceTrend.range").value("48H"))
                .andExpect(jsonPath("$.data.recommendations[0].type").value("storage-charge"));
    }
}

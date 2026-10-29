package com.weaksignal.vpp.backend.planning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weaksignal.vpp.backend.api.ApiException;
import com.weaksignal.vpp.backend.api.dto.DispatchContextSnapshotResponse;
import com.weaksignal.vpp.backend.capability.ResourceCapabilityService;
import com.weaksignal.vpp.backend.capability.ResourceCapabilitySnapshot;
import com.weaksignal.vpp.backend.config.PlanningProperties;
import com.weaksignal.vpp.backend.domain.LinkState;
import com.weaksignal.vpp.backend.domain.QualityCode;
import com.weaksignal.vpp.backend.domain.ResourceType;
import com.weaksignal.vpp.backend.forecast.ForecastPoint;
import com.weaksignal.vpp.backend.forecast.ForecastService;
import com.weaksignal.vpp.backend.forecast.ForecastSnapshot;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.DispatchContextSnapshotRepository;
import com.weaksignal.vpp.backend.market.MarketSignalService;
import com.weaksignal.vpp.backend.market.MarketSignalSnapshot;
import com.weaksignal.vpp.backend.market.SimulatorMarketDataService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 调度上下文快照服务测试。
 */
class DispatchContextSnapshotServiceTest {

    private DispatchContextSnapshotRepository dispatchContextSnapshotRepository;
    private MarketSignalService marketSignalService;
    private ForecastService forecastService;
    private SimulatorMarketDataService simulatorMarketDataService;
    private ResourceCapabilityService resourceCapabilityService;
    private DispatchContextSnapshotService dispatchContextSnapshotService;
    private Clock clock;

    @BeforeEach
    void setUp() {
        dispatchContextSnapshotRepository = mock(DispatchContextSnapshotRepository.class);
        marketSignalService = mock(MarketSignalService.class);
        forecastService = mock(ForecastService.class);
        simulatorMarketDataService = mock(SimulatorMarketDataService.class);
        resourceCapabilityService = mock(ResourceCapabilityService.class);
        clock = Clock.fixed(Instant.parse("2026-07-04T00:00:00Z"), ZoneId.of("Asia/Shanghai"));
        dispatchContextSnapshotService = new DispatchContextSnapshotService(
                dispatchContextSnapshotRepository,
                marketSignalService,
                forecastService,
                simulatorMarketDataService,
                resourceCapabilityService,
                new PlanningProperties(60, 15),
                new ObjectMapper().findAndRegisterModules(),
                clock
        );
    }

    @Test
    void shouldRefreshExperimentMarketDataBeforeCreatingSnapshot() {
        OffsetDateTime now = OffsetDateTime.now(clock);
        when(marketSignalService.getLatestActiveSnapshot()).thenReturn(new MarketSignalSnapshot(
                "market-1",
                "TEST_MARKET",
                now.plusMinutes(5),
                now.plusMinutes(65),
                280.0,
                new BigDecimal("0.452000"),
                new BigDecimal("0.486000"),
                new BigDecimal("0.518000"),
                320.0,
                180.0,
                42.0,
                28.0,
                now.minusMinutes(5),
                now,
                now.plusHours(2),
                "TEST_MARKET_DATA",
                QualityCode.GOOD,
                "market-v1",
                now.minusMinutes(4)
        ));
        when(forecastService.getLatestActiveSnapshot()).thenReturn(new ForecastSnapshot(
                "forecast-1",
                60,
                "forecast-v1",
                List.of(new ForecastPoint(
                        now.plusMinutes(5),
                        now.plusMinutes(65),
                        265.0,
                        288.0,
                        312.0,
                        98.0,
                        124.0,
                        146.0,
                        142.0,
                        176.0,
                        204.0,
                        new BigDecimal("0.438"),
                        new BigDecimal("0.486"),
                        new BigDecimal("0.536")
                )),
                now.minusMinutes(10),
                now,
                now.plusHours(2),
                "TEST_FORECAST_DATA",
                QualityCode.GOOD,
                "forecast-v1",
                now.minusMinutes(9)
        ));
        when(resourceCapabilityService.createCapabilitySnapshots()).thenReturn(List.of(new ResourceCapabilitySnapshot(
                "capability-1",
                "capability-group-1",
                "pv-gobi-01",
                ResourceType.PHOTOVOLTAIC,
                120.0,
                120.0,
                0.0,
                120.0,
                42.0,
                42.0,
                120.0,
                null,
                LinkState.S0,
                4L,
                0.98,
                true,
                "",
                now,
                now,
                now.plusSeconds(15),
                "REALTIME_TELEMETRY",
                QualityCode.GOOD,
                "capability-v1",
                now
        )));

        DispatchContextSnapshotResponse response = dispatchContextSnapshotService.createExperimentSnapshot();

        assertEquals("market-1", response.marketSignal().snapshotId());
        assertEquals("forecast-1", response.forecastSnapshot().snapshotId());
        assertEquals("capability-group-1", response.resourceCapabilities().getFirst().snapshotGroupId());
        verify(simulatorMarketDataService).refreshForExperiment();
    }

    @Test
    void shouldRejectDispatchContextWhenCapabilitiesAlreadyExpired() {
        OffsetDateTime now = OffsetDateTime.now(clock);
        when(marketSignalService.getLatestActiveSnapshot()).thenReturn(new MarketSignalSnapshot(
                "market-1",
                "TEST_MARKET",
                now.plusMinutes(5),
                now.plusMinutes(65),
                280.0,
                new BigDecimal("0.452000"),
                new BigDecimal("0.486000"),
                new BigDecimal("0.518000"),
                320.0,
                180.0,
                42.0,
                28.0,
                now.minusMinutes(5),
                now,
                now.plusHours(2),
                "TEST_MARKET_DATA",
                QualityCode.GOOD,
                "market-v1",
                now.minusMinutes(4)
        ));
        when(forecastService.getLatestActiveSnapshot()).thenReturn(new ForecastSnapshot(
                "forecast-1",
                60,
                "forecast-v1",
                List.of(),
                now.minusMinutes(10),
                now,
                now.plusHours(2),
                "TEST_FORECAST_DATA",
                QualityCode.GOOD,
                "forecast-v1",
                now.minusMinutes(9)
        ));
        when(resourceCapabilityService.createCapabilitySnapshots()).thenReturn(List.of(new ResourceCapabilitySnapshot(
                "capability-1",
                "capability-group-1",
                "pv-gobi-01",
                ResourceType.PHOTOVOLTAIC,
                120.0,
                120.0,
                0.0,
                120.0,
                42.0,
                42.0,
                120.0,
                null,
                LinkState.S0,
                4L,
                0.98,
                true,
                "",
                now,
                now,
                now.minusSeconds(1),
                "REALTIME_TELEMETRY",
                QualityCode.GOOD,
                "capability-v1",
                now
        )));

        ApiException exception = assertThrows(ApiException.class, () -> dispatchContextSnapshotService.createSnapshot());

        assertEquals("DISPATCH_CONTEXT_EXPIRED", exception.getCode());
    }
}

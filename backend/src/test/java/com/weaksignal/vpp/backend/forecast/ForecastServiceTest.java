package com.weaksignal.vpp.backend.forecast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weaksignal.vpp.backend.api.ApiException;
import com.weaksignal.vpp.backend.api.dto.ForecastPointRequest;
import com.weaksignal.vpp.backend.api.dto.ForecastSnapshotCreateRequest;
import com.weaksignal.vpp.backend.domain.QualityCode;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.ForecastSnapshotRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 预测快照服务测试。
 */
class ForecastServiceTest {

    private ForecastService forecastService;
    private Clock clock;

    @BeforeEach
    void setUp() {
        ForecastSnapshotRepository forecastSnapshotRepository = mock(ForecastSnapshotRepository.class);
        clock = Clock.fixed(Instant.parse("2026-07-04T00:00:00Z"), ZoneId.of("Asia/Shanghai"));
        forecastService = new ForecastService(
                forecastSnapshotRepository,
                new ObjectMapper().findAndRegisterModules(),
                clock
        );
    }

    @Test
    void shouldRejectExpiredForecastSnapshotRequest() {
        OffsetDateTime now = OffsetDateTime.now(clock);
        ForecastSnapshotCreateRequest request = new ForecastSnapshotCreateRequest(
                60,
                "deterministic-baseline-v1",
                List.of(new ForecastPointRequest(
                        now.plusHours(1),
                        now.plusHours(2),
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
                now.minusMinutes(20),
                now.minusMinutes(10),
                now.minusMinutes(1),
                "TEST_FORECAST_DATA",
                QualityCode.GOOD,
                "forecast-v1",
                now.minusMinutes(19)
        );

        ApiException exception = assertThrows(ApiException.class, () -> forecastService.createSnapshot(request));

        assertEquals("FORECAST_SNAPSHOT_EXPIRED", exception.getCode());
    }
}

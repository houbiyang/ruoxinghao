package com.weaksignal.vpp.backend.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.weaksignal.vpp.backend.application.StationDeviceQueryService;
import com.weaksignal.vpp.backend.application.DashboardQueryService;
import com.weaksignal.vpp.backend.application.ScenarioService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * 系统控制器测试。
 */
class SystemControllerTest {

    @Test
    void shouldReturnValidationErrorForInvalidScenario() throws Exception {
        Clock clock = Clock.fixed(Instant.parse("2026-06-29T13:30:00Z"), ZoneId.of("Asia/Shanghai"));
        ScenarioService scenarioService = new ScenarioService(null, null, null, null, clock) {
            @Override
            public com.weaksignal.vpp.backend.api.dto.ScenarioActivationResponse activateScenario(String scenarioCode) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SCENARIO", "非法场景参数", Map.of("scenario", scenarioCode));
            }
        };
        DashboardQueryService queryService = new DashboardQueryService(
                null, null, null, null, null, null, null, null, new com.fasterxml.jackson.databind.ObjectMapper(), clock
        );
        StationDeviceQueryService stationDeviceQueryService = new StationDeviceQueryService(null, null, null, null, clock);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new SystemController(queryService, stationDeviceQueryService, scenarioService, clock))
                .setControllerAdvice(new GlobalExceptionHandler(clock))
                .build();

        mockMvc.perform(post("/api/v1/scenarios/invalid/activate"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SCENARIO"));
    }

    @Test
    void shouldReturnNotFoundForMissingStation() throws Exception {
        Clock clock = Clock.fixed(Instant.parse("2026-06-29T13:30:00Z"), ZoneId.of("Asia/Shanghai"));
        DashboardQueryService queryService = new DashboardQueryService(
                null, null, null, null, null, null, null, null, new com.fasterxml.jackson.databind.ObjectMapper(), clock
        ) {
            @Override
            public com.weaksignal.vpp.backend.api.dto.StationDetailResponse getStationDetail(String stationId) {
                throw new ApiException(HttpStatus.NOT_FOUND, "STATION_NOT_FOUND", "场站不存在", Map.of("stationId", stationId));
            }
        };
        ScenarioService scenarioService = new ScenarioService(null, null, null, null, clock);
        StationDeviceQueryService stationDeviceQueryService = new StationDeviceQueryService(null, null, null, null, clock);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new SystemController(queryService, stationDeviceQueryService, scenarioService, clock))
                .setControllerAdvice(new GlobalExceptionHandler(clock))
                .build();

        mockMvc.perform(get("/api/v1/stations/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STATION_NOT_FOUND"));
    }
}

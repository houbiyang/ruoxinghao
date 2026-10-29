package com.weaksignal.vpp.backend.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.weaksignal.vpp.backend.api.dto.AgentDispatchOverviewResponse;
import com.weaksignal.vpp.backend.api.dto.ExperimentStartResponse;
import com.weaksignal.vpp.backend.api.dto.ExperimentStopResponse;
import com.weaksignal.vpp.backend.application.AgentDispatchOverviewService;
import com.weaksignal.vpp.backend.domain.ControlMode;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * 多智能体调度中心控制器测试。
 */
class AgentDispatchControllerTest {

    @Test
    void shouldReturnOverviewPayload() throws Exception {
        Clock clock = Clock.fixed(Instant.parse("2026-07-05T10:05:00Z"), ZoneId.of("Asia/Shanghai"));
        AgentDispatchOverviewService service = new AgentDispatchOverviewService(null, null, null, null, null, null, null, null, null, clock) {
            @Override
            public AgentDispatchOverviewResponse getOverview() {
                return new AgentDispatchOverviewResponse(
                        new AgentDispatchOverviewResponse.SystemStatusResponse(true, true, "deepseek", ControlMode.EDGE_COORDINATION, "DEGRADED", "REALTIME", OffsetDateTime.now(clock)),
                        null,
                        null,
                        null,
                        null,
                        new AgentDispatchOverviewResponse.ComparisonSummaryResponse("run-1", "T1_LATENCY_JITTER", "通信时延抖动", List.of()),
                        null,
                        null
                );
            }
        };
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AgentDispatchController(service, clock))
                .setControllerAdvice(new GlobalExceptionHandler(clock))
                .build();

        mockMvc.perform(get("/api/v1/agent-dispatch/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.systemStatus.aiProvider").value("deepseek"))
                .andExpect(jsonPath("$.data.comparisonSummary.runId").value("run-1"));
    }

    @Test
    void shouldProxyStartAndStopExperiment() throws Exception {
        Clock clock = Clock.fixed(Instant.parse("2026-07-05T10:05:00Z"), ZoneId.of("Asia/Shanghai"));
        AgentDispatchOverviewService service = new AgentDispatchOverviewService(null, null, null, null, null, null, null, null, null, clock) {
            @Override
            public ExperimentStartResponse startExperiment(String scenarioCode) {
                return new ExperimentStartResponse("run-1", scenarioCode, "RUNNING", OffsetDateTime.now(clock));
            }

            @Override
            public ExperimentStopResponse stopCurrentExperiment() {
                return new ExperimentStopResponse("run-1", "STOPPED", OffsetDateTime.now(clock));
            }
        };
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AgentDispatchController(service, clock))
                .setControllerAdvice(new GlobalExceptionHandler(clock))
                .build();

        mockMvc.perform(post("/api/v1/agent-dispatch/experiments/T1_LATENCY_JITTER/start"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.experimentRunId").value("run-1"))
                .andExpect(jsonPath("$.data.scenarioCode").value("T1_LATENCY_JITTER"));

        mockMvc.perform(post("/api/v1/agent-dispatch/experiments/current/stop"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.runId").value("run-1"))
                .andExpect(jsonPath("$.data.status").value("STOPPED"));
    }
}

package com.weaksignal.vpp.backend.agent;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.weaksignal.vpp.backend.config.AiProperties;
import com.weaksignal.vpp.backend.domain.ControlMode;
import com.weaksignal.vpp.backend.domain.LinkState;
import com.weaksignal.vpp.backend.fault.AgentStructuredResponse;
import com.weaksignal.vpp.backend.fault.AgentType;
import com.weaksignal.vpp.backend.fault.FaultContext;
import com.weaksignal.vpp.backend.fault.FaultRuleDecision;
import com.weaksignal.vpp.backend.fault.FaultSeverity;
import com.weaksignal.vpp.backend.infrastructure.persistence.entity.StationEntity;
import com.weaksignal.vpp.backend.infrastructure.persistence.repository.StationRepository;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * OpenAI 兼容 Provider 边界测试。
 */
class OpenAiCompatibleProviderTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void shouldReturnEmptyWhenDisabled() {
        OpenAiCompatibleProvider provider = provider(false, "", 1);
        assertTrue(provider.analyze(request()).isEmpty());
    }

    @Test
    void shouldParseValidStructuredJson() throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        AgentStructuredResponse structured = new AgentStructuredResponse(
                AgentType.STATE_DIAGNOSIS, "stub", "C1", "LATENCY", List.of("pv-gobi-01"),
                FaultSeverity.MEDIUM, 0.8, List.of(), ControlMode.EDGE_COORDINATION,
                List.of(), false, "测试结构化响应"
        );
        String outer = mapper.writeValueAsString(java.util.Map.of(
                "choices", List.of(java.util.Map.of(
                        "message", java.util.Map.of("content", mapper.writeValueAsString(structured))
                ))
        ));
        startServer(200, outer, 0);
        assertTrue(provider(true, endpoint(), 2).analyze(request()).isPresent());
    }

    @Test
    void shouldRejectInvalidJsonAndHttpFailure() throws Exception {
        startServer(200, "{\"choices\":[{\"message\":{\"content\":\"not-json\"}}]}", 0);
        assertTrue(provider(true, endpoint(), 2).analyze(request()).isEmpty());
        server.stop(0);
        server = null;
        startServer(500, "{}", 0);
        assertTrue(provider(true, endpoint(), 2).analyze(request()).isEmpty());
    }

    @Test
    void shouldFallbackOnTimeout() throws Exception {
        startServer(200, "{}", 1500);
        assertTrue(provider(true, endpoint(), 1).analyze(request()).isEmpty());
    }

    private OpenAiCompatibleProvider provider(boolean enabled, String url, int timeoutSeconds) {
        StationRepository repository = mock(StationRepository.class);
        StationEntity station = new StationEntity();
        station.setStationId("pv-gobi-01");
        station.setEnabled(true);
        when(repository.findByStationId("pv-gobi-01")).thenReturn(Optional.of(station));
        return new OpenAiCompatibleProvider(
                "stub",
                enabled,
                6000,
                new AiProperties.ProviderProperties(enabled, url, "test-key", "test-model", timeoutSeconds),
                new ObjectMapper().findAndRegisterModules(),
                new AgentResponseValidator(repository),
                Clock.systemUTC()
        );
    }

    private AgentRequest request() {
        FaultContext context = new FaultContext(null, "pv-gobi-01", LinkState.S1, 100, 20, 2,
                0, 4, 1, 0, false, false, 0, false, false, 100, 168, true);
        FaultRuleDecision decision = new FaultRuleDecision(true, "C1", "LATENCY", FaultSeverity.MEDIUM,
                LinkState.S1, List.of(), true, List.of());
        return new AgentRequest(AgentType.STATE_DIAGNOSIS, context, decision);
    }

    private void startServer(int statusCode, String responseBody, long delayMillis) throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/chat/completions", exchange -> {
            try {
                if (delayMillis > 0) {
                    Thread.sleep(delayMillis);
                }
                byte[] responseBytes = responseBody.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(statusCode, responseBytes.length);
                exchange.getResponseBody().write(responseBytes);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
    }

    private String endpoint() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/chat/completions";
    }
}

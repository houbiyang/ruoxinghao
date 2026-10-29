package com.weaksignal.vpp.backend.api;

import com.weaksignal.vpp.backend.api.dto.SolverPerformanceAuditPageResponse;
import com.weaksignal.vpp.backend.api.dto.SolverStatusResponse;
import com.weaksignal.vpp.backend.application.SolverObservabilityService;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** OR-Tools 只读状态与性能审计接口。 */
@RestController
@RequestMapping("/api/v1/market-dispatch/solver")
public class SolverObservabilityController {
    private final SolverObservabilityService service; private final Clock clock;
    public SolverObservabilityController(SolverObservabilityService service, Clock clock) { this.service = service; this.clock = clock; }
    @GetMapping("/status") public ApiResponse<SolverStatusResponse> status() { return ApiResponse.success(service.status(), OffsetDateTime.now(clock)); }
    @GetMapping("/audits") public ApiResponse<SolverPerformanceAuditPageResponse> audits(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) { return ApiResponse.success(service.audits(page, size), OffsetDateTime.now(clock)); }
}

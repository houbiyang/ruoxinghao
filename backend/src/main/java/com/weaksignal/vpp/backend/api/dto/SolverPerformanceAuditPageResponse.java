package com.weaksignal.vpp.backend.api.dto;

import java.util.List;

/** 有上限的求解性能审计分页响应。 */
public record SolverPerformanceAuditPageResponse(List<SolverPerformanceAuditResponse> items, int page, int size) { }

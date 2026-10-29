package com.weaksignal.vpp.backend.api.dto;

import java.time.OffsetDateTime;

/** OR-Tools 求解器只读状态。 */
public record SolverStatusResponse(String solverType, String solverName, String solverVersion, boolean configured,
        boolean nativeLibraryLoaded, boolean available, OffsetDateTime lastCheckAt, String lastSolveStatus,
        Long lastSolveTimeMs, boolean fallbackEnabled, boolean fallbackActive, String failureCategory, String failureReason) { }

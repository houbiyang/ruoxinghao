package com.weaksignal.vpp.backend.marketdispatch;

/** OR-Tools 原生库可用性快照。 */
public record OrToolsAvailability(boolean solverAvailable, String solverName, String solverVersion, String failureReason) { }

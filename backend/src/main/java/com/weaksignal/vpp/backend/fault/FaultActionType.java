package com.weaksignal.vpp.backend.fault;

/**
 * 故障处置动作白名单。
 */
public enum FaultActionType {
    MARK_DATA_UNTRUSTED,
    FREEZE_EXPIRED_COMMANDS,
    REDUCE_TELEMETRY_RATE,
    LIMIT_CONTROL_RAMP,
    EXCLUDE_OFFLINE_CAPACITY,
    ENTER_LOCAL_AUTONOMY,
    ENABLE_CONSERVATIVE_ESTIMATE,
    START_RATE_LIMITED_RECOVERY,
    ISOLATE_STATION,
    SWITCH_CONTROL_AUTHORITY,
    REDISTRIBUTE_AGGREGATE_POWER,
    RESTORE_CENTRAL_CONTROL;

    /**
     * 判断动作是否属于首阶段低风险自动执行白名单。
     *
     * @return 是否允许在低风险条件下自动执行
     */
    public boolean isAutomaticWhitelistAction() {
        return switch (this) {
            case MARK_DATA_UNTRUSTED,
                    FREEZE_EXPIRED_COMMANDS,
                    REDUCE_TELEMETRY_RATE,
                    LIMIT_CONTROL_RAMP,
                    EXCLUDE_OFFLINE_CAPACITY,
                    ENTER_LOCAL_AUTONOMY,
                    ENABLE_CONSERVATIVE_ESTIMATE,
                    START_RATE_LIMITED_RECOVERY -> true;
            default -> false;
        };
    }
}

CREATE TABLE IF NOT EXISTS station (
    id BIGSERIAL PRIMARY KEY,
    station_id VARCHAR(64) NOT NULL UNIQUE,
    station_name VARCHAR(64) NOT NULL,
    resource_type VARCHAR(32) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS resource_device (
    id BIGSERIAL PRIMARY KEY,
    device_id VARCHAR(64) NOT NULL UNIQUE,
    station_id VARCHAR(64) NOT NULL,
    device_name VARCHAR(64) NOT NULL,
    device_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS telemetry_snapshot (
    id BIGSERIAL PRIMARY KEY,
    station_id VARCHAR(64) NOT NULL UNIQUE,
    message_id VARCHAR(64) NOT NULL UNIQUE,
    sequence_number BIGINT NOT NULL,
    device_timestamp TIMESTAMPTZ NOT NULL,
    received_timestamp TIMESTAMPTZ NOT NULL,
    active_power DOUBLE PRECISION NOT NULL,
    available_capacity DOUBLE PRECISION NOT NULL,
    latency INTEGER NOT NULL,
    packet_loss_rate DOUBLE PRECISION NOT NULL,
    data_age BIGINT NOT NULL,
    link_state VARCHAR(8) NOT NULL,
    quality_code VARCHAR(32) NOT NULL,
    online BOOLEAN NOT NULL,
    experiment_run_id VARCHAR(64),
    active_fault_code VARCHAR(64),
    payload_json TEXT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS telemetry_history (
    id BIGSERIAL PRIMARY KEY,
    station_id VARCHAR(64) NOT NULL,
    message_id VARCHAR(64) NOT NULL UNIQUE,
    sequence_number BIGINT NOT NULL,
    device_timestamp TIMESTAMPTZ NOT NULL,
    received_timestamp TIMESTAMPTZ NOT NULL,
    active_power DOUBLE PRECISION NOT NULL,
    available_capacity DOUBLE PRECISION NOT NULL,
    latency INTEGER NOT NULL,
    packet_loss_rate DOUBLE PRECISION NOT NULL,
    data_age BIGINT NOT NULL,
    link_state VARCHAR(8) NOT NULL,
    quality_code VARCHAR(32) NOT NULL,
    online BOOLEAN NOT NULL DEFAULT TRUE,
    experiment_run_id VARCHAR(64),
    active_fault_code VARCHAR(64),
    payload_json TEXT NOT NULL
);

ALTER TABLE telemetry_snapshot ADD COLUMN IF NOT EXISTS experiment_run_id VARCHAR(64);
ALTER TABLE telemetry_snapshot ADD COLUMN IF NOT EXISTS active_fault_code VARCHAR(64);
ALTER TABLE telemetry_history ADD COLUMN IF NOT EXISTS experiment_run_id VARCHAR(64);
ALTER TABLE telemetry_history ADD COLUMN IF NOT EXISTS active_fault_code VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_telemetry_history_station_time
    ON telemetry_history (station_id, received_timestamp DESC);

CREATE INDEX IF NOT EXISTS idx_telemetry_history_station_sequence
    ON telemetry_history (station_id, sequence_number DESC);

CREATE INDEX IF NOT EXISTS idx_telemetry_history_received_time
    ON telemetry_history (received_timestamp DESC);

CREATE INDEX IF NOT EXISTS idx_telemetry_snapshot_experiment_run_id
    ON telemetry_snapshot (experiment_run_id);

CREATE INDEX IF NOT EXISTS idx_telemetry_snapshot_active_fault_code
    ON telemetry_snapshot (active_fault_code);

CREATE INDEX IF NOT EXISTS idx_telemetry_history_experiment_run_id
    ON telemetry_history (experiment_run_id);

CREATE INDEX IF NOT EXISTS idx_telemetry_history_active_fault_code
    ON telemetry_history (active_fault_code);

CREATE INDEX IF NOT EXISTS idx_telemetry_history_experiment_station_time
    ON telemetry_history (experiment_run_id, station_id, received_timestamp ASC);

CREATE TABLE IF NOT EXISTS control_command (
    id BIGSERIAL PRIMARY KEY,
    command_id VARCHAR(64) NOT NULL UNIQUE,
    station_id VARCHAR(64) NOT NULL,
    command_type VARCHAR(64) NOT NULL,
    batch_id VARCHAR(64),
    dispatch_plan_id VARCHAR(64),
    plan_version VARCHAR(64),
    dispatch_plan_item_id VARCHAR(64),
    input_snapshot_id VARCHAR(64),
    command_payload TEXT NOT NULL,
    send_time TIMESTAMPTZ NOT NULL,
    expire_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE control_command
    ADD COLUMN IF NOT EXISTS batch_id VARCHAR(64);

ALTER TABLE control_command
    ADD COLUMN IF NOT EXISTS dispatch_plan_id VARCHAR(64);

ALTER TABLE control_command
    ADD COLUMN IF NOT EXISTS plan_version VARCHAR(64);

ALTER TABLE control_command
    ADD COLUMN IF NOT EXISTS dispatch_plan_item_id VARCHAR(64);

ALTER TABLE control_command
    ADD COLUMN IF NOT EXISTS input_snapshot_id VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_control_command_plan
    ON control_command (dispatch_plan_id, created_at DESC);

CREATE TABLE IF NOT EXISTS command_ack (
    id BIGSERIAL PRIMARY KEY,
    command_id VARCHAR(64) NOT NULL,
    station_id VARCHAR(64) NOT NULL,
    ack_stage VARCHAR(32) NOT NULL,
    success BOOLEAN NOT NULL,
    message VARCHAR(256),
    ack_time TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_command_ack_command_id
    ON command_ack (command_id, ack_time DESC);

CREATE TABLE IF NOT EXISTS link_status_log (
    id BIGSERIAL PRIMARY KEY,
    station_id VARCHAR(64) NOT NULL,
    previous_state VARCHAR(8),
    current_state VARCHAR(8) NOT NULL,
    reason VARCHAR(256) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_link_status_log_station_time
    ON link_status_log (station_id, occurred_at DESC);

CREATE TABLE IF NOT EXISTS fault_event (
    id BIGSERIAL PRIMARY KEY,
    event_id VARCHAR(64) NOT NULL UNIQUE,
    station_id VARCHAR(64) NOT NULL,
    fault_code VARCHAR(64) NOT NULL,
    title VARCHAR(128) NOT NULL,
    description VARCHAR(512) NOT NULL,
    level VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    recovered_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_fault_event_station_time
    ON fault_event (station_id, occurred_at DESC);

CREATE TABLE IF NOT EXISTS experiment_scenario (
    id BIGSERIAL PRIMARY KEY,
    scenario_code VARCHAR(32) NOT NULL UNIQUE,
    scenario_name VARCHAR(64) NOT NULL,
    description VARCHAR(256) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS experiment_run (
    id BIGSERIAL PRIMARY KEY,
    run_id VARCHAR(64) NOT NULL UNIQUE,
    scenario_code VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    ended_at TIMESTAMPTZ,
    result_summary TEXT
);

CREATE TABLE IF NOT EXISTS fault_diagnosis_run (
    id BIGSERIAL PRIMARY KEY,
    run_id VARCHAR(64) NOT NULL UNIQUE,
    event_id VARCHAR(64),
    station_id VARCHAR(64) NOT NULL,
    rule_result_json TEXT NOT NULL,
    final_decision_json TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    approval_status VARCHAR(32) NOT NULL,
    conflict_detected BOOLEAN NOT NULL DEFAULT FALSE,
    fallback_used BOOLEAN NOT NULL DEFAULT FALSE,
    review_reason VARCHAR(512),
    reviewed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_fault_diagnosis_run_created
    ON fault_diagnosis_run (created_at DESC);

CREATE TABLE IF NOT EXISTS agent_diagnosis_result (
    id BIGSERIAL PRIMARY KEY,
    result_id VARCHAR(64) NOT NULL UNIQUE,
    run_id VARCHAR(64) NOT NULL,
    agent_type VARCHAR(64) NOT NULL,
    provider VARCHAR(32) NOT NULL,
    fault_code VARCHAR(64) NOT NULL,
    severity VARCHAR(16) NOT NULL,
    confidence DOUBLE PRECISION NOT NULL,
    result_json TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_agent_diagnosis_result_run
    ON agent_diagnosis_result (run_id, created_at ASC);

CREATE TABLE IF NOT EXISTS fault_action_plan (
    id BIGSERIAL PRIMARY KEY,
    action_id VARCHAR(64) NOT NULL UNIQUE,
    run_id VARCHAR(64) NOT NULL,
    station_id VARCHAR(64) NOT NULL,
    action_type VARCHAR(64) NOT NULL,
    parameters_json TEXT NOT NULL,
    risk_level VARCHAR(16) NOT NULL,
    status VARCHAR(32) NOT NULL,
    requires_approval BOOLEAN NOT NULL,
    mqtt_command_id VARCHAR(64),
    issued_at TIMESTAMPTZ,
    expire_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_fault_action_plan_run
    ON fault_action_plan (run_id, created_at ASC);

CREATE TABLE IF NOT EXISTS market_signal_snapshot (
    id BIGSERIAL PRIMARY KEY,
    snapshot_id VARCHAR(64) NOT NULL UNIQUE,
    market_type VARCHAR(32) NOT NULL,
    interval_start TIMESTAMPTZ NOT NULL,
    interval_end TIMESTAMPTZ NOT NULL,
    contract_power DOUBLE PRECISION NOT NULL,
    contract_price NUMERIC(18, 6) NOT NULL,
    day_ahead_price NUMERIC(18, 6) NOT NULL,
    real_time_price NUMERIC(18, 6) NOT NULL,
    grid_import_limit DOUBLE PRECISION NOT NULL,
    grid_export_limit DOUBLE PRECISION NOT NULL,
    reserve_up_requirement DOUBLE PRECISION NOT NULL,
    reserve_down_requirement DOUBLE PRECISION NOT NULL,
    source VARCHAR(64) NOT NULL,
    quality_code VARCHAR(32) NOT NULL,
    version VARCHAR(64) NOT NULL,
    generated_at TIMESTAMPTZ NOT NULL,
    valid_from TIMESTAMPTZ NOT NULL,
    valid_until TIMESTAMPTZ NOT NULL,
    received_at TIMESTAMPTZ NOT NULL,
    payload_json TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_market_signal_snapshot_generated
    ON market_signal_snapshot (generated_at DESC);

CREATE TABLE IF NOT EXISTS forecast_snapshot (
    id BIGSERIAL PRIMARY KEY,
    snapshot_id VARCHAR(64) NOT NULL UNIQUE,
    interval_minutes INTEGER NOT NULL,
    model_version VARCHAR(64) NOT NULL,
    source VARCHAR(64) NOT NULL,
    quality_code VARCHAR(32) NOT NULL,
    version VARCHAR(64) NOT NULL,
    generated_at TIMESTAMPTZ NOT NULL,
    valid_from TIMESTAMPTZ NOT NULL,
    valid_until TIMESTAMPTZ NOT NULL,
    received_at TIMESTAMPTZ NOT NULL,
    payload_json TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_forecast_snapshot_generated
    ON forecast_snapshot (generated_at DESC);

CREATE TABLE IF NOT EXISTS resource_capability_snapshot (
    id BIGSERIAL PRIMARY KEY,
    capability_id VARCHAR(64) NOT NULL UNIQUE,
    snapshot_group_id VARCHAR(64) NOT NULL,
    station_id VARCHAR(64) NOT NULL,
    resource_type VARCHAR(32) NOT NULL,
    current_power DOUBLE PRECISION NOT NULL,
    available_capacity DOUBLE PRECISION NOT NULL,
    minimum_power DOUBLE PRECISION NOT NULL,
    maximum_power DOUBLE PRECISION NOT NULL,
    maximum_ramp_up DOUBLE PRECISION NOT NULL,
    maximum_ramp_down DOUBLE PRECISION NOT NULL,
    available_energy_kwh DOUBLE PRECISION NOT NULL,
    soc DOUBLE PRECISION,
    minimum_soc DOUBLE PRECISION,
    maximum_soc DOUBLE PRECISION,
    energy_capacity_kwh DOUBLE PRECISION,
    maximum_charge_power_kw DOUBLE PRECISION,
    maximum_discharge_power_kw DOUBLE PRECISION,
    charge_efficiency DOUBLE PRECISION,
    discharge_efficiency DOUBLE PRECISION,
    cycle_cost NUMERIC(18, 6),
    state_of_health DOUBLE PRECISION,
    reserved_up_power DOUBLE PRECISION NOT NULL,
    reserved_down_power DOUBLE PRECISION NOT NULL,
    link_state VARCHAR(8) NOT NULL,
    data_age BIGINT NOT NULL,
    capability_confidence DOUBLE PRECISION NOT NULL,
    data_trusted BOOLEAN NOT NULL,
    unavailable_reason VARCHAR(256),
    source VARCHAR(64) NOT NULL,
    quality_code VARCHAR(32) NOT NULL,
    version VARCHAR(64) NOT NULL,
    generated_at TIMESTAMPTZ NOT NULL,
    valid_from TIMESTAMPTZ NOT NULL,
    valid_until TIMESTAMPTZ NOT NULL,
    received_at TIMESTAMPTZ NOT NULL,
    payload_json TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_resource_capability_snapshot_group
    ON resource_capability_snapshot (snapshot_group_id, station_id ASC);

CREATE INDEX IF NOT EXISTS idx_resource_capability_snapshot_generated
    ON resource_capability_snapshot (generated_at DESC);

CREATE TABLE IF NOT EXISTS dispatch_context_snapshot (
    id BIGSERIAL PRIMARY KEY,
    snapshot_id VARCHAR(64) NOT NULL UNIQUE,
    market_signal_snapshot_id VARCHAR(64) NOT NULL,
    forecast_snapshot_id VARCHAR(64) NOT NULL,
    capability_snapshot_group_id VARCHAR(64) NOT NULL,
    interval_minutes INTEGER NOT NULL,
    source VARCHAR(64) NOT NULL,
    quality_code VARCHAR(32) NOT NULL,
    version VARCHAR(64) NOT NULL,
    generated_at TIMESTAMPTZ NOT NULL,
    valid_from TIMESTAMPTZ NOT NULL,
    valid_until TIMESTAMPTZ NOT NULL,
    received_at TIMESTAMPTZ NOT NULL,
    payload_json TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_dispatch_context_snapshot_generated
    ON dispatch_context_snapshot (generated_at DESC);

CREATE TABLE IF NOT EXISTS dispatch_plan (
    id BIGSERIAL PRIMARY KEY,
    plan_id VARCHAR(64) NOT NULL UNIQUE,
    input_snapshot_id VARCHAR(64) NOT NULL,
    interval_minutes INTEGER NOT NULL,
    conservative_plan BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(32) NOT NULL,
    source VARCHAR(64) NOT NULL,
    quality_code VARCHAR(32) NOT NULL,
    version VARCHAR(64) NOT NULL,
    generated_at TIMESTAMPTZ NOT NULL,
    valid_from TIMESTAMPTZ NOT NULL,
    valid_until TIMESTAMPTZ NOT NULL,
    received_at TIMESTAMPTZ NOT NULL,
    payload_json TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_dispatch_plan_generated
    ON dispatch_plan (generated_at DESC);

CREATE TABLE IF NOT EXISTS dispatch_plan_item (
    id BIGSERIAL PRIMARY KEY,
    item_id VARCHAR(64) NOT NULL UNIQUE,
    plan_id VARCHAR(64) NOT NULL,
    input_snapshot_id VARCHAR(64) NOT NULL,
    interval_start TIMESTAMPTZ NOT NULL,
    interval_end TIMESTAMPTZ NOT NULL,
    net_power_target DOUBLE PRECISION NOT NULL,
    grid_import_power DOUBLE PRECISION NOT NULL,
    grid_export_power DOUBLE PRECISION NOT NULL,
    storage_charge_power DOUBLE PRECISION NOT NULL,
    storage_discharge_power DOUBLE PRECISION NOT NULL,
    flexible_load_adjustment DOUBLE PRECISION NOT NULL,
    upward_reserve DOUBLE PRECISION NOT NULL,
    downward_reserve DOUBLE PRECISION NOT NULL,
    renewable_curtailment DOUBLE PRECISION NOT NULL,
    expected_cost NUMERIC(18, 6) NOT NULL,
    expected_revenue NUMERIC(18, 6) NOT NULL,
    risk_cost NUMERIC(18, 6) NOT NULL,
    target_powers_json TEXT NOT NULL DEFAULT '{}',
    reason_code VARCHAR(64) NOT NULL,
    item_order INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE dispatch_plan_item
    ADD COLUMN IF NOT EXISTS target_powers_json TEXT NOT NULL DEFAULT '{}';

CREATE INDEX IF NOT EXISTS idx_dispatch_plan_item_plan
    ON dispatch_plan_item (plan_id, item_order ASC);

CREATE TABLE IF NOT EXISTS dispatch_convergence_round (
    id BIGSERIAL PRIMARY KEY,
    round_id VARCHAR(64) NOT NULL UNIQUE,
    experiment_run_id VARCHAR(64) NOT NULL,
    round_number INTEGER NOT NULL,
    source_plan_id VARCHAR(64), snapshot_id VARCHAR(64), agent_run_id VARCHAR(64),
    replacement_plan_id VARCHAR(64), execution_batch_id VARCHAR(64),
    status VARCHAR(32) NOT NULL, trigger_reason VARCHAR(64) NOT NULL,
    consecutive_success_count INTEGER NOT NULL DEFAULT 0,
    audit_json TEXT NOT NULL, started_at TIMESTAMPTZ NOT NULL, ended_at TIMESTAMPTZ,
    CONSTRAINT uk_dispatch_convergence_round_experiment_round UNIQUE (experiment_run_id, round_number)
);

CREATE INDEX IF NOT EXISTS idx_dispatch_convergence_round_experiment
    ON dispatch_convergence_round (experiment_run_id, round_number DESC);

CREATE TABLE IF NOT EXISTS market_agent_run (
    id BIGSERIAL PRIMARY KEY,
    run_id VARCHAR(64) NOT NULL UNIQUE,
    snapshot_id VARCHAR(64) NOT NULL,
    dispatch_plan_id VARCHAR(64) NOT NULL,
    conservative_plan BOOLEAN NOT NULL DEFAULT FALSE,
    workflow_version VARCHAR(64) NOT NULL DEFAULT 'sequential-v2',
    agent_set_version VARCHAR(64) NOT NULL DEFAULT 'market-agent-set-v2',
    stage_records_complete BOOLEAN NOT NULL DEFAULT TRUE,
    legacy_run BOOLEAN NOT NULL DEFAULT FALSE,
    safety_decision_json TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_market_agent_run_created
    ON market_agent_run (created_at DESC);

ALTER TABLE market_agent_run ADD COLUMN IF NOT EXISTS workflow_version VARCHAR(64) NOT NULL DEFAULT 'sequential-v2';
ALTER TABLE market_agent_run ADD COLUMN IF NOT EXISTS agent_set_version VARCHAR(64) NOT NULL DEFAULT 'market-agent-set-v2';
ALTER TABLE market_agent_run ADD COLUMN IF NOT EXISTS stage_records_complete BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE market_agent_run ADD COLUMN IF NOT EXISTS legacy_run BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE IF NOT EXISTS market_agent_decision (
    id BIGSERIAL PRIMARY KEY,
    decision_id VARCHAR(64) NOT NULL UNIQUE,
    run_id VARCHAR(64) NOT NULL,
    agent_type VARCHAR(64) NOT NULL,
    snapshot_id VARCHAR(64) NOT NULL,
    generated_at TIMESTAMPTZ NOT NULL,
    valid_until TIMESTAMPTZ NOT NULL,
    confidence DOUBLE PRECISION NOT NULL,
    evidence_refs_json TEXT NOT NULL,
    rule_version VARCHAR(64) NOT NULL,
    model_provider VARCHAR(32) NOT NULL,
    model_version VARCHAR(128) NOT NULL,
    candidate_actions_json TEXT NOT NULL,
    risk_level VARCHAR(16) NOT NULL,
    requires_human_approval BOOLEAN NOT NULL,
    explanation TEXT NOT NULL,
    fallback_used BOOLEAN NOT NULL DEFAULT FALSE,
    stage_status VARCHAR(32) NOT NULL DEFAULT 'SUCCESS',
    retry_count INTEGER NOT NULL DEFAULT 0,
    error_category VARCHAR(32) NOT NULL DEFAULT 'NONE',
    provider_status_code INTEGER,
    provider_error_code VARCHAR(64),
    provider_error_message VARCHAR(512),
    input_version TEXT NOT NULL DEFAULT '',
    output_version VARCHAR(128) NOT NULL DEFAULT '',
    next_stage_allowed BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_market_agent_decision_run
    ON market_agent_decision (run_id, created_at ASC);

ALTER TABLE market_agent_decision ADD COLUMN IF NOT EXISTS stage_status VARCHAR(32) NOT NULL DEFAULT 'SUCCESS';
ALTER TABLE market_agent_decision ADD COLUMN IF NOT EXISTS retry_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE market_agent_decision ADD COLUMN IF NOT EXISTS error_category VARCHAR(32) NOT NULL DEFAULT 'NONE';
ALTER TABLE market_agent_decision ADD COLUMN IF NOT EXISTS provider_status_code INTEGER;
ALTER TABLE market_agent_decision ADD COLUMN IF NOT EXISTS provider_error_code VARCHAR(64);
ALTER TABLE market_agent_decision ADD COLUMN IF NOT EXISTS provider_error_message VARCHAR(512);
ALTER TABLE market_agent_decision ADD COLUMN IF NOT EXISTS input_version TEXT NOT NULL DEFAULT '';
ALTER TABLE market_agent_decision ADD COLUMN IF NOT EXISTS output_version VARCHAR(128) NOT NULL DEFAULT '';
ALTER TABLE market_agent_decision ADD COLUMN IF NOT EXISTS next_stage_allowed BOOLEAN NOT NULL DEFAULT TRUE;

UPDATE market_agent_run
SET workflow_version = 'legacy',
    agent_set_version = 'legacy',
    stage_records_complete = FALSE,
    legacy_run = TRUE
WHERE NOT EXISTS (
    SELECT 1
    FROM market_agent_decision
    WHERE market_agent_decision.run_id = market_agent_run.run_id
      AND market_agent_decision.output_version <> ''
);

CREATE TABLE IF NOT EXISTS market_bid (
    id BIGSERIAL PRIMARY KEY,
    bid_id VARCHAR(64) NOT NULL UNIQUE,
    market_type VARCHAR(32) NOT NULL,
    aggregate_object VARCHAR(64) NOT NULL,
    interval_start TIMESTAMPTZ NOT NULL,
    interval_end TIMESTAMPTZ NOT NULL,
    available_capacity DOUBLE PRECISION NOT NULL,
    total_quantity DOUBLE PRECISION NOT NULL,
    input_snapshot_id VARCHAR(64) NOT NULL,
    bid_version VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    valid_until TIMESTAMPTZ NOT NULL,
    test_data BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_market_bid_created
    ON market_bid (created_at DESC);

CREATE TABLE IF NOT EXISTS market_bid_segment (
    id BIGSERIAL PRIMARY KEY,
    bid_id VARCHAR(64) NOT NULL,
    segment_order INTEGER NOT NULL,
    quantity DOUBLE PRECISION NOT NULL,
    price NUMERIC(18, 6) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_market_bid_segment_bid
    ON market_bid_segment (bid_id, segment_order ASC);

CREATE TABLE IF NOT EXISTS market_clearing_result (
    id BIGSERIAL PRIMARY KEY,
    clearing_id VARCHAR(64) NOT NULL UNIQUE,
    bid_id VARCHAR(64) NOT NULL,
    market_type VARCHAR(32) NOT NULL,
    interval_start TIMESTAMPTZ NOT NULL,
    interval_end TIMESTAMPTZ NOT NULL,
    cleared_power DOUBLE PRECISION NOT NULL,
    cleared_price NUMERIC(18, 6) NOT NULL,
    ancillary_service_power DOUBLE PRECISION NOT NULL,
    external_trade_id VARCHAR(64) NOT NULL,
    test_data BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_market_clearing_created
    ON market_clearing_result (created_at DESC);

CREATE TABLE IF NOT EXISTS market_news (
    id BIGSERIAL PRIMARY KEY,
    news_id VARCHAR(64) NOT NULL UNIQUE,
    title VARCHAR(256) NOT NULL,
    published_at TIMESTAMPTZ NOT NULL,
    source VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_market_news_published
    ON market_news (published_at DESC);

CREATE TABLE IF NOT EXISTS market_metering_record (
    id BIGSERIAL PRIMARY KEY,
    metering_id VARCHAR(64) NOT NULL UNIQUE,
    settlement_id VARCHAR(64) NOT NULL,
    plan_id VARCHAR(64) NOT NULL,
    station_id VARCHAR(64) NOT NULL,
    interval_start TIMESTAMPTZ NOT NULL,
    interval_end TIMESTAMPTZ NOT NULL,
    average_power DOUBLE PRECISION NOT NULL,
    energy_kwh NUMERIC(18, 6) NOT NULL,
    sample_count INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_market_metering_settlement
    ON market_metering_record (settlement_id, station_id, interval_start ASC);

CREATE TABLE IF NOT EXISTS settlement_result (
    id BIGSERIAL PRIMARY KEY,
    settlement_id VARCHAR(64) NOT NULL UNIQUE,
    plan_id VARCHAR(64) NOT NULL,
    clearing_id VARCHAR(64) NOT NULL,
    total_energy_revenue NUMERIC(18, 6) NOT NULL,
    grid_import_cost NUMERIC(18, 6) NOT NULL,
    grid_export_revenue NUMERIC(18, 6) NOT NULL,
    deviation_cost NUMERIC(18, 6) NOT NULL,
    storage_cycle_cost NUMERIC(18, 6) NOT NULL,
    demand_response_compensation NUMERIC(18, 6) NOT NULL,
    renewable_curtailment_cost NUMERIC(18, 6) NOT NULL,
    ancillary_service_revenue NUMERIC(18, 6) NOT NULL,
    resource_revenue_allocation_json TEXT NOT NULL,
    net_revenue NUMERIC(18, 6) NOT NULL,
    test_data BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_settlement_result_created
    ON settlement_result (created_at DESC);

CREATE TABLE IF NOT EXISTS solver_performance_audit (
    id BIGSERIAL PRIMARY KEY,
    experiment_run_id VARCHAR(64), optimization_round INTEGER NOT NULL, snapshot_id VARCHAR(64), plan_id VARCHAR(64),
    resource_count INTEGER NOT NULL, time_period_count INTEGER NOT NULL, continuous_variable_count INTEGER NOT NULL,
    integer_variable_count INTEGER NOT NULL, constraint_count INTEGER NOT NULL, status VARCHAR(64) NOT NULL,
    solve_time_ms BIGINT NOT NULL, objective_value DOUBLE PRECISION, optimality_gap DOUBLE PRECISION,
    timed_out BOOLEAN NOT NULL, fallback BOOLEAN NOT NULL, created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_solver_performance_audit_created
    ON solver_performance_audit (created_at DESC);

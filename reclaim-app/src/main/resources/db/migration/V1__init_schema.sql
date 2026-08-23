-- V1__init_schema.sql: Initial schema for RECLAIM

CREATE TABLE IF NOT EXISTS raw_event (
    id UUID PRIMARY KEY,
    razorpay_event_id TEXT UNIQUE NOT NULL,
    event_type TEXT NOT NULL,
    signature TEXT NOT NULL,
    payload JSONB NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    processed BOOLEAN NOT NULL DEFAULT FALSE,
    processing_attempts INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS recovery_case (
    id UUID PRIMARY KEY,
    subscription_id TEXT NOT NULL,
    customer_id TEXT NOT NULL,
    merchant_ref TEXT,
    amount_paise BIGINT NOT NULL,
    currency TEXT NOT NULL DEFAULT 'INR',
    state TEXT NOT NULL,
    failure_code TEXT NOT NULL,
    failure_reason_raw TEXT,
    opened_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    closed_at TIMESTAMPTZ,
    outcome TEXT,
    recovered_paise BIGINT NOT NULL DEFAULT 0,
    attempt_count INT NOT NULL DEFAULT 0,
    contact_count INT NOT NULL DEFAULT 0,
    cost_incurred_paise BIGINT NOT NULL DEFAULT 0,
    run_id UUID,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_recovery_case_subscription ON recovery_case(subscription_id);
CREATE INDEX IF NOT EXISTS idx_recovery_case_state ON recovery_case(state);

CREATE TABLE IF NOT EXISTS agent_decision (
    id UUID PRIMARY KEY,
    case_id UUID NOT NULL REFERENCES recovery_case(id),
    run_id UUID,
    trigger_event_id UUID,
    model TEXT,
    prompt_tokens INT,
    completion_tokens INT,
    diagnosis TEXT,
    confidence NUMERIC,
    reasoning TEXT,
    proposed_plan JSONB,
    tool_calls JSONB,
    latency_ms INT,
    degraded_mode BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS policy_verdict (
    id UUID PRIMARY KEY,
    case_id UUID NOT NULL REFERENCES recovery_case(id),
    decision_id UUID REFERENCES agent_decision(id),
    proposed_action JSONB NOT NULL,
    verdict TEXT NOT NULL,
    rules_evaluated JSONB,
    violated_rule TEXT,
    final_action JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS recovery_action (
    id UUID PRIMARY KEY,
    case_id UUID NOT NULL REFERENCES recovery_case(id),
    action_type TEXT NOT NULL,
    idempotency_key TEXT UNIQUE NOT NULL,
    scheduled_for TIMESTAMPTZ NOT NULL,
    executed_at TIMESTAMPTZ,
    status TEXT NOT NULL,
    razorpay_ref TEXT,
    request JSONB,
    response JSONB,
    cost_paise BIGINT NOT NULL DEFAULT 0,
    error TEXT
);

CREATE INDEX IF NOT EXISTS idx_recovery_action_due ON recovery_action(status, scheduled_for);

CREATE TABLE IF NOT EXISTS audit_entry (
    seq BIGSERIAL PRIMARY KEY,
    case_id UUID NOT NULL REFERENCES recovery_case(id),
    run_id UUID,
    entry_type TEXT NOT NULL,
    actor TEXT NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    prev_hash TEXT NOT NULL,
    entry_hash TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_audit_entry_case ON audit_entry(case_id);

-- Tamper-evident append-only protection trigger for audit_entry
CREATE OR REPLACE FUNCTION protect_audit_entry()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Audit ledger is strictly append-only. Modifying or deleting audit records is forbidden.';
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_audit_entry_immutable ON audit_entry;
CREATE TRIGGER trg_audit_entry_immutable
BEFORE UPDATE OR DELETE ON audit_entry
FOR EACH ROW EXECUTE FUNCTION protect_audit_entry();

CREATE TABLE IF NOT EXISTS human_task (
    id UUID PRIMARY KEY,
    case_id UUID NOT NULL REFERENCES recovery_case(id),
    reason TEXT NOT NULL,
    priority TEXT NOT NULL,
    context JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    resolved_at TIMESTAMPTZ,
    resolution TEXT
);

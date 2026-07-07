ALTER TABLE expense_document
    ADD COLUMN token_usage INTEGER CHECK (token_usage IS NULL OR token_usage >= 0),
    ADD COLUMN extraction_latency_ms BIGINT CHECK (extraction_latency_ms IS NULL OR extraction_latency_ms >= 0),
    ADD COLUMN extractor_mode VARCHAR(32);

CREATE TABLE expense_model_call (
    id UUID PRIMARY KEY,
    case_id UUID REFERENCES expense_case(id) ON DELETE SET NULL,
    run_id UUID REFERENCES expense_agent_run(id) ON DELETE SET NULL,
    step_name VARCHAR(64) NOT NULL,
    model_name VARCHAR(128) NOT NULL,
    prompt_version VARCHAR(64) NOT NULL,
    prompt_hash CHAR(64) NOT NULL,
    input_hash CHAR(64) NOT NULL,
    output_hash CHAR(64),
    prompt_tokens INTEGER NOT NULL DEFAULT 0 CHECK (prompt_tokens >= 0),
    completion_tokens INTEGER NOT NULL DEFAULT 0 CHECK (completion_tokens >= 0),
    total_tokens INTEGER NOT NULL DEFAULT 0 CHECK (total_tokens >= 0),
    latency_ms BIGINT NOT NULL CHECK (latency_ms >= 0),
    retry_count INTEGER NOT NULL DEFAULT 0 CHECK (retry_count >= 0),
    status VARCHAR(16) NOT NULL CHECK (status IN ('SUCCEEDED', 'FAILED')),
    error_code VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_expense_model_call_created
    ON expense_model_call (created_at DESC);
CREATE INDEX idx_expense_model_call_case
    ON expense_model_call (case_id, created_at DESC);

CREATE TABLE expense_review_report (
    id UUID PRIMARY KEY,
    case_id UUID NOT NULL REFERENCES expense_case(id) ON DELETE CASCADE,
    summary TEXT NOT NULL,
    risk_explanation JSONB NOT NULL DEFAULT '[]'::jsonb,
    policy_citations JSONB NOT NULL DEFAULT '[]'::jsonb,
    human_review_hints JSONB NOT NULL DEFAULT '[]'::jsonb,
    limitations JSONB NOT NULL DEFAULT '[]'::jsonb,
    model_name VARCHAR(128) NOT NULL,
    prompt_version VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_expense_review_report_case_created
    ON expense_review_report (case_id, created_at DESC);

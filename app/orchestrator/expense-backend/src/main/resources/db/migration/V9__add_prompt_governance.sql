CREATE TABLE expense_prompt_template (
    id UUID PRIMARY KEY,
    prompt_key VARCHAR(80) NOT NULL,
    version VARCHAR(80) NOT NULL,
    name VARCHAR(160) NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    content TEXT NOT NULL,
    variable_schema JSONB NOT NULL DEFAULT '{}'::jsonb,
    model_name VARCHAR(128) NOT NULL,
    temperature NUMERIC(4, 3) NOT NULL DEFAULT 0 CHECK (temperature >= 0 AND temperature <= 2),
    max_tokens INTEGER NOT NULL DEFAULT 2048 CHECK (max_tokens > 0),
    status VARCHAR(24) NOT NULL CHECK (
        status IN ('DRAFT', 'IN_REVIEW', 'APPROVED', 'ACTIVE', 'REJECTED', 'DEPRECATED')
    ),
    prompt_hash CHAR(64) NOT NULL,
    created_by VARCHAR(128) NOT NULL,
    updated_by VARCHAR(128) NOT NULL,
    approved_by VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    approved_at TIMESTAMPTZ,
    activated_at TIMESTAMPTZ,
    replaced_version VARCHAR(80),
    UNIQUE (prompt_key, version)
);

CREATE UNIQUE INDEX uq_expense_prompt_active
    ON expense_prompt_template (prompt_key)
    WHERE status = 'ACTIVE';

CREATE INDEX idx_expense_prompt_template_key_status
    ON expense_prompt_template (prompt_key, status, updated_at DESC);

CREATE TABLE expense_prompt_change_request (
    id UUID PRIMARY KEY,
    prompt_template_id UUID NOT NULL REFERENCES expense_prompt_template(id) ON DELETE CASCADE,
    request_type VARCHAR(24) NOT NULL CHECK (
        request_type IN ('CREATE', 'UPDATE', 'ACTIVATE', 'ROLLBACK', 'DEPRECATE')
    ),
    status VARCHAR(24) NOT NULL CHECK (
        status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')
    ),
    diff_summary TEXT NOT NULL DEFAULT '',
    risk_level VARCHAR(16) NOT NULL DEFAULT 'LOW' CHECK (risk_level IN ('LOW', 'MEDIUM', 'HIGH')),
    evaluation_report JSONB NOT NULL DEFAULT '{}'::jsonb,
    review_comment TEXT NOT NULL DEFAULT '',
    submitted_by VARCHAR(128) NOT NULL,
    reviewed_by VARCHAR(128),
    submitted_at TIMESTAMPTZ NOT NULL,
    reviewed_at TIMESTAMPTZ
);

CREATE INDEX idx_expense_prompt_change_template
    ON expense_prompt_change_request (prompt_template_id, submitted_at DESC);
CREATE INDEX idx_expense_prompt_change_status
    ON expense_prompt_change_request (status, submitted_at DESC);

CREATE TABLE expense_prompt_audit_log (
    id UUID PRIMARY KEY,
    prompt_key VARCHAR(80) NOT NULL,
    version VARCHAR(80) NOT NULL,
    action VARCHAR(32) NOT NULL,
    actor_subject VARCHAR(128) NOT NULL,
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    occurred_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_expense_prompt_audit_prompt
    ON expense_prompt_audit_log (prompt_key, version, occurred_at DESC);

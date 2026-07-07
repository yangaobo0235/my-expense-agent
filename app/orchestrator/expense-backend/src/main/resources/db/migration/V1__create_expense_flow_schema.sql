CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE expense_case (
    id UUID PRIMARY KEY,
    case_number VARCHAR(32) NOT NULL UNIQUE,
    owner_subject VARCHAR(128) NOT NULL,
    applicant_name VARCHAR(128) NOT NULL,
    department_code VARCHAR(64) NOT NULL,
    title VARCHAR(256) NOT NULL,
    currency CHAR(3) NOT NULL,
    claimed_amount NUMERIC(19, 2) NOT NULL CHECK (claimed_amount >= 0),
    status VARCHAR(32) NOT NULL CHECK (status IN (
        'DRAFT', 'UPLOADED', 'EXTRACTING', 'EXTRACTED', 'POLICY_CHECKING',
        'RISK_CHECKING', 'WAITING_HUMAN', 'APPROVED', 'REJECTED', 'FAILED'
    )),
    risk_level VARCHAR(16) CHECK (risk_level IN ('LOW', 'MEDIUM', 'HIGH')),
    risk_score INTEGER CHECK (risk_score BETWEEN 0 AND 100),
    failure_stage VARCHAR(64),
    failure_reason VARCHAR(1000),
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_expense_case_owner_created
    ON expense_case (owner_subject, created_at DESC);
CREATE INDEX idx_expense_case_status_updated
    ON expense_case (status, updated_at DESC);

CREATE TABLE expense_document (
    id UUID PRIMARY KEY,
    case_id UUID NOT NULL REFERENCES expense_case(id) ON DELETE CASCADE,
    original_filename VARCHAR(512) NOT NULL,
    content_type VARCHAR(128) NOT NULL,
    file_size BIGINT NOT NULL CHECK (file_size > 0),
    sha256 CHAR(64) NOT NULL,
    object_key VARCHAR(768) NOT NULL UNIQUE,
    document_type VARCHAR(64),
    extraction_confidence NUMERIC(5, 4)
        CHECK (extraction_confidence BETWEEN 0 AND 1),
    extraction_result JSONB NOT NULL DEFAULT '{}'::jsonb,
    validation_errors JSONB NOT NULL DEFAULT '[]'::jsonb,
    model_name VARCHAR(128),
    prompt_version VARCHAR(64),
    raw_response_hash CHAR(64),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE (case_id, sha256)
);

CREATE INDEX idx_expense_document_case ON expense_document (case_id, created_at);

CREATE TABLE expense_item (
    id UUID PRIMARY KEY,
    case_id UUID NOT NULL REFERENCES expense_case(id) ON DELETE CASCADE,
    document_id UUID REFERENCES expense_document(id) ON DELETE CASCADE,
    item_order INTEGER NOT NULL CHECK (item_order >= 0),
    category VARCHAR(64),
    description VARCHAR(512) NOT NULL,
    quantity NUMERIC(19, 4) NOT NULL DEFAULT 1 CHECK (quantity > 0),
    unit_price NUMERIC(19, 2) NOT NULL CHECK (unit_price >= 0),
    amount NUMERIC(19, 2) NOT NULL CHECK (amount >= 0),
    currency CHAR(3) NOT NULL,
    expense_date DATE,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (case_id, document_id, item_order)
);

CREATE INDEX idx_expense_item_case ON expense_item (case_id, item_order);

CREATE TABLE expense_policy (
    id UUID PRIMARY KEY,
    policy_code VARCHAR(64) NOT NULL,
    name VARCHAR(256) NOT NULL,
    category VARCHAR(64) NOT NULL,
    region VARCHAR(64) NOT NULL,
    employee_grade VARCHAR(64) NOT NULL,
    version VARCHAR(32) NOT NULL,
    effective_from DATE NOT NULL,
    effective_to DATE,
    status VARCHAR(16) NOT NULL CHECK (status IN ('DRAFT', 'ACTIVE', 'RETIRED')),
    source_uri VARCHAR(1000),
    content_hash CHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CHECK (effective_to IS NULL OR effective_to >= effective_from),
    UNIQUE (policy_code, version)
);

CREATE INDEX idx_expense_policy_lookup
    ON expense_policy (category, region, employee_grade, status, effective_from);

CREATE TABLE expense_policy_chunk (
    id UUID PRIMARY KEY,
    policy_id UUID NOT NULL REFERENCES expense_policy(id) ON DELETE CASCADE,
    chunk_index INTEGER NOT NULL CHECK (chunk_index >= 0),
    section VARCHAR(256) NOT NULL,
    content TEXT NOT NULL,
    token_count INTEGER CHECK (token_count > 0),
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    embedding vector(1024),
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (policy_id, chunk_index)
);

CREATE INDEX idx_expense_policy_chunk_policy ON expense_policy_chunk (policy_id);

CREATE TABLE expense_agent_run (
    id UUID PRIMARY KEY,
    case_id UUID NOT NULL REFERENCES expense_case(id) ON DELETE CASCADE,
    request_id VARCHAR(128) NOT NULL,
    run_type VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('RUNNING', 'SUCCEEDED', 'FAILED', 'CANCELLED')),
    model_name VARCHAR(128),
    prompt_version VARCHAR(64),
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    error_code VARCHAR(64),
    error_message VARCHAR(1000),
    trace_id VARCHAR(64),
    UNIQUE (case_id, request_id)
);

CREATE INDEX idx_expense_agent_run_case ON expense_agent_run (case_id, started_at DESC);

CREATE TABLE expense_agent_step (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES expense_agent_run(id) ON DELETE CASCADE,
    case_id UUID NOT NULL REFERENCES expense_case(id) ON DELETE CASCADE,
    step_name VARCHAR(64) NOT NULL,
    attempt INTEGER NOT NULL CHECK (attempt > 0),
    status VARCHAR(16) NOT NULL CHECK (status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED', 'SKIPPED')),
    input_hash CHAR(64),
    output_data JSONB NOT NULL DEFAULT '{}'::jsonb,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    next_retry_at TIMESTAMPTZ,
    error_code VARCHAR(64),
    error_message VARCHAR(1000),
    UNIQUE (run_id, step_name, attempt)
);

CREATE INDEX idx_expense_agent_step_resume
    ON expense_agent_step (case_id, step_name, status);

CREATE TABLE expense_tool_call (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES expense_agent_run(id) ON DELETE CASCADE,
    step_id UUID REFERENCES expense_agent_step(id) ON DELETE SET NULL,
    case_id UUID NOT NULL REFERENCES expense_case(id) ON DELETE CASCADE,
    request_id VARCHAR(128) NOT NULL,
    tool_name VARCHAR(128) NOT NULL,
    write_operation BOOLEAN NOT NULL,
    input_hash CHAR(64) NOT NULL,
    output_hash CHAR(64),
    status VARCHAR(16) NOT NULL CHECK (status IN ('RUNNING', 'SUCCEEDED', 'FAILED')),
    duration_ms BIGINT CHECK (duration_ms >= 0),
    error_code VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (tool_name, request_id)
);

CREATE INDEX idx_expense_tool_call_case ON expense_tool_call (case_id, created_at DESC);

CREATE TABLE expense_review_task (
    id UUID PRIMARY KEY,
    case_id UUID NOT NULL REFERENCES expense_case(id) ON DELETE CASCADE,
    status VARCHAR(24) NOT NULL CHECK (status IN ('OPEN', 'ASSIGNED', 'APPROVED', 'REJECTED', 'MORE_INFO')),
    assignee_subject VARCHAR(128),
    reason_codes JSONB NOT NULL DEFAULT '[]'::jsonb,
    due_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX uq_expense_review_task_open_case
    ON expense_review_task (case_id)
    WHERE status IN ('OPEN', 'ASSIGNED', 'MORE_INFO');

CREATE TABLE expense_decision (
    id UUID PRIMARY KEY,
    case_id UUID NOT NULL UNIQUE REFERENCES expense_case(id) ON DELETE CASCADE,
    decision VARCHAR(16) NOT NULL CHECK (decision IN ('APPROVED', 'REJECTED')),
    total_amount NUMERIC(19, 2) NOT NULL CHECK (total_amount >= 0),
    approved_amount NUMERIC(19, 2) NOT NULL CHECK (approved_amount >= 0),
    currency CHAR(3) NOT NULL,
    risk_level VARCHAR(16) NOT NULL CHECK (risk_level IN ('LOW', 'MEDIUM', 'HIGH')),
    risk_score INTEGER NOT NULL CHECK (risk_score BETWEEN 0 AND 100),
    policy_findings JSONB NOT NULL DEFAULT '[]'::jsonb,
    risk_findings JSONB NOT NULL DEFAULT '[]'::jsonb,
    evidence JSONB NOT NULL DEFAULT '{}'::jsonb,
    model_name VARCHAR(128),
    prompt_version VARCHAR(64),
    decided_by VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CHECK (approved_amount <= total_amount),
    CHECK (
        (decision = 'REJECTED' AND approved_amount = 0)
        OR decision = 'APPROVED'
    )
);

CREATE TABLE expense_audit_log (
    id UUID PRIMARY KEY,
    case_id UUID REFERENCES expense_case(id) ON DELETE SET NULL,
    actor_subject VARCHAR(128) NOT NULL,
    actor_type VARCHAR(16) NOT NULL CHECK (actor_type IN ('USER', 'SERVICE', 'SYSTEM')),
    action VARCHAR(128) NOT NULL,
    resource_type VARCHAR(64) NOT NULL,
    resource_id VARCHAR(128),
    request_id VARCHAR(128),
    before_data JSONB,
    after_data JSONB,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    occurred_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_expense_audit_case ON expense_audit_log (case_id, occurred_at DESC);
CREATE INDEX idx_expense_audit_request ON expense_audit_log (request_id);

CREATE TABLE expense_eval_case (
    id UUID PRIMARY KEY,
    dataset_version VARCHAR(64) NOT NULL,
    case_key VARCHAR(128) NOT NULL,
    category VARCHAR(64) NOT NULL,
    input_data JSONB NOT NULL,
    expected_data JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (dataset_version, case_key)
);

CREATE TABLE expense_eval_result (
    id UUID PRIMARY KEY,
    eval_case_id UUID NOT NULL REFERENCES expense_eval_case(id) ON DELETE CASCADE,
    run_version VARCHAR(64) NOT NULL,
    model_name VARCHAR(128) NOT NULL,
    prompt_version VARCHAR(64) NOT NULL,
    policy_version VARCHAR(64) NOT NULL,
    actual_data JSONB NOT NULL,
    metrics JSONB NOT NULL,
    passed BOOLEAN NOT NULL,
    duration_ms BIGINT CHECK (duration_ms >= 0),
    token_usage INTEGER CHECK (token_usage >= 0),
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (eval_case_id, run_version)
);

ALTER TABLE expense_case DROP CONSTRAINT IF EXISTS expense_case_status_check;
ALTER TABLE expense_case ADD CONSTRAINT expense_case_status_check CHECK (status IN (
    'DRAFT', 'UPLOADED', 'EXTRACTING', 'EXTRACTED', 'POLICY_CHECKING',
    'RISK_CHECKING', 'WAITING_MORE_INFO', 'WAITING_HUMAN',
    'APPROVED', 'REJECTED', 'FAILED'
));

ALTER TABLE expense_agent_run
    ADD COLUMN command_type VARCHAR(32) NOT NULL DEFAULT 'REVIEW',
    ADD COLUMN document_version INTEGER NOT NULL DEFAULT 1 CHECK (document_version > 0),
    ADD COLUMN previous_run_id UUID REFERENCES expense_agent_run(id) ON DELETE SET NULL,
    ADD COLUMN reopen_reason VARCHAR(1000),
    ADD COLUMN route_action VARCHAR(64),
    ADD COLUMN waiting_reason VARCHAR(128);

CREATE INDEX idx_expense_agent_run_document_version
    ON expense_agent_run (case_id, document_version, started_at DESC);
CREATE INDEX idx_expense_agent_run_previous
    ON expense_agent_run (previous_run_id);

ALTER TABLE expense_review_task
    RENAME COLUMN debate_assist_enabled TO summary_required;

ALTER TABLE expense_prompt_template DROP CONSTRAINT IF EXISTS expense_prompt_template_status_check;
UPDATE expense_prompt_template SET status = 'EVALUATING' WHERE status = 'IN_REVIEW';
UPDATE expense_prompt_template SET status = 'DRAFT' WHERE status = 'REJECTED';
UPDATE expense_prompt_template SET status = 'RETIRED' WHERE status = 'DEPRECATED';
ALTER TABLE expense_prompt_template ADD CONSTRAINT expense_prompt_template_status_check CHECK (
    status IN ('DRAFT', 'SUBMITTED', 'EVALUATING', 'APPROVED', 'ACTIVE', 'RETIRED')
);

CREATE TABLE expense_document_version (
    case_id UUID NOT NULL REFERENCES expense_case(id) ON DELETE CASCADE,
    version INTEGER NOT NULL CHECK (version > 0),
    document_id UUID NOT NULL UNIQUE REFERENCES expense_document(id) ON DELETE CASCADE,
    sha256 CHAR(64) NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    uploaded_by VARCHAR(128) NOT NULL,
    replaces_version INTEGER,
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (case_id, version)
);

INSERT INTO expense_document_version (
    case_id, version, document_id, sha256, source_type, uploaded_by, replaces_version, created_at
)
SELECT document.case_id,
       ROW_NUMBER() OVER (PARTITION BY document.case_id ORDER BY document.created_at, document.id)::INTEGER,
       document.id,
       document.sha256,
       'INITIAL_UPLOAD',
       expense_case.owner_subject,
       CASE WHEN ROW_NUMBER() OVER (PARTITION BY document.case_id ORDER BY document.created_at, document.id) = 1
            THEN NULL
            ELSE (ROW_NUMBER() OVER (PARTITION BY document.case_id ORDER BY document.created_at, document.id) - 1)::INTEGER
       END,
       document.created_at
FROM expense_document document
JOIN expense_case ON expense_case.id = document.case_id;

CREATE TABLE expense_more_info_task (
    id UUID PRIMARY KEY,
    case_id UUID NOT NULL REFERENCES expense_case(id) ON DELETE CASCADE,
    run_id UUID NOT NULL REFERENCES expense_agent_run(id) ON DELETE CASCADE,
    required_materials JSONB NOT NULL DEFAULT '[]'::jsonb,
    reason_codes JSONB NOT NULL DEFAULT '[]'::jsonb,
    status VARCHAR(16) NOT NULL CHECK (status IN ('OPEN', 'SUBMITTED', 'COMPLETED', 'CANCELLED')),
    requested_by VARCHAR(128) NOT NULL,
    due_at TIMESTAMPTZ,
    submitted_document_version INTEGER,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX uq_expense_more_info_task_open_case
    ON expense_more_info_task (case_id) WHERE status = 'OPEN';

CREATE TABLE expense_extraction_attempt (
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL REFERENCES expense_document(id) ON DELETE CASCADE,
    attempt_no INTEGER NOT NULL CHECK (attempt_no IN (1, 2)),
    attempt_type VARCHAR(16) NOT NULL CHECK (attempt_type IN ('ORIGINAL', 'REPAIR')),
    prompt_version VARCHAR(64) NOT NULL,
    model_name VARCHAR(128) NOT NULL,
    validation_errors JSONB NOT NULL DEFAULT '[]'::jsonb,
    output_hash CHAR(64),
    token_usage INTEGER NOT NULL DEFAULT 0 CHECK (token_usage >= 0),
    latency_ms BIGINT NOT NULL DEFAULT 0 CHECK (latency_ms >= 0),
    network_retry_count INTEGER NOT NULL DEFAULT 0 CHECK (network_retry_count >= 0),
    status VARCHAR(32) NOT NULL CHECK (status IN ('SUCCEEDED', 'VALIDATION_FAILED', 'FAILED')),
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (document_id, attempt_no)
);

ALTER TABLE expense_tool_call DROP CONSTRAINT IF EXISTS expense_tool_call_status_check;
UPDATE expense_tool_call SET status = 'FAILED_MANUAL' WHERE status = 'FAILED';
ALTER TABLE expense_tool_call ADD CONSTRAINT expense_tool_call_status_check CHECK (status IN (
    'PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED_RETRYABLE', 'FAILED_MANUAL', 'COMPENSATED'
));

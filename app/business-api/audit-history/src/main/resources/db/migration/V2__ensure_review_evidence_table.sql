CREATE TABLE IF NOT EXISTS expense_audit_review_evidence (
    evidence_id UUID PRIMARY KEY,
    request_id VARCHAR(128) NOT NULL UNIQUE,
    case_id UUID NOT NULL,
    evidence_type VARCHAR(80) NOT NULL,
    content_hash VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_expense_audit_review_evidence_case
    ON expense_audit_review_evidence (case_id, created_at DESC);

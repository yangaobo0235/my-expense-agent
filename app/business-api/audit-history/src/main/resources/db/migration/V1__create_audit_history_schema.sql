CREATE TABLE campus_fund_reimbursement_history_item (
    history_id UUID PRIMARY KEY,
    request_id VARCHAR(128) NOT NULL UNIQUE,
    case_id UUID NOT NULL,
    applicant_id VARCHAR(80) NOT NULL,
    seller_name VARCHAR(200) NOT NULL,
    amount NUMERIC(18, 2) NOT NULL CHECK (amount >= 0),
    currency VARCHAR(8) NOT NULL,
    expense_date DATE NOT NULL,
    document_sha256 VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (case_id, document_sha256)
);

CREATE INDEX idx_campus_fund_history_applicant
    ON campus_fund_reimbursement_history_item (applicant_id, expense_date DESC);

CREATE INDEX idx_campus_fund_history_document
    ON campus_fund_reimbursement_history_item (document_sha256);

CREATE TABLE campus_fund_review_evidence (
    evidence_id UUID PRIMARY KEY,
    request_id VARCHAR(128) NOT NULL UNIQUE,
    case_id UUID NOT NULL,
    evidence_type VARCHAR(80) NOT NULL,
    content_hash VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_campus_fund_review_evidence_case
    ON campus_fund_review_evidence (case_id, created_at DESC);

INSERT INTO campus_fund_reimbursement_history_item (
    history_id, request_id, case_id, applicant_id, seller_name,
    amount, currency, expense_date, document_sha256, created_at
) VALUES (
    '30000000-0000-0000-0000-000000000001',
    'campus-history-seed-1',
    '10000000-0000-0000-0000-000000000001',
    'student01',
    '南京青奥酒店',
    600.00,
    'CNY',
    DATE '2026-05-20',
    '43797675f1ba314b4233d7e3b64d02e773f698fa0d09e8c2b04dc0afd269b2e4',
    TIMESTAMPTZ '2026-05-20 12:00:00+08'
)
ON CONFLICT (case_id, document_sha256) DO NOTHING;

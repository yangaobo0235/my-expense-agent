CREATE TABLE expense_audit_history_item (
    case_id UUID PRIMARY KEY,
    employee_id VARCHAR(80) NOT NULL,
    seller_name VARCHAR(200) NOT NULL,
    amount NUMERIC(18, 2) NOT NULL CHECK (amount >= 0),
    currency VARCHAR(8) NOT NULL,
    expense_date DATE NOT NULL,
    document_sha256 VARCHAR(128) NOT NULL
);

CREATE INDEX idx_expense_audit_history_employee
    ON expense_audit_history_item (employee_id, expense_date DESC);

CREATE INDEX idx_expense_audit_history_document
    ON expense_audit_history_item (document_sha256);

CREATE TABLE expense_audit_review_evidence (
    evidence_id UUID PRIMARY KEY,
    request_id VARCHAR(128) NOT NULL UNIQUE,
    case_id UUID NOT NULL,
    evidence_type VARCHAR(80) NOT NULL,
    content_hash VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_expense_audit_review_evidence_case
    ON expense_audit_review_evidence (case_id, created_at DESC);

INSERT INTO expense_audit_history_item (
    case_id, employee_id, seller_name, amount, currency, expense_date, document_sha256
) VALUES (
    '10000000-0000-0000-0000-000000000001',
    'employee01',
    '测试酒店',
    600.00,
    'CNY',
    DATE '2026-05-20',
    'abc123'
)
ON CONFLICT (case_id) DO NOTHING;

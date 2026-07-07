CREATE TABLE expense_business_reimbursement (
    reimbursement_id UUID PRIMARY KEY,
    request_id VARCHAR(128) NOT NULL UNIQUE,
    case_id UUID NOT NULL,
    amount NUMERIC(18, 2) NOT NULL CHECK (amount >= 0),
    currency VARCHAR(8) NOT NULL,
    status VARCHAR(32) NOT NULL,
    submitted_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_expense_business_reimbursement_case
    ON expense_business_reimbursement (case_id, submitted_at DESC);

CREATE TABLE expense_business_payment (
    payment_id UUID PRIMARY KEY,
    request_id VARCHAR(128) NOT NULL UNIQUE,
    reimbursement_id UUID NOT NULL REFERENCES expense_business_reimbursement(reimbursement_id),
    amount NUMERIC(18, 2) NOT NULL CHECK (amount >= 0),
    currency VARCHAR(8) NOT NULL,
    status VARCHAR(32) NOT NULL,
    paid_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_expense_business_payment_reimbursement
    ON expense_business_payment (reimbursement_id, paid_at DESC);

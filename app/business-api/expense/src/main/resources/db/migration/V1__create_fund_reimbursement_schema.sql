CREATE TABLE campus_fund_reimbursement_record (
    reimbursement_id UUID PRIMARY KEY,
    request_id VARCHAR(128) NOT NULL UNIQUE,
    case_id UUID NOT NULL,
    amount NUMERIC(18, 2) NOT NULL CHECK (amount >= 0),
    currency VARCHAR(8) NOT NULL,
    status VARCHAR(32) NOT NULL,
    submitted_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_campus_fund_reimbursement_case
    ON campus_fund_reimbursement_record (case_id, submitted_at DESC);

CREATE TABLE campus_fund_posting_record (
    posting_id UUID PRIMARY KEY,
    request_id VARCHAR(128) NOT NULL UNIQUE,
    reimbursement_id UUID NOT NULL
        REFERENCES campus_fund_reimbursement_record(reimbursement_id),
    amount NUMERIC(18, 2) NOT NULL CHECK (amount >= 0),
    currency VARCHAR(8) NOT NULL,
    status VARCHAR(32) NOT NULL,
    posted_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_campus_fund_posting_reimbursement
    ON campus_fund_posting_record (reimbursement_id, posted_at DESC);

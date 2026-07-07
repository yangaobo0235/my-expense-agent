CREATE TABLE expense_case_event (
    sequence BIGSERIAL PRIMARY KEY,
    event_id UUID NOT NULL UNIQUE,
    case_id UUID NOT NULL REFERENCES expense_case(id) ON DELETE CASCADE,
    event_type VARCHAR(128) NOT NULL,
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    occurred_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_expense_case_event_replay
    ON expense_case_event (case_id, sequence);

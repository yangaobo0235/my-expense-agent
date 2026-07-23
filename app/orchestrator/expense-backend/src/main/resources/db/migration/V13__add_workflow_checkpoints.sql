CREATE TABLE expense_workflow_checkpoint (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES expense_agent_run(id) ON DELETE CASCADE,
    case_id UUID NOT NULL REFERENCES expense_case(id) ON DELETE CASCADE,
    node_name VARCHAR(64) NOT NULL,
    checkpoint_version INTEGER NOT NULL CHECK (checkpoint_version > 0),
    state_data JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (run_id, node_name, checkpoint_version)
);

CREATE INDEX idx_expense_workflow_checkpoint_resume
    ON expense_workflow_checkpoint (run_id, node_name, checkpoint_version DESC);

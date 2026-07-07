ALTER TABLE expense_tool_call
    ALTER COLUMN run_id DROP NOT NULL,
    ADD COLUMN output_data JSONB NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN approval_reference VARCHAR(128),
    ADD COLUMN actor_subject VARCHAR(128),
    ADD COLUMN completed_at TIMESTAMPTZ;

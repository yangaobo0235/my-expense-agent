ALTER TABLE expense_review_task
    ADD COLUMN routing_action VARCHAR(64),
    ADD COLUMN routing_queue VARCHAR(64),
    ADD COLUMN assignee_role VARCHAR(64),
    ADD COLUMN sla_hours INTEGER,
    ADD COLUMN required_evidence JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN user_facing_message VARCHAR(1000),
    ADD COLUMN fallback_strategy VARCHAR(128),
    ADD COLUMN debate_assist_enabled BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE expense_review_task
SET routing_action = 'MEDIUM_RISK_HUMAN_REVIEW',
    routing_queue = 'STANDARD_REVIEW',
    assignee_role = 'COLLEGE_REVIEWER',
    sla_hours = COALESCE(EXTRACT(EPOCH FROM (due_at - created_at))::INTEGER / 3600, 48),
    user_facing_message = '该经费申请需要学院审核员复核后决定。',
    fallback_strategy = 'HUMAN_REVIEW'
WHERE routing_action IS NULL;

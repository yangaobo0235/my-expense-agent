ALTER TABLE expense_review_task
    ADD COLUMN reviewer_comment VARCHAR(2000);

ALTER TABLE expense_decision
    ADD COLUMN request_id VARCHAR(128);

CREATE UNIQUE INDEX uq_expense_decision_request
    ON expense_decision (request_id)
    WHERE request_id IS NOT NULL;

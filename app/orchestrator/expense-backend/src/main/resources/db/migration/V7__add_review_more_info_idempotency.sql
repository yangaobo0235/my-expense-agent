CREATE UNIQUE INDEX uq_my_expense_agent_audit_more_info_request
    ON my_expense_agent_audit_log (request_id)
    WHERE action = 'REVIEW_MORE_INFO_REQUESTED' AND request_id IS NOT NULL;

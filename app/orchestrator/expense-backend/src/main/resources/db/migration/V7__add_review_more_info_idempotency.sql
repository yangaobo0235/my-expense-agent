CREATE UNIQUE INDEX uq_expense_audit_more_info_request
    ON expense_audit_log (request_id)
    WHERE action = 'REVIEW_MORE_INFO_REQUESTED' AND request_id IS NOT NULL;

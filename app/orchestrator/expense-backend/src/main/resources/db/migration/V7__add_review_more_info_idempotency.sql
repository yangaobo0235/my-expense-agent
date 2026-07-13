CREATE UNIQUE INDEX uq_campus_fund_audit_more_info_request
    ON campus_fund_audit_log (request_id)
    WHERE action = 'REVIEW_MORE_INFO_REQUESTED' AND request_id IS NOT NULL;

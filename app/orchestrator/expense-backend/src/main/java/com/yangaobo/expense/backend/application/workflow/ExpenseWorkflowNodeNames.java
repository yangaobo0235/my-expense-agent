package com.yangaobo.expense.backend.application.workflow;

final class ExpenseWorkflowNodeNames {

    static final String RESTORE_WORKFLOW = "RESTORE_WORKFLOW";
    static final String LOAD_EXTRACTED = "LOAD_EXTRACTED";
    static final String EXECUTION_POLICY = "EXECUTION_POLICY";
    static final String PARALLEL_EVIDENCE_COLLECTION = "PARALLEL_EVIDENCE_COLLECTION";
    static final String EVIDENCE_QUALITY_GATE = "EVIDENCE_QUALITY_GATE";
    static final String RISK_ASSESSMENT = "RISK_ASSESSMENT";
    static final String RISK_ROUTING = "RISK_ROUTING";
    static final String CREATE_MORE_INFO_TASK = "CREATE_MORE_INFO_TASK";
    static final String GENERATE_REVIEW_SUMMARY = "GENERATE_REVIEW_SUMMARY";
    static final String VERIFY_SUMMARY_REFERENCES = "VERIFY_SUMMARY_REFERENCES";
    static final String CREATE_REVIEW_TASK = "CREATE_REVIEW_TASK";
    static final String FINALIZE = "FINALIZE";

    private ExpenseWorkflowNodeNames() {}
}

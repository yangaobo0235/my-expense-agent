package com.yangaobo.expense.backend.application.workflow;

import com.yangaobo.expense.backend.application.extraction.ExtractedExpenseDocument;
import com.yangaobo.expense.backend.domain.model.ExpenseCase;
import com.yangaobo.expense.backend.domain.risk.RiskAssessment;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bsc.langgraph4j.state.AgentState;

public class ExpenseWorkflowGraphState extends AgentState {

    static final String CASE_ID = "caseId";
    static final String RUN_ID = "runId";
    static final String OWNER_SUBJECT = "ownerSubject";
    static final String REQUEST_ID = "requestId";
    static final String COMMAND = "command";
    static final String EXPENSE_CASE = "expenseCase";
    static final String RESTORE_ONLY = "restoreOnly";
    static final String EXTRACTED_DOCUMENTS = "extractedDocuments";
    static final String AGENT_PLAN = "agentPlan";
    static final String APPLICANT_CONTEXT = "applicantContext";
    static final String DUPLICATE_CHECK = "duplicateCheck";
    static final String PROJECT_BUDGET = "projectBudget";
    static final String REIMBURSEMENT_HISTORY = "reimbursementHistory";
    static final String EVIDENCE_RESULT = "evidenceResult";
    static final String POLICY_FINDINGS = "policyFindings";
    static final String RISK_ASSESSMENT = "riskAssessment";
    static final String ROUTING_DECISION = "routingDecision";
    static final String WORKFLOW_RESULT = "workflowResult";

    public ExpenseWorkflowGraphState(Map<String, Object> initData) {
        super(initData);
    }

    static Map<String, Object> initial(
            UUID caseId,
            UUID runId,
            String ownerSubject,
            String requestId,
            ExpenseWorkflowCommand command,
            ExpenseCase expenseCase,
            boolean restoreOnly) {
        Map<String, Object> data = new HashMap<>();
        data.put(CASE_ID, caseId);
        data.put(RUN_ID, runId);
        data.put(OWNER_SUBJECT, ownerSubject);
        data.put(REQUEST_ID, requestId);
        data.put(COMMAND, command);
        data.put(EXPENSE_CASE, expenseCase);
        data.put(RESTORE_ONLY, restoreOnly);
        return data;
    }

    UUID caseId() {
        return (UUID) value(CASE_ID).orElseThrow();
    }

    UUID runId() {
        return (UUID) value(RUN_ID).orElseThrow();
    }

    String ownerSubject() {
        return (String) value(OWNER_SUBJECT).orElseThrow();
    }

    String requestId() {
        return (String) value(REQUEST_ID).orElseThrow();
    }

    ExpenseWorkflowCommand command() {
        return (ExpenseWorkflowCommand) value(COMMAND).orElseThrow();
    }

    ExpenseCase expenseCase() {
        return (ExpenseCase) value(EXPENSE_CASE).orElseThrow();
    }

    boolean restoreOnly() {
        return value(RESTORE_ONLY, false);
    }

    List<ExtractedExpenseDocument> extractedDocuments() {
        return (List<ExtractedExpenseDocument>) value(EXTRACTED_DOCUMENTS).orElseThrow();
    }

    ExpenseContextGateway.DuplicateCheck duplicateCheck() {
        return (ExpenseContextGateway.DuplicateCheck) value(DUPLICATE_CHECK).orElseThrow();
    }

    ExpenseContextGateway.ApplicantContext applicantContext() {
        return (ExpenseContextGateway.ApplicantContext) value(APPLICANT_CONTEXT).orElseThrow();
    }

    ExpenseContextGateway.ProjectBudget projectBudget() {
        return (ExpenseContextGateway.ProjectBudget) value(PROJECT_BUDGET).orElseThrow();
    }

    ExpenseContextGateway.ReimbursementHistory reimbursementHistory() {
        return (ExpenseContextGateway.ReimbursementHistory)
                value(REIMBURSEMENT_HISTORY).orElseThrow();
    }

    WorkflowEvidenceGateway.EvidenceResult evidenceResult() {
        return (WorkflowEvidenceGateway.EvidenceResult) value(EVIDENCE_RESULT).orElseThrow();
    }

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> policyFindings() {
        return (List<Map<String, Object>>) (Object) value(POLICY_FINDINGS, List.of());
    }

    RiskAssessment riskAssessment() {
        return (RiskAssessment) value(RISK_ASSESSMENT).orElseThrow();
    }

    RiskRoutingDecision routingDecision() {
        return (RiskRoutingDecision) value(ROUTING_DECISION).orElseThrow();
    }

    ExpenseWorkflowResult workflowResult() {
        return (ExpenseWorkflowResult) value(WORKFLOW_RESULT).orElseThrow();
    }
}

package com.yangaobo.expense.backend.interfaces.rest;

import com.yangaobo.expense.backend.application.workflow.ExpenseWorkflowResult;
import com.yangaobo.expense.backend.domain.model.RiskLevel;
import com.yangaobo.expense.backend.domain.risk.RiskSignal;
import com.yangaobo.expense.common.domain.ExpenseCaseStatus;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ExpenseWorkflowResponse(
        UUID caseId,
        UUID runId,
        ExpenseCaseStatus status,
        int riskScore,
        RiskLevel riskLevel,
        List<RiskSignal> riskSignals,
        List<Map<String, Object>> policyFindings,
        UUID reviewTaskId) {

    static ExpenseWorkflowResponse from(ExpenseWorkflowResult result) {
        return new ExpenseWorkflowResponse(
                result.caseId(),
                result.runId(),
                result.status(),
                result.riskScore(),
                result.riskLevel(),
                result.riskSignals(),
                result.policyFindings(),
                result.reviewTaskId());
    }
}

package com.yangaobo.expense.backend.application.workflow;

import com.yangaobo.expense.backend.domain.model.RiskLevel;
import com.yangaobo.expense.backend.domain.risk.RiskSignal;
import com.yangaobo.expense.common.domain.ExpenseCaseStatus;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ExpenseWorkflowResult(
        UUID caseId,
        UUID runId,
        ExpenseCaseStatus status,
        int riskScore,
        RiskLevel riskLevel,
        List<RiskSignal> riskSignals,
        List<Map<String, Object>> policyFindings,
        UUID reviewTaskId)
        implements Serializable {

    public ExpenseWorkflowResult {
        riskSignals = List.copyOf(riskSignals);
        policyFindings = List.copyOf(policyFindings);
    }
}

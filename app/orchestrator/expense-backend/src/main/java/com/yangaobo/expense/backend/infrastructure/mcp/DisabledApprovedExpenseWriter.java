package com.yangaobo.expense.backend.infrastructure.mcp;

import com.yangaobo.expense.backend.application.settlement.ApprovedExpenseWriter;
import com.yangaobo.expense.common.error.ExpenseFlowErrorCode;
import com.yangaobo.expense.common.error.ExpenseFlowException;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "expense.mcp.client",
        name = "enabled",
        havingValue = "false",
        matchIfMissing = false)
public class DisabledApprovedExpenseWriter
        implements ApprovedExpenseWriter {

    @Override
    public WriteResult submitReimbursement(
            UUID caseId,
            BigDecimal amount,
            String currency,
            String requestId,
            String actorSubject,
            String approvalReference) {
        throw unavailable();
    }

    @Override
    public WriteResult submitPayment(
            UUID caseId,
            UUID reimbursementId,
            BigDecimal amount,
            String currency,
            String requestId,
            String actorSubject,
            String approvalReference) {
        throw unavailable();
    }

    private static ExpenseFlowException unavailable() {
        return new ExpenseFlowException(
                ExpenseFlowErrorCode.DEPENDENCY_UNAVAILABLE,
                "MCP Client 未启用，无法执行结算");
    }
}

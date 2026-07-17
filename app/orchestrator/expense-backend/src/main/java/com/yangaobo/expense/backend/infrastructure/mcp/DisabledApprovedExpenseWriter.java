package com.yangaobo.expense.backend.infrastructure.mcp;

import com.yangaobo.expense.backend.application.settlement.ApprovedExpenseWriter;
import com.yangaobo.expense.common.error.MyExpenseAgentErrorCode;
import com.yangaobo.expense.common.error.MyExpenseAgentException;
import java.math.BigDecimal;
import java.time.LocalDate;
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
    public WriteResult debitProjectBudget(
            UUID caseId,
            BigDecimal amount,
            String currency,
            String requestId,
            String actorSubject,
            String approvalReference) {
        throw unavailable();
    }

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

    @Override
    public WriteResult recordReimbursementHistory(
            UUID caseId,
            String sellerName,
            BigDecimal amount,
            String currency,
            LocalDate expenseDate,
            String documentSha256,
            String requestId,
            String actorSubject,
            String approvalReference) {
        throw unavailable();
    }

    private static MyExpenseAgentException unavailable() {
        return new MyExpenseAgentException(
                MyExpenseAgentErrorCode.DEPENDENCY_UNAVAILABLE,
                "MCP Client 未启用，无法执行审批后入账");
    }
}

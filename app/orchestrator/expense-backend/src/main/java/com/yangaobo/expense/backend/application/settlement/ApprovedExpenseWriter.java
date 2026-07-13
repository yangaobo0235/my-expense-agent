package com.yangaobo.expense.backend.application.settlement;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

public interface ApprovedExpenseWriter {

    WriteResult debitProjectBudget(
            UUID caseId,
            BigDecimal amount,
            String currency,
            String requestId,
            String actorSubject,
            String approvalReference);

    WriteResult submitReimbursement(
            UUID caseId,
            BigDecimal amount,
            String currency,
            String requestId,
            String actorSubject,
            String approvalReference);

    WriteResult submitPayment(
            UUID caseId,
            UUID reimbursementId,
            BigDecimal amount,
            String currency,
            String requestId,
            String actorSubject,
            String approvalReference);

    WriteResult recordReimbursementHistory(
            UUID caseId,
            String sellerName,
            BigDecimal amount,
            String currency,
            LocalDate expenseDate,
            String documentSha256,
            String requestId,
            String actorSubject,
            String approvalReference);

    record WriteResult(
            boolean success, Map<String, Object> output) {

        public WriteResult {
            output = output == null ? Map.of() : Map.copyOf(output);
        }
    }
}

package com.yangaobo.expense.backend.application.workflow;

import java.time.LocalDate;

public record ExpenseWorkflowCommand(
        String requestId,
        String category,
        String region,
        String employeeGrade,
        LocalDate expenseDate,
        boolean duplicateDocument,
        boolean dateAnomaly,
        boolean sellerAnomaly,
        boolean policyLimitExceeded,
        boolean missingRequiredDocument,
        boolean forbiddenExpenseItem) {}

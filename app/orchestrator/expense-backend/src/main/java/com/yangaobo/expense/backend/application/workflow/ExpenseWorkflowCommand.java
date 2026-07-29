package com.yangaobo.expense.backend.application.workflow;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.UUID;

public record ExpenseWorkflowCommand(
        String requestId,
        String category,
        LocalDate expenseDate,
        WorkflowCommandType commandType,
        Integer documentVersion,
        UUID previousRunId,
        String reopenReason)
        implements Serializable {

    public ExpenseWorkflowCommand(String requestId, String category, LocalDate expenseDate) {
        this(requestId, category, expenseDate, WorkflowCommandType.REVIEW, null, null, null);
    }

    public ExpenseWorkflowCommand {
        commandType = commandType == null ? WorkflowCommandType.REVIEW : commandType;
        reopenReason = reopenReason == null ? null : reopenReason.trim();
    }
}

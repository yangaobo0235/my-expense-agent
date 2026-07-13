package com.yangaobo.expense.backend.application.workflow;

import java.io.Serializable;
import java.time.LocalDate;

public record ExpenseWorkflowCommand(
        String requestId,
        String category,
        LocalDate expenseDate)
        implements Serializable {}

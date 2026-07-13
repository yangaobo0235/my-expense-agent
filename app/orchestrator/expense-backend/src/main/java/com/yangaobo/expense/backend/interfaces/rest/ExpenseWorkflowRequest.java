package com.yangaobo.expense.backend.interfaces.rest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record ExpenseWorkflowRequest(
        @NotBlank @Size(max = 128) String requestId,
        @NotBlank @Size(max = 64) String category,
        @NotNull LocalDate expenseDate) {}

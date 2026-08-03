package com.yangaobo.expense.backend.interfaces.rest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

public record ReviewRunRequest(
        @NotBlank @Size(max = 128) String requestId,
        @NotBlank @Size(max = 64) String category,
        @NotNull LocalDate expenseDate,
        @Positive int documentVersion,
        @NotNull UUID previousRunId,
        @NotBlank @Size(max = 1000) String reopenReason) {}

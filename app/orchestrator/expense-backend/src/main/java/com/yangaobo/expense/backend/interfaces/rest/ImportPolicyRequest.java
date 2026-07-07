package com.yangaobo.expense.backend.interfaces.rest;

import com.yangaobo.expense.backend.domain.model.PolicyStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record ImportPolicyRequest(
        @NotBlank @Size(max = 64) String policyCode,
        @NotBlank @Size(max = 256) String name,
        @NotBlank @Size(max = 64) String category,
        @NotBlank @Size(max = 64) String region,
        @NotBlank @Size(max = 64) String employeeGrade,
        @NotBlank @Size(max = 32) String version,
        @NotNull LocalDate effectiveFrom,
        LocalDate effectiveTo,
        @NotNull PolicyStatus status,
        @Size(max = 1000) String sourceUri,
        @NotBlank String markdownContent) {}

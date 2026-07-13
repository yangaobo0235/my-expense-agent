package com.yangaobo.expense.backend.application.policy;

import com.yangaobo.expense.backend.domain.model.PolicyStatus;
import java.time.LocalDate;

public record ImportPolicyCommand(
        String policyCode,
        String name,
        String category,
        String region,
        String applicantType,
        String version,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        PolicyStatus status,
        String sourceUri,
        String markdownContent) {}

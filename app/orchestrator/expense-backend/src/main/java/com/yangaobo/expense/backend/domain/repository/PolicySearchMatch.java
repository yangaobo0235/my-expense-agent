package com.yangaobo.expense.backend.domain.repository;

import java.time.LocalDate;
import java.util.UUID;

public record PolicySearchMatch(
        UUID policyId,
        String policyCode,
        String policyName,
        String policyVersion,
        String category,
        String region,
        String employeeGrade,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        String sourceUri,
        UUID chunkId,
        int chunkIndex,
        String section,
        String content,
        double score) {}

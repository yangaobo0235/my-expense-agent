package com.yangaobo.expense.backend.domain.repository;

import com.yangaobo.expense.backend.domain.model.PolicyStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PolicyCatalogEntry(
        UUID id,
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
        int chunkCount,
        int indexedChunkCount,
        Instant updatedAt) {}

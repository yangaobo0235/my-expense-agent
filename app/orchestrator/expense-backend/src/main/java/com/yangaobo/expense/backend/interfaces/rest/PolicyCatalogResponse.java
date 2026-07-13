package com.yangaobo.expense.backend.interfaces.rest;

import com.yangaobo.expense.backend.domain.model.PolicyStatus;
import com.yangaobo.expense.backend.domain.repository.PolicyCatalogEntry;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PolicyCatalogResponse(
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
        Instant updatedAt) {

    static PolicyCatalogResponse from(PolicyCatalogEntry entry) {
        return new PolicyCatalogResponse(
                entry.id(),
                entry.policyCode(),
                entry.name(),
                entry.category(),
                entry.region(),
                entry.applicantType(),
                entry.version(),
                entry.effectiveFrom(),
                entry.effectiveTo(),
                entry.status(),
                entry.sourceUri(),
                entry.chunkCount(),
                entry.indexedChunkCount(),
                entry.updatedAt());
    }
}

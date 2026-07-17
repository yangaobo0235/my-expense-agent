package com.yangaobo.expense.backend.domain.model;

import com.yangaobo.expense.common.error.MyExpenseAgentErrorCode;
import com.yangaobo.expense.common.error.MyExpenseAgentException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public record ExpensePolicy(
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
        String contentHash,
        Instant createdAt,
        Instant updatedAt) {

    public ExpensePolicy {
        Objects.requireNonNull(id, "id");
        policyCode = required(policyCode, "policyCode", 64);
        name = required(name, "name", 256);
        category = required(category, "category", 64);
        region = required(region, "region", 64);
        applicantType = required(applicantType, "applicantType", 64);
        version = required(version, "version", 32);
        Objects.requireNonNull(effectiveFrom, "effectiveFrom");
        Objects.requireNonNull(status, "status");
        contentHash = required(contentHash, "contentHash", 64);
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (effectiveTo != null && effectiveTo.isBefore(effectiveFrom)) {
            throw validation("effectiveTo must not be earlier than effectiveFrom");
        }
        if (sourceUri != null && sourceUri.length() > 1000) {
            throw validation("sourceUri exceeds 1000 characters");
        }
    }

    private static String required(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw validation(field + " is required");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw validation(field + " exceeds " + maxLength + " characters");
        }
        return normalized;
    }

    private static MyExpenseAgentException validation(String message) {
        return new MyExpenseAgentException(MyExpenseAgentErrorCode.VALIDATION_FAILED, message);
    }
}

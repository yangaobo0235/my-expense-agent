package com.yangaobo.expense.backend.domain.model;

import com.yangaobo.expense.common.error.MyExpenseAgentErrorCode;
import com.yangaobo.expense.common.error.MyExpenseAgentException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ExpenseDocument(
        UUID id,
        UUID caseId,
        String originalFilename,
        String contentType,
        long fileSize,
        String sha256,
        String objectKey,
        Instant createdAt,
        Instant updatedAt) {

    public ExpenseDocument {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(caseId, "caseId");
        originalFilename = required(originalFilename, "originalFilename", 512);
        contentType = required(contentType, "contentType", 128);
        sha256 = required(sha256, "sha256", 64);
        objectKey = required(objectKey, "objectKey", 768);
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (fileSize <= 0) {
            throw validation("Document must not be empty");
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
        return new MyExpenseAgentException(MyExpenseAgentErrorCode.DOCUMENT_REJECTED, message);
    }
}

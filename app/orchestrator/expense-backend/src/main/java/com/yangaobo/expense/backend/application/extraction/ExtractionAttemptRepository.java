package com.yangaobo.expense.backend.application.extraction;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ExtractionAttemptRepository {

    void save(ExtractionAttempt attempt);

    List<ExtractionAttempt> findByDocumentId(UUID documentId);

    record ExtractionAttempt(
            UUID id,
            UUID documentId,
            int attemptNo,
            String attemptType,
            String promptVersion,
            String modelName,
            List<ExtractionValidationError> validationErrors,
            String outputHash,
            int tokenUsage,
            long latencyMs,
            int networkRetryCount,
            String status,
            Instant createdAt) {

        public ExtractionAttempt {
            validationErrors = validationErrors == null ? List.of() : List.copyOf(validationErrors);
        }
    }
}

package com.yangaobo.expense.backend.application.extraction;

import java.util.List;

public record ExtractionAttemptMetadata(
        String outputHash,
        int promptTokens,
        int completionTokens,
        long latencyMs,
        int networkRetryCount,
        List<ExtractionValidationError> validationErrors,
        String status) {

    public ExtractionAttemptMetadata {
        validationErrors = validationErrors == null ? List.of() : List.copyOf(validationErrors);
    }

    public int totalTokens() {
        return promptTokens + completionTokens;
    }
}

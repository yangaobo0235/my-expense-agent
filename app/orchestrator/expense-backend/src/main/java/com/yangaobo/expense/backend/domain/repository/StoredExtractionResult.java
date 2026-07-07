package com.yangaobo.expense.backend.domain.repository;

import java.util.List;

public record StoredExtractionResult(
        String documentType,
        double confidence,
        String resultJson,
        List<String> validationErrors,
        String modelName,
        String promptVersion,
        String rawResponseHash,
        int tokenUsage,
        long extractionLatencyMs,
        String extractorMode) {

    public StoredExtractionResult(
            String documentType,
            double confidence,
            String resultJson,
            List<String> validationErrors,
            String modelName,
            String promptVersion,
            String rawResponseHash) {
        this(
                documentType,
                confidence,
                resultJson,
                validationErrors,
                modelName,
                promptVersion,
                rawResponseHash,
                0,
                0,
                "deterministic");
    }

    public StoredExtractionResult {
        validationErrors = validationErrors == null ? List.of() : List.copyOf(validationErrors);
        extractorMode =
                extractorMode == null || extractorMode.isBlank()
                        ? "deterministic"
                        : extractorMode.trim();
    }

    public boolean valid() {
        return validationErrors.isEmpty();
    }
}

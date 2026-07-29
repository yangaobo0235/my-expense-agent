package com.yangaobo.expense.backend.application.extraction;

import java.util.List;

public record ExtractionCandidate(
        ExtractedExpenseDocument document,
        String modelName,
        String promptVersion,
        String rawResponseHash,
        int promptTokens,
        int completionTokens,
        long latencyMs,
        String extractorMode,
        int networkRetryCount,
        boolean repairUsed,
        List<ExtractionAttemptMetadata> priorAttempts) {

    public ExtractionCandidate {
        priorAttempts = priorAttempts == null ? List.of() : List.copyOf(priorAttempts);
    }

    public ExtractionCandidate(
            ExtractedExpenseDocument document,
            String modelName,
            String promptVersion,
            String rawResponseHash,
            int promptTokens,
            int completionTokens,
            long latencyMs,
            String extractorMode,
            int networkRetryCount,
            boolean repairUsed) {
        this(document, modelName, promptVersion, rawResponseHash, promptTokens,
                completionTokens, latencyMs, extractorMode, networkRetryCount, repairUsed, List.of());
    }

    public ExtractionCandidate(
            ExtractedExpenseDocument document,
            String modelName,
            String promptVersion,
            String rawResponseHash) {
        this(document, modelName, promptVersion, rawResponseHash, 0, 0, 0, "deterministic", 0, false, List.of());
    }

    public ExtractionCandidate(
            ExtractedExpenseDocument document,
            String modelName,
            String promptVersion,
            String rawResponseHash,
            int promptTokens,
            int completionTokens,
            long latencyMs,
            String extractorMode) {
        this(document, modelName, promptVersion, rawResponseHash, promptTokens,
                completionTokens, latencyMs, extractorMode, 0, false, List.of());
    }

    public int totalTokens() {
        return promptTokens + completionTokens;
    }
}

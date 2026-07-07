package com.yangaobo.expense.backend.application.extraction;

public record ExtractionCandidate(
        ExtractedExpenseDocument document,
        String modelName,
        String promptVersion,
        String rawResponseHash,
        int promptTokens,
        int completionTokens,
        long latencyMs,
        String extractorMode) {

    public ExtractionCandidate(
            ExtractedExpenseDocument document,
            String modelName,
            String promptVersion,
            String rawResponseHash) {
        this(document, modelName, promptVersion, rawResponseHash, 0, 0, 0, "deterministic");
    }

    public int totalTokens() {
        return promptTokens + completionTokens;
    }
}

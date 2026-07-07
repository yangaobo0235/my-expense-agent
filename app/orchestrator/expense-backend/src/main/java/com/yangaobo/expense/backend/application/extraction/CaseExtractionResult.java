package com.yangaobo.expense.backend.application.extraction;

import java.util.List;
import java.util.UUID;

public record CaseExtractionResult(UUID caseId, List<DocumentExtractionOutcome> documents) {

    public CaseExtractionResult {
        documents = documents == null ? List.of() : List.copyOf(documents);
    }

    public boolean requiresHumanReview() {
        return documents.stream()
                .anyMatch(
                        outcome ->
                                !outcome.validationErrors().isEmpty()
                                        || outcome.result().confidence() < 0.7);
    }
}

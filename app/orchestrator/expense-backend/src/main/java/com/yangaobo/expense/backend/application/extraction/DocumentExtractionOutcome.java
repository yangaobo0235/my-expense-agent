package com.yangaobo.expense.backend.application.extraction;

import java.util.List;
import java.util.UUID;

public record DocumentExtractionOutcome(
        UUID documentId,
        ExtractedExpenseDocument result,
        List<String> validationErrors,
        boolean reused,
        boolean repairUsed,
        boolean manualReviewRequired) {

    public DocumentExtractionOutcome {
        validationErrors =
                validationErrors == null ? List.of() : List.copyOf(validationErrors);
    }

    public DocumentExtractionOutcome(
            UUID documentId,
            ExtractedExpenseDocument result,
            List<String> validationErrors,
            boolean reused) {
        this(documentId, result, validationErrors, reused, false, !validationErrors.isEmpty());
    }
}

package com.yangaobo.expense.backend.application.extraction;

import java.util.List;
import java.util.UUID;

public record DocumentExtractionOutcome(
        UUID documentId,
        ExtractedExpenseDocument result,
        List<String> validationErrors,
        boolean reused) {

    public DocumentExtractionOutcome {
        validationErrors =
                validationErrors == null ? List.of() : List.copyOf(validationErrors);
    }
}

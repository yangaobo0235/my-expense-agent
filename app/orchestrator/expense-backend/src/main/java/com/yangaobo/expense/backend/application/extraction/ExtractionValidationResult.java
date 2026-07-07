package com.yangaobo.expense.backend.application.extraction;

import java.util.List;

public record ExtractionValidationResult(
        ExtractedExpenseDocument document, List<String> errors) {

    public ExtractionValidationResult {
        errors = errors == null ? List.of() : List.copyOf(errors);
    }

    public boolean valid() {
        return errors.isEmpty();
    }
}

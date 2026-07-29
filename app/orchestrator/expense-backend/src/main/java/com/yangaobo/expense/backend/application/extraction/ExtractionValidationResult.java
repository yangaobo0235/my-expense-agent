package com.yangaobo.expense.backend.application.extraction;

import java.util.List;

public record ExtractionValidationResult(
        ExtractedExpenseDocument document, List<ExtractionValidationError> violations) {

    public ExtractionValidationResult {
        violations = violations == null ? List.of() : List.copyOf(violations);
    }

    public boolean valid() {
        return violations.isEmpty();
    }

    public List<String> errors() {
        return violations.stream().map(ExtractionValidationError::message).toList();
    }
}

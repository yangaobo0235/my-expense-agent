package com.yangaobo.expense.backend.application.extraction;

public record ExtractionValidationError(
        ExtractionErrorCode code,
        String field,
        String message,
        boolean repairable) {

    public ExtractionValidationError {
        if (code == null) {
            throw new IllegalArgumentException("code不能为空");
        }
        field = field == null ? "" : field;
        message = message == null ? code.name() : message;
    }

    public static ExtractionValidationError repairable(
            ExtractionErrorCode code, String field, String message) {
        return new ExtractionValidationError(code, field, message, true);
    }

    public static ExtractionValidationError terminal(
            ExtractionErrorCode code, String field, String message) {
        return new ExtractionValidationError(code, field, message, false);
    }
}

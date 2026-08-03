package com.yangaobo.expense.backend.application.extraction;

public enum ExtractionErrorCode {
    JSON_INVALID,
    SCHEMA_INVALID,
    REQUIRED_FACT_MISSING,
    INVALID_DATE,
    INVALID_AMOUNT,
    INVALID_CURRENCY,
    INVALID_INVOICE_NUMBER,
    INVALID_CONFIDENCE,
    ITEM_TOTAL_MISMATCH,
    LOW_CONFIDENCE,
    PROMPT_INJECTION_DETECTED
}

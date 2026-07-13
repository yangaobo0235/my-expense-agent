package com.yangaobo.expense.backend.application.extraction;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ExtractedExpenseDocument(
        String documentType,
        String invoiceCode,
        String invoiceNumber,
        String sellerName,
        String buyerName,
        LocalDate issueDate,
        BigDecimal totalAmount,
        String currency,
        List<ExtractedExpenseItem> items,
        double confidence,
        List<String> warnings)
        implements Serializable {

    public ExtractedExpenseDocument {
        items = items == null ? List.of() : List.copyOf(items);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}

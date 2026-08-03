package com.yangaobo.expense.backend.application.evaluation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ExtractionEvaluationDataset(String datasetVersion, List<ExtractionCase> cases) {
    public record ExtractionCase(
            String id,
            String category,
            boolean jsonValid,
            boolean schemaPassed,
            String expectedInvoiceNumber,
            String actualInvoiceNumber,
            BigDecimal expectedAmount,
            BigDecimal actualAmount,
            LocalDate expectedDate,
            LocalDate actualDate,
            String expectedCurrency,
            String actualCurrency,
            List<String> expectedItems,
            List<String> actualItems,
            boolean repairUsed,
            boolean humanHandoff,
            long latencyMs,
            int tokenUsage) {}
}

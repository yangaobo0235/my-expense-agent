package com.yangaobo.expense.backend.application.evaluation;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ExtractionEvaluationReport(
        String datasetVersion,
        Instant generatedAt,
        int caseCount,
        Map<String, Integer> categoryCounts,
        Metrics metrics,
        boolean gatePassed,
        List<Failure> failures) {
    public record Metrics(
            double jsonValidRate,
            double schemaPassRate,
            double invoiceNumberExactMatch,
            double amountExactMatch,
            double dateAccuracy,
            double currencyAccuracy,
            double itemPrecision,
            double itemRecall,
            double itemF1,
            double repairSuccessRate,
            double humanHandoffRate,
            long p50LatencyMs,
            long p95LatencyMs,
            double averageTokenUsage) {}
    public record Failure(String caseId, List<String> mismatchedFields) {}
}

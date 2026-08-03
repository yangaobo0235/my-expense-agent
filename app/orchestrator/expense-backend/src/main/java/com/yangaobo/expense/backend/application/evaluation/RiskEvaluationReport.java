package com.yangaobo.expense.backend.application.evaluation;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record RiskEvaluationReport(
        String datasetVersion,
        String datasetSha256,
        String engineVersion,
        Instant generatedAt,
        int caseCount,
        Map<String, Integer> categoryCounts,
        Metrics metrics,
        List<Failure> failures) {

    public record Metrics(
            double riskLevelAccuracy,
            double routingAccuracy,
            double highRiskRecall) {}

    public record Failure(
            String caseId,
            List<String> expectedSignals,
            List<String> actualSignals,
            String expectedRiskLevel,
            String actualRiskLevel,
            boolean expectedHumanReview,
            boolean actualHumanReview) {}
}

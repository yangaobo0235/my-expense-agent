package com.yangaobo.expense.backend.application.evaluation;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record PolicyRagEvaluationReport(
        String datasetVersion,
        Instant generatedAt,
        int caseCount,
        Metrics metrics,
        List<Failure> failures) {

    public record Metrics(
            double recallAt5,
            double precisionAt5,
            double expectedPolicyHitRate,
            double expectedSectionHitRate,
            double noAnswerAccuracy,
            boolean injectionDefensePassed,
            double averageSearchLatencyMs) {}

    public record Failure(
            String caseId,
            String query,
            String expectedPolicyCode,
            List<String> expectedSections,
            List<Map<String, Object>> actualMatches,
            String reason) {

        public Failure {
            expectedSections = expectedSections == null ? List.of() : List.copyOf(expectedSections);
            actualMatches = actualMatches == null ? List.of() : List.copyOf(actualMatches);
        }
    }
}

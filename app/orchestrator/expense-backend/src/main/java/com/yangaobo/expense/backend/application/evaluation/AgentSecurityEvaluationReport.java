package com.yangaobo.expense.backend.application.evaluation;

import java.time.Instant;
import java.util.List;

public record AgentSecurityEvaluationReport(
        String datasetVersion,
        Instant generatedAt,
        int caseCount,
        Metrics metrics,
        List<Failure> failures) {

    public record Metrics(
            int blockedWriteToolCount,
            int unsafeWriteToolCallCount,
            int injectionDetectedCount,
            int humanHandoffCount,
            double securityPassRate) {}

    public record Failure(String caseId, String reason, String maliciousText) {}
}

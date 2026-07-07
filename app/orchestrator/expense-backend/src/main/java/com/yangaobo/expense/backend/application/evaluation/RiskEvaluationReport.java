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
        AgentGovernance agentGovernance,
        List<Failure> failures) {

    public record Metrics(
            double precision,
            double recall,
            double f1,
            double riskLevelAccuracy,
            double humanReviewAccuracy,
            double highRiskMissRate,
            double humanReviewTriggerRate) {}

    public record AgentGovernance(
            String planVersion,
            int totalAgents,
            int writeAgentCount,
            int idempotentWriteAgentCount,
            boolean writeToolIsolationPassed,
            boolean settlementWriteRetryProtected,
            double humanHandoffCoverage,
            double retryableAgentRate) {}

    public record Failure(
            String caseId,
            List<String> expectedSignals,
            List<String> actualSignals,
            String expectedRiskLevel,
            String actualRiskLevel,
            boolean expectedHumanReview,
            boolean actualHumanReview) {}
}

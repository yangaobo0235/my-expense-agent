package com.yangaobo.expense.backend.application.evaluation;

import java.math.BigDecimal;
import java.util.List;

public record RiskEvaluationDataset(
        String datasetVersion, String description, List<RiskEvaluationCase> cases) {

    public record RiskEvaluationCase(
            String id,
            String category,
            BigDecimal claimedAmount,
            BigDecimal extractedAmount,
            double extractionConfidence,
            boolean duplicateDocument,
            boolean dateAnomaly,
            boolean sellerAnomaly,
            boolean policyLimitExceeded,
            boolean missingRequiredDocument,
            boolean forbiddenExpenseItem,
            boolean projectBudgetExceeded,
            boolean policyEvidenceMissing,
            boolean promptInjectionDetected,
            List<String> expectedSignals,
            String expectedRiskLevel,
            boolean expectedHumanReview) {}
}

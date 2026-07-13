package com.yangaobo.expense.backend.domain.risk;

import java.math.BigDecimal;
import java.util.Objects;

public record RiskAssessmentInput(
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
        boolean policyEvidenceMissing) {

    public RiskAssessmentInput(
            BigDecimal claimedAmount,
            BigDecimal extractedAmount,
            double extractionConfidence,
            boolean duplicateDocument,
            boolean dateAnomaly,
            boolean sellerAnomaly,
            boolean policyLimitExceeded,
            boolean missingRequiredDocument,
            boolean forbiddenExpenseItem) {
        this(
                claimedAmount,
                extractedAmount,
                extractionConfidence,
                duplicateDocument,
                dateAnomaly,
                sellerAnomaly,
                policyLimitExceeded,
                missingRequiredDocument,
                forbiddenExpenseItem,
                false,
                false);
    }

    public RiskAssessmentInput {
        Objects.requireNonNull(claimedAmount, "claimedAmount");
        Objects.requireNonNull(extractedAmount, "extractedAmount");
        if (claimedAmount.signum() < 0 || extractedAmount.signum() < 0) {
            throw new IllegalArgumentException("费用金额不能为负数");
        }
        if (extractionConfidence < 0 || extractionConfidence > 1) {
            throw new IllegalArgumentException("提取置信度必须处于 0 到 1");
        }
    }
}

package com.yangaobo.expense.backend.application.evaluation;

import java.time.LocalDate;
import java.util.List;

public record PolicyRagEvaluationDataset(
        String datasetVersion, List<PolicyRagCase> cases) {

    public record PolicyRagCase(
            String caseId,
            String query,
            String category,
            String region,
            String employeeGrade,
            LocalDate expenseDate,
            String expectedPolicyCode,
            List<String> expectedSections,
            boolean expectedAnswerable,
            boolean injectionCase) {

        public PolicyRagCase {
            expectedSections = expectedSections == null ? List.of() : List.copyOf(expectedSections);
        }
    }
}

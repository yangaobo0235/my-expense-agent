package com.yangaobo.expense.backend.application.evaluation;

import java.util.List;

public record AgentSecurityEvaluationDataset(
        String datasetVersion, List<SecurityCase> cases) {

    public record SecurityCase(
            String caseId,
            String inputLocation,
            String maliciousText,
            boolean expectedBlockedWriteTool,
            boolean expectedHumanHandoff) {}
}

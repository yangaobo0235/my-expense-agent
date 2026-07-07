package com.yangaobo.expense.backend.application.prompt;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record PromptChangeRequest(
        UUID id,
        UUID promptTemplateId,
        PromptChangeRequestType requestType,
        PromptChangeRequestStatus status,
        String diffSummary,
        String riskLevel,
        Map<String, Object> evaluationReport,
        String reviewComment,
        String submittedBy,
        String reviewedBy,
        Instant submittedAt,
        Instant reviewedAt) {

    public PromptChangeRequest {
        evaluationReport =
                evaluationReport == null ? Map.of() : Map.copyOf(evaluationReport);
    }
}

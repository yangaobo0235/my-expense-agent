package com.yangaobo.expense.backend.interfaces.rest;

import com.yangaobo.expense.backend.application.prompt.PromptChangeRequest;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record PromptChangeRequestResponse(
        UUID id,
        UUID promptTemplateId,
        String requestType,
        String status,
        String diffSummary,
        String riskLevel,
        Map<String, Object> evaluationReport,
        String reviewComment,
        String submittedBy,
        String reviewedBy,
        Instant submittedAt,
        Instant reviewedAt) {

    static PromptChangeRequestResponse from(PromptChangeRequest request) {
        return new PromptChangeRequestResponse(
                request.id(),
                request.promptTemplateId(),
                request.requestType().name(),
                request.status().name(),
                request.diffSummary(),
                request.riskLevel(),
                request.evaluationReport(),
                request.reviewComment(),
                request.submittedBy(),
                request.reviewedBy(),
                request.submittedAt(),
                request.reviewedAt());
    }
}

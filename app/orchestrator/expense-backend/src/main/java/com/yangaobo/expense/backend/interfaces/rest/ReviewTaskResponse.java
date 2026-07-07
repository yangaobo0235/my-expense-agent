package com.yangaobo.expense.backend.interfaces.rest;

import com.yangaobo.expense.backend.application.workflow.ReviewRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReviewTaskResponse(
        UUID id,
        UUID caseId,
        String status,
        String assigneeSubject,
        List<String> reasonCodes,
        String routingAction,
        String routingQueue,
        String assigneeRole,
        Integer slaHours,
        List<String> requiredEvidence,
        String userFacingMessage,
        String fallbackStrategy,
        boolean debateAssistEnabled,
        String reviewerComment,
        Instant dueAt,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    static ReviewTaskResponse from(ReviewRepository.ReviewTask task) {
        return new ReviewTaskResponse(
                task.id(),
                task.caseId(),
                task.status(),
                task.assigneeSubject(),
                task.reasonCodes(),
                task.routingAction(),
                task.routingQueue(),
                task.assigneeRole(),
                task.slaHours(),
                task.requiredEvidence(),
                task.userFacingMessage(),
                task.fallbackStrategy(),
                task.debateAssistEnabled(),
                task.reviewerComment(),
                task.dueAt(),
                task.version(),
                task.createdAt(),
                task.updatedAt());
    }
}

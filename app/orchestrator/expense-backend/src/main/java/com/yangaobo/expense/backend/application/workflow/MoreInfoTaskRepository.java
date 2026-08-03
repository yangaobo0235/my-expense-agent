package com.yangaobo.expense.backend.application.workflow;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MoreInfoTaskRepository {

    MoreInfoTask create(
            UUID caseId,
            UUID runId,
            List<String> requiredMaterials,
            List<String> reasonCodes,
            String requestedBy,
            Instant dueAt,
            Instant now);

    Optional<MoreInfoTask> findById(UUID taskId);

    Optional<MoreInfoTask> findOpenByCaseId(UUID caseId);

    void markSubmitted(UUID taskId, int documentVersion, Instant now);

    void complete(UUID taskId, Instant now);

    record MoreInfoTask(
            UUID id,
            UUID caseId,
            UUID runId,
            List<String> requiredMaterials,
            List<String> reasonCodes,
            String status,
            String requestedBy,
            Instant dueAt,
            Integer submittedDocumentVersion,
            Instant completedAt,
            Instant createdAt) {
        public MoreInfoTask {
            requiredMaterials = requiredMaterials == null ? List.of() : List.copyOf(requiredMaterials);
            reasonCodes = reasonCodes == null ? List.of() : List.copyOf(reasonCodes);
        }
    }
}

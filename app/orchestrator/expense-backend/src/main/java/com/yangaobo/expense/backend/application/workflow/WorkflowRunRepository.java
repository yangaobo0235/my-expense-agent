package com.yangaobo.expense.backend.application.workflow;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface WorkflowRunRepository {

    WorkflowRun startOrLoad(UUID caseId, String requestId);

    default WorkflowRun startOrLoad(
            UUID caseId,
            String requestId,
            WorkflowCommandType commandType,
            int documentVersion,
            UUID previousRunId,
            String reopenReason) {
        return startOrLoad(caseId, requestId);
    }

    default int currentDocumentVersion(UUID caseId) {
        return 1;
    }

    default Optional<WorkflowRunDetail> findRun(UUID runId) {
        return Optional.empty();
    }

    default List<WorkflowRunDetail> findByCaseId(UUID caseId) {
        return latestRun(caseId).stream().toList();
    }

    default void updateOutcome(UUID runId, String routeAction, String waitingReason) {}

    default List<DocumentVersion> documentVersions(UUID caseId) { return List.of(); }

    Optional<Map<String, Object>> successfulStep(UUID runId, String stepName);

    Optional<WorkflowRunDetail> latestRun(UUID caseId);

    List<WorkflowStep> steps(UUID runId);

    List<WorkflowRunDetail> recentRuns(int limit);

    void startStep(UUID runId, UUID caseId, String stepName, int attempt, String inputHash);

    void succeedStep(
            UUID runId,
            String stepName,
            int attempt,
            Map<String, Object> output);

    void failStep(
            UUID runId,
            String stepName,
            int attempt,
            String errorCode,
            String errorMessage);

    void succeedRun(UUID runId);

    void failRun(UUID runId, String errorCode, String errorMessage);

    record WorkflowRun(
            UUID id,
            UUID caseId,
            String requestId,
            String status,
            WorkflowCommandType commandType,
            int documentVersion,
            UUID previousRunId,
            String reopenReason) {
        public WorkflowRun(UUID id, UUID caseId, String requestId, String status) {
            this(id, caseId, requestId, status, WorkflowCommandType.REVIEW, 1, null, null);
        }
    }

    record WorkflowRunDetail(
            UUID id,
            UUID caseId,
            String requestId,
            String status,
            Instant startedAt,
            Instant completedAt,
            String errorCode,
            String errorMessage,
            WorkflowCommandType commandType,
            int documentVersion,
            UUID previousRunId,
            String reopenReason,
            String routeAction,
            String waitingReason) {
        public WorkflowRunDetail(
                UUID id, UUID caseId, String requestId, String status,
                Instant startedAt, Instant completedAt, String errorCode, String errorMessage) {
            this(id, caseId, requestId, status, startedAt, completedAt, errorCode, errorMessage,
                    WorkflowCommandType.REVIEW, 1, null, null, null, null);
        }
    }

    record WorkflowStep(
            UUID id,
            String name,
            int attempt,
            String status,
            Map<String, Object> output,
            Instant startedAt,
            Instant completedAt,
            String errorCode,
            String errorMessage) {}

    record DocumentVersion(
            UUID caseId,
            int version,
            UUID documentId,
            String sha256,
            String sourceType,
            String uploadedBy,
            Integer replacesVersion,
            Instant createdAt) {}
}

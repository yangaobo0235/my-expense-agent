package com.yangaobo.expense.backend.application.workflow;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface WorkflowRunRepository {

    WorkflowRun startOrLoad(UUID caseId, String requestId);

    Optional<Map<String, Object>> successfulStep(UUID runId, String stepName);

    Optional<WorkflowRunDetail> latestRun(UUID caseId);

    List<WorkflowStep> steps(UUID runId);

    List<WorkflowRunDetail> recentRuns(int limit);

    void attachTraceId(UUID runId, String traceId);

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

    record WorkflowRun(UUID id, UUID caseId, String requestId, String status) {}

    record WorkflowRunDetail(
            UUID id,
            UUID caseId,
            String requestId,
            String status,
            Instant startedAt,
            Instant completedAt,
            String errorCode,
            String errorMessage,
            String traceId) {}

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
}

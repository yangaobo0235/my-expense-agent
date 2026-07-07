package com.yangaobo.expense.backend.application.settlement;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface ToolCallRepository {

    Optional<ToolCall> find(String toolName, String requestId);

    List<ToolCallDetail> findByCaseId(UUID caseId);

    ToolCall start(
            UUID caseId,
            String toolName,
            String requestId,
            String inputHash,
            String actorSubject,
            String approvalReference,
            Instant now);

    void succeed(
            UUID id,
            String outputHash,
            Map<String, Object> output,
            long durationMs,
            Instant now);

    void fail(
            UUID id,
            String errorCode,
            long durationMs,
            Instant now);

    record ToolCall(
            UUID id,
            UUID caseId,
            String toolName,
            String requestId,
            String status,
            Map<String, Object> output) {

        public ToolCall {
            output = output == null ? Map.of() : Map.copyOf(output);
        }
    }

    record ToolCallDetail(
            UUID id,
            String toolName,
            boolean writeOperation,
            String status,
            Map<String, Object> output,
            long durationMs,
            String errorCode,
            String approvalReference,
            Instant createdAt,
            Instant completedAt) {}
}

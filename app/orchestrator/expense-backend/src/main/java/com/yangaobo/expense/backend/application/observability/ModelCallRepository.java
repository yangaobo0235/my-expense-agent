package com.yangaobo.expense.backend.application.observability;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface ModelCallRepository {

    void save(ModelCallRecord record);

    List<ModelCallRecord> recent(int limit);

    List<ModelCallRecord> findByCaseId(UUID caseId, int limit);

    ModelCallSummary summary();

    record ModelCallRecord(
            UUID id,
            UUID caseId,
            UUID runId,
            String stepName,
            String modelName,
            String promptVersion,
            String promptHash,
            String inputHash,
            String outputHash,
            int promptTokens,
            int completionTokens,
            int totalTokens,
            long latencyMs,
            int retryCount,
            String status,
            String errorCode,
            Instant createdAt) {}

    record ModelCallSummary(
            long totalCalls,
            double successRate,
            double averageLatencyMs,
            long p95LatencyMs,
            long totalTokens,
            Map<String, Long> callsByModel,
            Map<String, Long> failuresByStep) {}
}

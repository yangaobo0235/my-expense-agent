package com.yangaobo.expense.backend.application.observability;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface CaseAuditRepository {

    List<AuditEvent> findByCaseId(UUID caseId, int limit);

    record AuditEvent(
            UUID id,
            UUID caseId,
            String actorSubject,
            String actorType,
            String action,
            String resourceType,
            String resourceId,
            String requestId,
            Map<String, Object> metadata,
            Instant occurredAt) {}
}

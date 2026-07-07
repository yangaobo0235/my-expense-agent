package com.yangaobo.expense.backend.application.prompt;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface PromptTemplateRepository {

    PromptTemplate save(PromptTemplate template);

    PromptTemplate update(PromptTemplate template);

    Optional<PromptTemplate> findById(UUID id);

    Optional<PromptTemplate> findByKeyAndVersion(String promptKey, String version);

    Optional<PromptTemplate> active(String promptKey);

    List<PromptTemplate> list(String promptKey);

    void deactivateActive(String promptKey, String replacedByVersion);

    PromptChangeRequest saveChangeRequest(PromptChangeRequest request);

    PromptChangeRequest updateChangeRequest(PromptChangeRequest request);

    Optional<PromptChangeRequest> findChangeRequest(UUID id);

    List<PromptChangeRequest> changeRequests(UUID promptTemplateId);

    List<PromptAuditEvent> auditLog(String promptKey, String version, int limit);

    void appendAudit(
            String promptKey,
            String version,
            String action,
            String actorSubject,
            Map<String, Object> payload);

    record PromptAuditEvent(
            UUID id,
            String promptKey,
            String version,
            String action,
            String actorSubject,
            Map<String, Object> payload,
            java.time.Instant occurredAt) {}
}

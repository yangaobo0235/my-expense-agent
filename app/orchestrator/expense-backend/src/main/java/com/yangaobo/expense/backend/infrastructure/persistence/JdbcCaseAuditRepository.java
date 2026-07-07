package com.yangaobo.expense.backend.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangaobo.expense.backend.application.observability.CaseAuditRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcCaseAuditRepository implements CaseAuditRepository {

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;

    public JdbcCaseAuditRepository(JdbcClient jdbcClient, ObjectMapper objectMapper) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<AuditEvent> findByCaseId(UUID caseId, int limit) {
        return jdbcClient
                .sql(
                        """
                        SELECT id, case_id, actor_subject, actor_type, action,
                               resource_type, resource_id, request_id,
                               metadata::text AS metadata, occurred_at
                        FROM expense_audit_log
                        WHERE case_id = :caseId
                        ORDER BY occurred_at DESC, id DESC
                        LIMIT :limit
                        """)
                .param("caseId", caseId)
                .param("limit", Math.max(1, Math.min(limit, 100)))
                .query(
                        (rs, row) ->
                                new AuditEvent(
                                        rs.getObject("id", UUID.class),
                                        rs.getObject("case_id", UUID.class),
                                        rs.getString("actor_subject"),
                                        rs.getString("actor_type"),
                                        rs.getString("action"),
                                        rs.getString("resource_type"),
                                        rs.getString("resource_id"),
                                        rs.getString("request_id"),
                                        readMap(rs.getString("metadata")),
                                        rs.getTimestamp("occurred_at").toInstant()))
                .list();
    }

    private Map<String, Object> readMap(String json) {
        try {
            return objectMapper.readValue(json == null ? "{}" : json, new TypeReference<>() {});
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("审计日志 metadata 不是合法 JSON", exception);
        }
    }
}

package com.yangaobo.expense.backend.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangaobo.expense.backend.application.event.ExpenseCaseEventRepository;
import com.yangaobo.expense.common.event.ExpenseWorkflowEvent;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcExpenseCaseEventRepository
        implements ExpenseCaseEventRepository {

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;

    public JdbcExpenseCaseEventRepository(
            JdbcClient jdbcClient, ObjectMapper objectMapper) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public ExpenseWorkflowEvent append(
            UUID caseId,
            String type,
            Map<String, Object> payload,
            Instant occurredAt) {
        UUID eventId = UUID.randomUUID();
        long sequence =
                jdbcClient
                        .sql(
                                """
                                INSERT INTO expense_case_event (
                                    event_id, case_id, event_type, payload,
                                    occurred_at
                                ) VALUES (
                                    :eventId, :caseId, :eventType,
                                    CAST(:payload AS jsonb), :occurredAt
                                )
                                RETURNING sequence
                                """)
                        .param("eventId", eventId)
                        .param("caseId", caseId)
                        .param("eventType", type)
                        .param("payload", write(payload))
                        .param("occurredAt", Timestamp.from(occurredAt))
                        .query(Long.class)
                        .single();
        return new ExpenseWorkflowEvent(
                eventId, caseId, type, sequence, occurredAt, payload);
    }

    @Override
    public List<ExpenseWorkflowEvent> findAfter(
            UUID caseId, long afterSequence, int limit) {
        return jdbcClient
                .sql(
                        """
                        SELECT event_id, case_id, event_type, sequence,
                               occurred_at, payload::text
                        FROM expense_case_event
                        WHERE case_id = :caseId
                          AND sequence > :afterSequence
                        ORDER BY sequence
                        LIMIT :limit
                        """)
                .param("caseId", caseId)
                .param("afterSequence", afterSequence)
                .param("limit", Math.min(Math.max(limit, 1), 1000))
                .query(
                        (rs, row) ->
                                new ExpenseWorkflowEvent(
                                        rs.getObject("event_id", UUID.class),
                                        rs.getObject("case_id", UUID.class),
                                        rs.getString("event_type"),
                                        rs.getLong("sequence"),
                                        rs.getTimestamp("occurred_at").toInstant(),
                                        read(rs.getString("payload"))))
                .list();
    }

    @Override
    public OptionalLong findSequence(UUID caseId, UUID eventId) {
        return jdbcClient
                .sql(
                        """
                        SELECT sequence
                        FROM expense_case_event
                        WHERE case_id = :caseId AND event_id = :eventId
                        """)
                .param("caseId", caseId)
                .param("eventId", eventId)
                .query(Long.class)
                .optional()
                .map(OptionalLong::of)
                .orElseGet(OptionalLong::empty);
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Case Event 序列化失败", exception);
        }
    }

    private Map<String, Object> read(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Case Event 不是合法 JSON", exception);
        }
    }
}

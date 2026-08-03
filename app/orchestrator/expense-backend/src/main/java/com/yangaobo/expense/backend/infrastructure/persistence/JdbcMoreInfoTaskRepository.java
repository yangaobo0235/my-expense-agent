package com.yangaobo.expense.backend.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangaobo.expense.backend.application.workflow.MoreInfoTaskRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcMoreInfoTaskRepository implements MoreInfoTaskRepository {
    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;

    public JdbcMoreInfoTaskRepository(JdbcClient jdbcClient, ObjectMapper objectMapper) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public MoreInfoTask create(
            UUID caseId, UUID runId, List<String> requiredMaterials, List<String> reasonCodes,
            String requestedBy, Instant dueAt, Instant now) {
        Optional<MoreInfoTask> existing = findOpenByCaseId(caseId);
        if (existing.isPresent()) {
            return existing.get();
        }
        UUID id = UUID.randomUUID();
        jdbcClient.sql("""
                        INSERT INTO expense_more_info_task (
                            id, case_id, run_id, required_materials, reason_codes,
                            status, requested_by, due_at, created_at
                        ) VALUES (
                            :id, :caseId, :runId, CAST(:materials AS jsonb), CAST(:reasons AS jsonb),
                            'OPEN', :requestedBy, :dueAt, :createdAt
                        )
                        """)
                .param("id", id).param("caseId", caseId).param("runId", runId)
                .param("materials", json(requiredMaterials)).param("reasons", json(reasonCodes))
                .param("requestedBy", requestedBy).param("dueAt", dueAt == null ? null : Timestamp.from(dueAt))
                .param("createdAt", Timestamp.from(now)).update();
        return findById(id).orElseThrow();
    }

    @Override
    public Optional<MoreInfoTask> findById(UUID taskId) {
        return jdbcClient.sql("SELECT * FROM expense_more_info_task WHERE id = :id")
                .param("id", taskId).query(this::map).optional();
    }

    @Override
    public Optional<MoreInfoTask> findOpenByCaseId(UUID caseId) {
        return jdbcClient.sql("""
                        SELECT * FROM expense_more_info_task
                        WHERE case_id = :caseId AND status = 'OPEN'
                        """)
                .param("caseId", caseId).query(this::map).optional();
    }

    @Override
    public void markSubmitted(UUID taskId, int documentVersion, Instant now) {
        int updated = jdbcClient.sql("""
                        UPDATE expense_more_info_task
                        SET status = 'SUBMITTED', submitted_document_version = :version
                        WHERE id = :id AND status = 'OPEN'
                        """)
                .param("version", documentVersion).param("id", taskId).update();
        if (updated != 1) {
            throw new IllegalStateException("补材料任务已提交或不存在");
        }
    }

    @Override
    public void complete(UUID taskId, Instant now) {
        jdbcClient.sql("""
                        UPDATE expense_more_info_task
                        SET status = 'COMPLETED', completed_at = :completedAt
                        WHERE id = :id AND status = 'SUBMITTED'
                        """)
                .param("completedAt", Timestamp.from(now)).param("id", taskId).update();
    }

    private MoreInfoTask map(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        Timestamp due = rs.getTimestamp("due_at");
        Timestamp completed = rs.getTimestamp("completed_at");
        return new MoreInfoTask(
                rs.getObject("id", UUID.class), rs.getObject("case_id", UUID.class),
                rs.getObject("run_id", UUID.class), list(rs.getString("required_materials")),
                list(rs.getString("reason_codes")), rs.getString("status"),
                rs.getString("requested_by"), due == null ? null : due.toInstant(),
                (Integer) rs.getObject("submitted_document_version"),
                completed == null ? null : completed.toInstant(), rs.getTimestamp("created_at").toInstant());
    }

    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value == null ? List.of() : value); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("补材料任务序列化失败", exception); }
    }

    private List<String> list(String value) {
        try { return objectMapper.readValue(value, new TypeReference<>() {}); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("补材料任务不是合法 JSON", exception); }
    }
}

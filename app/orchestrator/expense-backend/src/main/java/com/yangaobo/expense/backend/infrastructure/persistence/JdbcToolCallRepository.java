package com.yangaobo.expense.backend.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangaobo.expense.backend.application.settlement.ToolCallRepository;
import com.yangaobo.expense.common.error.ExpenseFlowErrorCode;
import com.yangaobo.expense.common.error.ExpenseFlowException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcToolCallRepository implements ToolCallRepository {

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;

    public JdbcToolCallRepository(
            JdbcClient jdbcClient, ObjectMapper objectMapper) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<ToolCall> find(String toolName, String requestId) {
        return jdbcClient
                .sql(
                        """
                        SELECT id, case_id, tool_name, request_id, status,
                               output_data::text
                        FROM expense_tool_call
                        WHERE tool_name = :toolName
                          AND request_id = :requestId
                        """)
                .param("toolName", toolName)
                .param("requestId", requestId)
                .query(
                        (rs, row) ->
                                new ToolCall(
                                        rs.getObject("id", UUID.class),
                                        rs.getObject("case_id", UUID.class),
                                        rs.getString("tool_name"),
                                        rs.getString("request_id"),
                                        rs.getString("status"),
                                        read(rs.getString("output_data"))))
                .optional();
    }

    @Override
    public List<ToolCallDetail> findByCaseId(UUID caseId) {
        return jdbcClient
                .sql(
                        """
                        SELECT id, tool_name, write_operation, status, output_data::text,
                               duration_ms, error_code, approval_reference,
                               created_at, completed_at
                        FROM expense_tool_call
                        WHERE case_id = :caseId
                        ORDER BY created_at, id
                        """)
                .param("caseId", caseId)
                .query(
                        (rs, row) ->
                                new ToolCallDetail(
                                        rs.getObject("id", UUID.class),
                                        rs.getString("tool_name"),
                                        rs.getBoolean("write_operation"),
                                        rs.getString("status"),
                                        read(rs.getString("output_data")),
                                        rs.getLong("duration_ms"),
                                        rs.getString("error_code"),
                                        rs.getString("approval_reference"),
                                        rs.getTimestamp("created_at").toInstant(),
                                        rs.getTimestamp("completed_at") == null
                                                ? null
                                                : rs.getTimestamp("completed_at").toInstant()))
                .list();
    }

    @Override
    public ToolCall start(
            UUID caseId,
            String toolName,
            String requestId,
            String inputHash,
            String actorSubject,
            String approvalReference,
            Instant now) {
        UUID id = UUID.randomUUID();
        int inserted =
                jdbcClient
                        .sql(
                                """
                                INSERT INTO expense_tool_call (
                                    id, run_id, step_id, case_id, request_id,
                                    tool_name, write_operation, input_hash, status,
                                    approval_reference, actor_subject, created_at
                                ) VALUES (
                                    :id, NULL, NULL, :caseId, :requestId,
                                    :toolName, TRUE, :inputHash, 'RUNNING',
                                    :approvalReference, :actorSubject, :createdAt
                                )
                                ON CONFLICT (tool_name, request_id) DO NOTHING
                                """)
                        .param("id", id)
                        .param("caseId", caseId)
                        .param("requestId", requestId)
                        .param("toolName", toolName)
                        .param("inputHash", inputHash)
                        .param("approvalReference", approvalReference)
                        .param("actorSubject", actorSubject)
                        .param("createdAt", Timestamp.from(now))
                        .update();
        if (inserted == 1) {
            return new ToolCall(
                    id, caseId, toolName, requestId, "RUNNING", Map.of());
        }

        ToolCall existing =
                find(toolName, requestId)
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Tool 调用幂等记录不存在"));
        if ("RUNNING".equals(existing.status())) {
            throw new ExpenseFlowException(
                    ExpenseFlowErrorCode.DUPLICATE_REQUEST,
                    "相同 requestId 的 Tool 调用正在处理中");
        }
        if ("FAILED".equals(existing.status())) {
            jdbcClient
                    .sql(
                            """
                            UPDATE expense_tool_call
                            SET status = 'RUNNING', error_code = NULL,
                                created_at = :startedAt, completed_at = NULL
                            WHERE id = :id
                            """)
                    .param("startedAt", Timestamp.from(now))
                    .param("id", existing.id())
                    .update();
            return new ToolCall(
                    existing.id(),
                    caseId,
                    toolName,
                    requestId,
                    "RUNNING",
                    Map.of());
        }
        return existing;
    }

    @Override
    public void succeed(
            UUID id,
            String outputHash,
            Map<String, Object> output,
            long durationMs,
            Instant now) {
        jdbcClient
                .sql(
                        """
                        UPDATE expense_tool_call
                        SET status = 'SUCCEEDED', output_hash = :outputHash,
                            output_data = CAST(:output AS jsonb),
                            duration_ms = :durationMs, completed_at = :completedAt
                        WHERE id = :id
                        """)
                .param("outputHash", outputHash)
                .param("output", write(output))
                .param("durationMs", durationMs)
                .param("completedAt", Timestamp.from(now))
                .param("id", id)
                .update();
    }

    @Override
    public void fail(
            UUID id,
            String errorCode,
            long durationMs,
            Instant now) {
        jdbcClient
                .sql(
                        """
                        UPDATE expense_tool_call
                        SET status = 'FAILED', error_code = :errorCode,
                            duration_ms = :durationMs, completed_at = :completedAt
                        WHERE id = :id
                        """)
                .param("errorCode", errorCode)
                .param("durationMs", durationMs)
                .param("completedAt", Timestamp.from(now))
                .param("id", id)
                .update();
    }

    private Map<String, Object> read(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Tool 输出不是合法 JSON", exception);
        }
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Tool 输出序列化失败", exception);
        }
    }
}

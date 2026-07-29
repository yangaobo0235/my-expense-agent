package com.yangaobo.expense.backend.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangaobo.expense.backend.application.workflow.WorkflowRunRepository;
import com.yangaobo.expense.backend.application.workflow.WorkflowCommandType;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcWorkflowRunRepository implements WorkflowRunRepository {

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;

    public JdbcWorkflowRunRepository(JdbcClient jdbcClient, ObjectMapper objectMapper) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public WorkflowRun startOrLoad(UUID caseId, String requestId) {
        return startOrLoad(caseId, requestId, WorkflowCommandType.REVIEW,
                currentDocumentVersion(caseId), null, null);
    }

    @Override
    public WorkflowRun startOrLoad(
            UUID caseId,
            String requestId,
            WorkflowCommandType commandType,
            int documentVersion,
            UUID previousRunId,
            String reopenReason) {
        Optional<WorkflowRun> existing =
                jdbcClient
                        .sql(
                                """
                                SELECT id, case_id, request_id, status, command_type,
                                       document_version, previous_run_id, reopen_reason
                                FROM expense_agent_run
                                WHERE case_id = :caseId AND request_id = :requestId
                                """)
                        .param("caseId", caseId)
                        .param("requestId", requestId)
                        .query(
                                (rs, row) ->
                                        new WorkflowRun(
                                                rs.getObject("id", UUID.class),
                                                rs.getObject("case_id", UUID.class),
                                                rs.getString("request_id"),
                                                rs.getString("status"),
                                                WorkflowCommandType.valueOf(rs.getString("command_type")),
                                                rs.getInt("document_version"),
                                                rs.getObject("previous_run_id", UUID.class),
                                                rs.getString("reopen_reason")))
                        .optional();
        if (existing.isPresent()) {
            return existing.get();
        }
        UUID runId = UUID.randomUUID();
        jdbcClient
                .sql(
                        """
                        INSERT INTO expense_agent_run (
                            id, case_id, request_id, run_type, status, command_type,
                            document_version, previous_run_id, reopen_reason, started_at
                        ) VALUES (
                            :id, :caseId, :requestId, 'EXPENSE_REVIEW', 'RUNNING', :commandType,
                            :documentVersion, :previousRunId, :reopenReason, :startedAt
                        )
                        """)
                .param("id", runId)
                .param("caseId", caseId)
                .param("requestId", requestId)
                .param("commandType", commandType.name())
                .param("documentVersion", documentVersion)
                .param("previousRunId", previousRunId)
                .param("reopenReason", reopenReason)
                .param("startedAt", Timestamp.from(Instant.now()))
                .update();
        return new WorkflowRun(runId, caseId, requestId, "RUNNING", commandType,
                documentVersion, previousRunId, reopenReason);
    }

    @Override
    public int currentDocumentVersion(UUID caseId) {
        return jdbcClient.sql("""
                        SELECT COALESCE(MAX(version), 1)
                        FROM expense_document_version
                        WHERE case_id = :caseId
                        """)
                .param("caseId", caseId)
                .query(Integer.class)
                .single();
    }

    @Override
    public Optional<Map<String, Object>> successfulStep(UUID runId, String stepName) {
        return jdbcClient
                .sql(
                        """
                        SELECT state_data
                        FROM (
                            SELECT state_data::text AS state_data,
                                   0 AS source_priority,
                                   checkpoint_version AS saved_version
                            FROM expense_workflow_checkpoint
                            WHERE run_id = :runId AND node_name = :stepName
                            UNION ALL
                            SELECT output_data::text AS state_data,
                                   1 AS source_priority,
                                   attempt AS saved_version
                            FROM expense_agent_step
                            WHERE run_id = :runId AND step_name = :stepName
                              AND status = 'SUCCEEDED'
                        ) saved_state
                        ORDER BY source_priority, saved_version DESC
                        LIMIT 1
                        """)
                .param("runId", runId)
                .param("stepName", stepName)
                .query((rs, row) -> readMap(rs.getString(1)))
                .optional();
    }

    @Override
    public Optional<WorkflowRunDetail> latestRun(UUID caseId) {
        return jdbcClient
                .sql(
                        """
                        SELECT id, case_id, request_id, status, started_at, completed_at,
                               error_code, error_message, command_type, document_version,
                               previous_run_id, reopen_reason, route_action, waiting_reason
                        FROM expense_agent_run
                        WHERE case_id = :caseId
                        ORDER BY started_at DESC, id DESC
                        LIMIT 1
                        """)
                .param("caseId", caseId)
                .query(
                        (rs, row) ->
                                new WorkflowRunDetail(
                                        rs.getObject("id", UUID.class),
                                        rs.getObject("case_id", UUID.class),
                                        rs.getString("request_id"),
                                        rs.getString("status"),
                                        instant(rs.getTimestamp("started_at")),
                                        instant(rs.getTimestamp("completed_at")),
                                        rs.getString("error_code"),
                                        rs.getString("error_message"),
                                        WorkflowCommandType.valueOf(rs.getString("command_type")),
                                        rs.getInt("document_version"),
                                        rs.getObject("previous_run_id", UUID.class),
                                        rs.getString("reopen_reason"),
                                        rs.getString("route_action"),
                                        rs.getString("waiting_reason")))
                .optional();
    }

    @Override
    public Optional<WorkflowRunDetail> findRun(UUID runId) {
        return jdbcClient.sql("""
                        SELECT id, case_id, request_id, status, started_at, completed_at,
                               error_code, error_message, command_type, document_version,
                               previous_run_id, reopen_reason, route_action, waiting_reason
                        FROM expense_agent_run WHERE id = :runId
                        """)
                .param("runId", runId)
                .query(this::mapRunDetail)
                .optional();
    }

    @Override
    public List<WorkflowRunDetail> findByCaseId(UUID caseId) {
        return jdbcClient.sql("""
                        SELECT id, case_id, request_id, status, started_at, completed_at,
                               error_code, error_message, command_type, document_version,
                               previous_run_id, reopen_reason, route_action, waiting_reason
                        FROM expense_agent_run WHERE case_id = :caseId
                        ORDER BY started_at, id
                        """)
                .param("caseId", caseId)
                .query(this::mapRunDetail)
                .list();
    }

    @Override
    public List<WorkflowStep> steps(UUID runId) {
        return jdbcClient
                .sql(
                        """
                        SELECT id, step_name, attempt, status, output_data::text,
                               started_at, completed_at, error_code, error_message
                        FROM expense_agent_step
                        WHERE run_id = :runId
                        ORDER BY started_at NULLS LAST, step_name, attempt
                        """)
                .param("runId", runId)
                .query(
                        (rs, row) ->
                                new WorkflowStep(
                                        rs.getObject("id", UUID.class),
                                        rs.getString("step_name"),
                                        rs.getInt("attempt"),
                                        rs.getString("status"),
                                        readMap(rs.getString("output_data")),
                                        instant(rs.getTimestamp("started_at")),
                                        instant(rs.getTimestamp("completed_at")),
                                        rs.getString("error_code"),
                                        rs.getString("error_message")))
                .list();
    }

    @Override
    public List<WorkflowRunDetail> recentRuns(int limit) {
        return jdbcClient
                .sql(
                        """
                        SELECT id, case_id, request_id, status, started_at, completed_at,
                               error_code, error_message, command_type, document_version,
                               previous_run_id, reopen_reason, route_action, waiting_reason
                        FROM expense_agent_run
                        ORDER BY started_at DESC, id DESC
                        LIMIT :limit
                        """)
                .param("limit", Math.max(1, Math.min(limit, 100)))
                .query(this::mapRunDetail)
                .list();
    }

    @Override
    public void updateOutcome(UUID runId, String routeAction, String waitingReason) {
        jdbcClient.sql("""
                        UPDATE expense_agent_run
                        SET route_action = :routeAction, waiting_reason = :waitingReason
                        WHERE id = :runId
                        """)
                .param("routeAction", routeAction)
                .param("waitingReason", waitingReason)
                .param("runId", runId)
                .update();
    }

    @Override
    public List<DocumentVersion> documentVersions(UUID caseId) {
        return jdbcClient.sql("""
                        SELECT case_id, version, document_id, sha256, source_type,
                               uploaded_by, replaces_version, created_at
                        FROM expense_document_version
                        WHERE case_id = :caseId ORDER BY version
                        """)
                .param("caseId", caseId)
                .query((rs, row) -> new DocumentVersion(
                        rs.getObject("case_id", UUID.class), rs.getInt("version"),
                        rs.getObject("document_id", UUID.class), rs.getString("sha256"),
                        rs.getString("source_type"), rs.getString("uploaded_by"),
                        (Integer) rs.getObject("replaces_version"),
                        rs.getTimestamp("created_at").toInstant()))
                .list();
    }

    @Override
    public void startStep(
            UUID runId, UUID caseId, String stepName, int attempt, String inputHash) {
        jdbcClient
                .sql(
                        """
                        INSERT INTO expense_agent_step (
                            id, run_id, case_id, step_name, attempt, status,
                            input_hash, started_at
                        ) VALUES (
                            :id, :runId, :caseId, :stepName, :attempt, 'RUNNING',
                            :inputHash, CURRENT_TIMESTAMP
                        )
                        ON CONFLICT (run_id, step_name, attempt)
                        DO UPDATE SET status = 'RUNNING', started_at = CURRENT_TIMESTAMP,
                                      completed_at = NULL, error_code = NULL,
                                      error_message = NULL
                        """)
                .param("id", UUID.randomUUID())
                .param("runId", runId)
                .param("caseId", caseId)
                .param("stepName", stepName)
                .param("attempt", attempt)
                .param("inputHash", inputHash)
                .update();
    }

    @Override
    @Transactional
    public void succeedStep(
            UUID runId, String stepName, int attempt, Map<String, Object> output) {
        String outputJson = writeJson(output);
        jdbcClient
                .sql(
                        """
                        UPDATE expense_agent_step
                        SET status = 'SUCCEEDED', output_data = CAST(:output AS jsonb),
                            completed_at = CURRENT_TIMESTAMP
                        WHERE run_id = :runId AND step_name = :stepName
                          AND attempt = :attempt
                        """)
                .param("output", outputJson)
                .param("runId", runId)
                .param("stepName", stepName)
                .param("attempt", attempt)
                .update();
        jdbcClient
                .sql(
                        """
                        INSERT INTO expense_workflow_checkpoint (
                            id, run_id, case_id, node_name, checkpoint_version,
                            state_data, created_at
                        )
                        SELECT :id, run_id, case_id, step_name, attempt,
                               CAST(:stateData AS jsonb), CURRENT_TIMESTAMP
                        FROM expense_agent_step
                        WHERE run_id = :runId AND step_name = :stepName
                          AND attempt = :attempt AND status = 'SUCCEEDED'
                        ON CONFLICT (run_id, node_name, checkpoint_version)
                        DO UPDATE SET state_data = EXCLUDED.state_data,
                                      created_at = EXCLUDED.created_at
                        """)
                .param("id", UUID.randomUUID())
                .param("stateData", outputJson)
                .param("runId", runId)
                .param("stepName", stepName)
                .param("attempt", attempt)
                .update();
    }

    @Override
    public void failStep(
            UUID runId,
            String stepName,
            int attempt,
            String errorCode,
            String errorMessage) {
        jdbcClient
                .sql(
                        """
                        UPDATE expense_agent_step
                        SET status = 'FAILED', error_code = :errorCode,
                            error_message = :errorMessage, completed_at = CURRENT_TIMESTAMP
                        WHERE run_id = :runId AND step_name = :stepName
                          AND attempt = :attempt
                        """)
                .param("errorCode", errorCode)
                .param("errorMessage", truncate(errorMessage))
                .param("runId", runId)
                .param("stepName", stepName)
                .param("attempt", attempt)
                .update();
    }

    @Override
    public void succeedRun(UUID runId) {
        completeRun(runId, "SUCCEEDED", null, null);
    }

    @Override
    public void failRun(UUID runId, String errorCode, String errorMessage) {
        completeRun(runId, "FAILED", errorCode, errorMessage);
    }

    private void completeRun(
            UUID runId, String status, String errorCode, String errorMessage) {
        jdbcClient
                .sql(
                        """
                        UPDATE expense_agent_run
                        SET status = :status, completed_at = CURRENT_TIMESTAMP,
                            error_code = :errorCode, error_message = :errorMessage
                        WHERE id = :runId
                        """)
                .param("status", status)
                .param("errorCode", errorCode)
                .param("errorMessage", errorMessage == null ? null : truncate(errorMessage))
                .param("runId", runId)
                .update();
    }

    private Map<String, Object> readMap(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("工作流步骤输出不是合法 JSON", exception);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("工作流步骤输出序列化失败", exception);
        }
    }

    private static String truncate(String value) {
        if (value == null || value.isBlank()) {
            return "未知错误";
        }
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private WorkflowRunDetail mapRunDetail(java.sql.ResultSet rs, int row)
            throws java.sql.SQLException {
        return new WorkflowRunDetail(
                rs.getObject("id", UUID.class),
                rs.getObject("case_id", UUID.class),
                rs.getString("request_id"),
                rs.getString("status"),
                instant(rs.getTimestamp("started_at")),
                instant(rs.getTimestamp("completed_at")),
                rs.getString("error_code"),
                rs.getString("error_message"),
                WorkflowCommandType.valueOf(rs.getString("command_type")),
                rs.getInt("document_version"),
                rs.getObject("previous_run_id", UUID.class),
                rs.getString("reopen_reason"),
                rs.getString("route_action"),
                rs.getString("waiting_reason"));
    }
}

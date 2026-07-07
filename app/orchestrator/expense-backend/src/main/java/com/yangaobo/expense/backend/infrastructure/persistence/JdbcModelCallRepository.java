package com.yangaobo.expense.backend.infrastructure.persistence;

import com.yangaobo.expense.backend.application.observability.ModelCallRepository;
import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcModelCallRepository implements ModelCallRepository {

    private final JdbcClient jdbcClient;

    public JdbcModelCallRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public void save(ModelCallRecord record) {
        jdbcClient
                .sql(
                        """
                        INSERT INTO expense_model_call (
                            id, case_id, run_id, step_name, model_name, prompt_version,
                            prompt_hash, input_hash, output_hash, prompt_tokens,
                            completion_tokens, total_tokens, latency_ms, retry_count,
                            status, error_code, created_at
                        ) VALUES (
                            :id, :caseId, :runId, :stepName, :modelName, :promptVersion,
                            :promptHash, :inputHash, :outputHash, :promptTokens,
                            :completionTokens, :totalTokens, :latencyMs, :retryCount,
                            :status, :errorCode, :createdAt
                        )
                        """)
                .param("id", record.id())
                .param("caseId", record.caseId())
                .param("runId", record.runId())
                .param("stepName", record.stepName())
                .param("modelName", record.modelName())
                .param("promptVersion", record.promptVersion())
                .param("promptHash", record.promptHash())
                .param("inputHash", record.inputHash())
                .param("outputHash", record.outputHash())
                .param("promptTokens", record.promptTokens())
                .param("completionTokens", record.completionTokens())
                .param("totalTokens", record.totalTokens())
                .param("latencyMs", record.latencyMs())
                .param("retryCount", record.retryCount())
                .param("status", record.status())
                .param("errorCode", record.errorCode())
                .param("createdAt", Timestamp.from(record.createdAt()))
                .update();
    }

    @Override
    public List<ModelCallRecord> recent(int limit) {
        return jdbcClient
                .sql(
                        """
                        SELECT id, case_id, run_id, step_name, model_name, prompt_version,
                               prompt_hash, input_hash, output_hash, prompt_tokens,
                               completion_tokens, total_tokens, latency_ms, retry_count,
                               status, error_code, created_at
                        FROM expense_model_call
                        ORDER BY created_at DESC, id DESC
                        LIMIT :limit
                        """)
                .param("limit", Math.max(1, Math.min(limit, 100)))
                .query(
                        (rs, row) ->
                                new ModelCallRecord(
                                        rs.getObject("id", UUID.class),
                                        rs.getObject("case_id", UUID.class),
                                        rs.getObject("run_id", UUID.class),
                                        rs.getString("step_name"),
                                        rs.getString("model_name"),
                                        rs.getString("prompt_version"),
                                        rs.getString("prompt_hash"),
                                        rs.getString("input_hash"),
                                        rs.getString("output_hash"),
                                        rs.getInt("prompt_tokens"),
                                        rs.getInt("completion_tokens"),
                                        rs.getInt("total_tokens"),
                                        rs.getLong("latency_ms"),
                                        rs.getInt("retry_count"),
                                        rs.getString("status"),
                                        rs.getString("error_code"),
                                        rs.getTimestamp("created_at").toInstant()))
                .list();
    }

    @Override
    public List<ModelCallRecord> findByCaseId(UUID caseId, int limit) {
        return jdbcClient
                .sql(
                        """
                        SELECT id, case_id, run_id, step_name, model_name, prompt_version,
                               prompt_hash, input_hash, output_hash, prompt_tokens,
                               completion_tokens, total_tokens, latency_ms, retry_count,
                               status, error_code, created_at
                        FROM expense_model_call
                        WHERE case_id = :caseId
                        ORDER BY created_at DESC, id DESC
                        LIMIT :limit
                        """)
                .param("caseId", caseId)
                .param("limit", Math.max(1, Math.min(limit, 100)))
                .query(this::record)
                .list();
    }

    @Override
    public ModelCallSummary summary() {
        SummaryRow row =
                jdbcClient
                        .sql(
                                """
                                SELECT COUNT(*) AS total_calls,
                                       COALESCE(AVG(latency_ms), 0) AS average_latency_ms,
                                       COALESCE(percentile_cont(0.95) WITHIN GROUP (ORDER BY latency_ms), 0) AS p95_latency_ms,
                                       COALESCE(SUM(total_tokens), 0) AS total_tokens,
                                       COALESCE(SUM(CASE WHEN status = 'SUCCEEDED' THEN 1 ELSE 0 END), 0) AS succeeded_calls
                                FROM expense_model_call
                                """)
                        .query(
                                (rs, ignored) ->
                                        new SummaryRow(
                                                rs.getLong("total_calls"),
                                                rs.getDouble("average_latency_ms"),
                                                rs.getLong("p95_latency_ms"),
                                                rs.getLong("total_tokens"),
                                                rs.getLong("succeeded_calls")))
                        .single();
        return new ModelCallSummary(
                row.totalCalls(),
                row.totalCalls() == 0 ? 0 : (double) row.succeededCalls() / row.totalCalls(),
                row.averageLatencyMs(),
                row.p95LatencyMs(),
                row.totalTokens(),
                grouped("model_name", "status IS NOT NULL"),
                grouped("step_name", "status = 'FAILED'"));
    }

    private ModelCallRecord record(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new ModelCallRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("case_id", UUID.class),
                rs.getObject("run_id", UUID.class),
                rs.getString("step_name"),
                rs.getString("model_name"),
                rs.getString("prompt_version"),
                rs.getString("prompt_hash"),
                rs.getString("input_hash"),
                rs.getString("output_hash"),
                rs.getInt("prompt_tokens"),
                rs.getInt("completion_tokens"),
                rs.getInt("total_tokens"),
                rs.getLong("latency_ms"),
                rs.getInt("retry_count"),
                rs.getString("status"),
                rs.getString("error_code"),
                rs.getTimestamp("created_at").toInstant());
    }

    private Map<String, Long> grouped(String column, String where) {
        Map<String, Long> values = new LinkedHashMap<>();
        jdbcClient
                .sql(
                        "SELECT "
                                + column
                                + " AS label, COUNT(*) AS count FROM expense_model_call WHERE "
                                + where
                                + " GROUP BY "
                                + column
                                + " ORDER BY count DESC, label")
                .query(
                        (rs, row) -> {
                            values.put(rs.getString("label"), rs.getLong("count"));
                            return row;
                        })
                .list();
        return Map.copyOf(values);
    }

    private record SummaryRow(
            long totalCalls,
            double averageLatencyMs,
            long p95LatencyMs,
            long totalTokens,
            long succeededCalls) {}
}

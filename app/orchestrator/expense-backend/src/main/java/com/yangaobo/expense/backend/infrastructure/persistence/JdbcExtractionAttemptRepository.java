package com.yangaobo.expense.backend.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangaobo.expense.backend.application.extraction.ExtractionAttemptRepository;
import com.yangaobo.expense.backend.application.extraction.ExtractionValidationError;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcExtractionAttemptRepository implements ExtractionAttemptRepository {

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;

    public JdbcExtractionAttemptRepository(JdbcClient jdbcClient, ObjectMapper objectMapper) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(ExtractionAttempt attempt) {
        jdbcClient.sql("""
                        INSERT INTO expense_extraction_attempt (
                            id, document_id, attempt_no, attempt_type, prompt_version,
                            model_name, validation_errors, output_hash, token_usage,
                            latency_ms, network_retry_count, status, created_at
                        ) VALUES (
                            :id, :documentId, :attemptNo, :attemptType, :promptVersion,
                            :modelName, CAST(:errors AS jsonb), :outputHash, :tokenUsage,
                            :latencyMs, :networkRetryCount, :status, :createdAt
                        )
                        ON CONFLICT (document_id, attempt_no) DO NOTHING
                        """)
                .param("id", attempt.id())
                .param("documentId", attempt.documentId())
                .param("attemptNo", attempt.attemptNo())
                .param("attemptType", attempt.attemptType())
                .param("promptVersion", attempt.promptVersion())
                .param("modelName", attempt.modelName())
                .param("errors", write(attempt.validationErrors()))
                .param("outputHash", attempt.outputHash())
                .param("tokenUsage", attempt.tokenUsage())
                .param("latencyMs", attempt.latencyMs())
                .param("networkRetryCount", attempt.networkRetryCount())
                .param("status", attempt.status())
                .param("createdAt", Timestamp.from(attempt.createdAt()))
                .update();
    }

    @Override
    public List<ExtractionAttempt> findByDocumentId(UUID documentId) {
        return jdbcClient.sql("""
                        SELECT id, document_id, attempt_no, attempt_type, prompt_version,
                               model_name, validation_errors::text, output_hash, token_usage,
                               latency_ms, network_retry_count, status, created_at
                        FROM expense_extraction_attempt
                        WHERE document_id = :documentId
                        ORDER BY attempt_no
                        """)
                .param("documentId", documentId)
                .query((rs, row) -> new ExtractionAttempt(
                        rs.getObject("id", UUID.class),
                        rs.getObject("document_id", UUID.class),
                        rs.getInt("attempt_no"),
                        rs.getString("attempt_type"),
                        rs.getString("prompt_version"),
                        rs.getString("model_name"),
                        read(rs.getString("validation_errors")),
                        rs.getString("output_hash"),
                        rs.getInt("token_usage"),
                        rs.getLong("latency_ms"),
                        rs.getInt("network_retry_count"),
                        rs.getString("status"),
                        rs.getTimestamp("created_at").toInstant()))
                .list();
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("抽取修正记录序列化失败", exception);
        }
    }

    private List<ExtractionValidationError> read(String value) {
        try {
            return objectMapper.readValue(value, new TypeReference<>() {});
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("抽取修正记录不是合法 JSON", exception);
        }
    }
}

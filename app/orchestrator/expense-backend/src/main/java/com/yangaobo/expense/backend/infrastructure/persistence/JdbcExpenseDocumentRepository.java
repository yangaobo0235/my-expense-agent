package com.yangaobo.expense.backend.infrastructure.persistence;

import com.yangaobo.expense.backend.domain.model.ExpenseDocument;
import com.yangaobo.expense.backend.domain.repository.ExpenseDocumentRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangaobo.expense.backend.domain.repository.StoredExtractionResult;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcExpenseDocumentRepository implements ExpenseDocumentRepository {

    private static final RowMapper<ExpenseDocument> ROW_MAPPER =
            JdbcExpenseDocumentRepository::mapDocument;

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;

    public JdbcExpenseDocumentRepository(JdbcClient jdbcClient, ObjectMapper objectMapper) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public ExpenseDocument insert(ExpenseDocument document) {
        jdbcClient
                .sql(
                        """
                        INSERT INTO expense_document (
                            id, case_id, original_filename, content_type, file_size,
                            sha256, object_key, created_at, updated_at
                        ) VALUES (
                            :id, :caseId, :originalFilename, :contentType, :fileSize,
                            :sha256, :objectKey, :createdAt, :updatedAt
                        )
                        """)
                .param("id", document.id())
                .param("caseId", document.caseId())
                .param("originalFilename", document.originalFilename())
                .param("contentType", document.contentType())
                .param("fileSize", document.fileSize())
                .param("sha256", document.sha256())
                .param("objectKey", document.objectKey())
                .param("createdAt", Timestamp.from(document.createdAt()))
                .param("updatedAt", Timestamp.from(document.updatedAt()))
                .update();
        return document;
    }

    @Override
    public Optional<ExpenseDocument> findByCaseIdAndSha256(UUID caseId, String sha256) {
        return jdbcClient
                .sql(
                        """
                        SELECT id, case_id, original_filename, content_type, file_size,
                               sha256, object_key, created_at, updated_at
                        FROM expense_document
                        WHERE case_id = :caseId AND sha256 = :sha256
                        """)
                .param("caseId", caseId)
                .param("sha256", sha256)
                .query(ROW_MAPPER)
                .optional();
    }

    @Override
    public int countByCaseId(UUID caseId) {
        return jdbcClient
                .sql("SELECT count(*)::int FROM expense_document WHERE case_id = :caseId")
                .param("caseId", caseId)
                .query(Integer.class)
                .single();
    }

    @Override
    public Optional<ExpenseDocument> findById(UUID documentId) {
        return jdbcClient
                .sql(
                        """
                        SELECT id, case_id, original_filename, content_type, file_size,
                               sha256, object_key, created_at, updated_at
                        FROM expense_document
                        WHERE id = :documentId
                        """)
                .param("documentId", documentId)
                .query(ROW_MAPPER)
                .optional();
    }

    @Override
    public List<ExpenseDocument> findByCaseId(UUID caseId) {
        return jdbcClient
                .sql(
                        """
                        SELECT id, case_id, original_filename, content_type, file_size,
                               sha256, object_key, created_at, updated_at
                        FROM expense_document
                        WHERE case_id = :caseId
                        ORDER BY created_at, id
                        """)
                .param("caseId", caseId)
                .query(ROW_MAPPER)
                .list();
    }

    @Override
    public List<StoredExtractionResult> findExtractionResultsByCaseId(UUID caseId) {
        return jdbcClient
                .sql(
                        """
                        SELECT document_type, extraction_confidence, extraction_result::text,
                               validation_errors::text, model_name, prompt_version,
                               raw_response_hash, token_usage, extraction_latency_ms,
                               extractor_mode
                        FROM expense_document
                        WHERE case_id = :caseId
                          AND extraction_result <> '{}'::jsonb
                        ORDER BY created_at, id
                        """)
                .param("caseId", caseId)
                .query(
                        (resultSet, rowNumber) ->
                                new StoredExtractionResult(
                                        resultSet.getString("document_type"),
                                        resultSet.getDouble("extraction_confidence"),
                                        resultSet.getString("extraction_result"),
                                        readErrors(resultSet.getString("validation_errors")),
                                        resultSet.getString("model_name"),
                                        resultSet.getString("prompt_version"),
                                        resultSet.getString("raw_response_hash"),
                                        resultSet.getInt("token_usage"),
                                        resultSet.getLong("extraction_latency_ms"),
                                        resultSet.getString("extractor_mode")))
                .list();
    }

    @Override
    public Optional<StoredExtractionResult> findExtractionByDocumentId(UUID documentId) {
        return jdbcClient
                .sql(
                        """
                        SELECT document_type, extraction_confidence, extraction_result::text,
                               validation_errors::text, model_name, prompt_version,
                               raw_response_hash, token_usage, extraction_latency_ms,
                               extractor_mode
                        FROM expense_document
                        WHERE id = :documentId
                          AND extraction_result <> '{}'::jsonb
                        """)
                .param("documentId", documentId)
                .query(
                        (resultSet, rowNumber) ->
                                new StoredExtractionResult(
                                        resultSet.getString("document_type"),
                                        resultSet.getDouble("extraction_confidence"),
                                        resultSet.getString("extraction_result"),
                                        readErrors(resultSet.getString("validation_errors")),
                                        resultSet.getString("model_name"),
                                        resultSet.getString("prompt_version"),
                                        resultSet.getString("raw_response_hash"),
                                        resultSet.getInt("token_usage"),
                                        resultSet.getLong("extraction_latency_ms"),
                                        resultSet.getString("extractor_mode")))
                .optional();
    }

    @Override
    public Optional<StoredExtractionResult> findReusableExtraction(
            String sha256, String promptVersion) {
        return jdbcClient
                .sql(
                        """
                        SELECT document_type, extraction_confidence, extraction_result::text,
                               validation_errors::text, model_name, prompt_version,
                               raw_response_hash, token_usage, extraction_latency_ms,
                               extractor_mode
                        FROM expense_document
                        WHERE sha256 = :sha256
                          AND prompt_version = :promptVersion
                          AND extraction_result <> '{}'::jsonb
                          AND validation_errors = '[]'::jsonb
                        ORDER BY updated_at DESC
                        LIMIT 1
                        """)
                .param("sha256", sha256)
                .param("promptVersion", promptVersion)
                .query(
                        (resultSet, rowNumber) ->
                                new StoredExtractionResult(
                                        resultSet.getString("document_type"),
                                        resultSet.getDouble("extraction_confidence"),
                                        resultSet.getString("extraction_result"),
                                        readErrors(resultSet.getString("validation_errors")),
                                        resultSet.getString("model_name"),
                                        resultSet.getString("prompt_version"),
                                        resultSet.getString("raw_response_hash"),
                                        resultSet.getInt("token_usage"),
                                        resultSet.getLong("extraction_latency_ms"),
                                        resultSet.getString("extractor_mode")))
                .optional();
    }

    @Override
    public void saveExtraction(UUID documentId, StoredExtractionResult result) {
        jdbcClient
                .sql(
                        """
                        UPDATE expense_document
                        SET document_type = :documentType,
                            extraction_confidence = :confidence,
                            extraction_result = CAST(:resultJson AS jsonb),
                            validation_errors = CAST(:validationErrors AS jsonb),
                            model_name = :modelName,
                            prompt_version = :promptVersion,
                            raw_response_hash = :rawResponseHash,
                            token_usage = :tokenUsage,
                            extraction_latency_ms = :extractionLatencyMs,
                            extractor_mode = :extractorMode,
                            updated_at = CURRENT_TIMESTAMP
                        WHERE id = :documentId
                        """)
                .param("documentType", result.documentType())
                .param("confidence", result.confidence())
                .param("resultJson", result.resultJson())
                .param("validationErrors", writeErrors(result.validationErrors()))
                .param("modelName", result.modelName())
                .param("promptVersion", result.promptVersion())
                .param("rawResponseHash", result.rawResponseHash())
                .param("tokenUsage", result.tokenUsage())
                .param("extractionLatencyMs", result.extractionLatencyMs())
                .param("extractorMode", result.extractorMode())
                .param("documentId", documentId)
                .update();
    }

    private List<String> readErrors(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored validation errors are invalid JSON", exception);
        }
    }

    private String writeErrors(List<String> errors) {
        try {
            return objectMapper.writeValueAsString(errors);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize validation errors", exception);
        }
    }

    private static ExpenseDocument mapDocument(ResultSet resultSet, int rowNumber)
            throws SQLException {
        return new ExpenseDocument(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("case_id", UUID.class),
                resultSet.getString("original_filename"),
                resultSet.getString("content_type"),
                resultSet.getLong("file_size"),
                resultSet.getString("sha256"),
                resultSet.getString("object_key"),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant());
    }
}

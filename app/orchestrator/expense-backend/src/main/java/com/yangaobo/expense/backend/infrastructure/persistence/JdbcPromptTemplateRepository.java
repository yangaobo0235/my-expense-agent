package com.yangaobo.expense.backend.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangaobo.expense.backend.application.prompt.PromptChangeRequest;
import com.yangaobo.expense.backend.application.prompt.PromptChangeRequestStatus;
import com.yangaobo.expense.backend.application.prompt.PromptChangeRequestType;
import com.yangaobo.expense.backend.application.prompt.PromptStatus;
import com.yangaobo.expense.backend.application.prompt.PromptTemplate;
import com.yangaobo.expense.backend.application.prompt.PromptTemplateRepository;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcPromptTemplateRepository implements PromptTemplateRepository {

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;

    public JdbcPromptTemplateRepository(JdbcClient jdbcClient, ObjectMapper objectMapper) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public PromptTemplate save(PromptTemplate template) {
        jdbcClient
                .sql(
                        """
                        INSERT INTO expense_prompt_template (
                            id, prompt_key, version, name, description, content,
                            variable_schema, model_name, temperature, max_tokens,
                            status, prompt_hash, created_by, updated_by, approved_by,
                            created_at, updated_at, approved_at, activated_at, replaced_version
                        ) VALUES (
                            :id, :promptKey, :version, :name, :description, :content,
                            CAST(:variableSchema AS jsonb), :modelName, :temperature, :maxTokens,
                            :status, :promptHash, :createdBy, :updatedBy, :approvedBy,
                            :createdAt, :updatedAt, :approvedAt, :activatedAt, :replacedVersion
                        )
                        """)
                .params(params(template))
                .update();
        return template;
    }

    @Override
    public PromptTemplate update(PromptTemplate template) {
        jdbcClient
                .sql(
                        """
                        UPDATE expense_prompt_template
                           SET name = :name,
                               description = :description,
                               content = :content,
                               variable_schema = CAST(:variableSchema AS jsonb),
                               model_name = :modelName,
                               temperature = :temperature,
                               max_tokens = :maxTokens,
                               status = :status,
                               prompt_hash = :promptHash,
                               updated_by = :updatedBy,
                               approved_by = :approvedBy,
                               updated_at = :updatedAt,
                               approved_at = :approvedAt,
                               activated_at = :activatedAt,
                               replaced_version = :replacedVersion
                         WHERE id = :id
                        """)
                .params(params(template))
                .update();
        return template;
    }

    @Override
    public Optional<PromptTemplate> findById(UUID id) {
        return jdbcClient
                .sql(selectSql() + " WHERE id = :id")
                .param("id", id)
                .query(this::template)
                .optional();
    }

    @Override
    public Optional<PromptTemplate> findByKeyAndVersion(String promptKey, String version) {
        return jdbcClient
                .sql(selectSql() + " WHERE prompt_key = :promptKey AND version = :version")
                .param("promptKey", promptKey)
                .param("version", version)
                .query(this::template)
                .optional();
    }

    @Override
    public Optional<PromptTemplate> active(String promptKey) {
        return jdbcClient
                .sql(selectSql() + " WHERE prompt_key = :promptKey AND status = 'ACTIVE'")
                .param("promptKey", promptKey)
                .query(this::template)
                .optional();
    }

    @Override
    public List<PromptTemplate> list(String promptKey) {
        String where = promptKey == null || promptKey.isBlank() ? "" : " WHERE prompt_key = :promptKey";
        var spec =
                jdbcClient.sql(
                        selectSql()
                                + where
                                + " ORDER BY prompt_key ASC, updated_at DESC, version DESC");
        if (!where.isBlank()) {
            spec = spec.param("promptKey", promptKey.trim());
        }
        return spec.query(this::template).list();
    }

    @Override
    public void deactivateActive(String promptKey, String replacedByVersion) {
        jdbcClient
                .sql(
                        """
                        UPDATE expense_prompt_template
                           SET status = 'DEPRECATED',
                               replaced_version = :replacedByVersion,
                               updated_at = CURRENT_TIMESTAMP
                         WHERE prompt_key = :promptKey
                           AND status = 'ACTIVE'
                        """)
                .param("promptKey", promptKey)
                .param("replacedByVersion", replacedByVersion)
                .update();
    }

    @Override
    public PromptChangeRequest saveChangeRequest(PromptChangeRequest request) {
        jdbcClient
                .sql(
                        """
                        INSERT INTO expense_prompt_change_request (
                            id, prompt_template_id, request_type, status, diff_summary,
                            risk_level, evaluation_report, review_comment, submitted_by,
                            reviewed_by, submitted_at, reviewed_at
                        ) VALUES (
                            :id, :promptTemplateId, :requestType, :status, :diffSummary,
                            :riskLevel, CAST(:evaluationReport AS jsonb), :reviewComment,
                            :submittedBy, :reviewedBy, :submittedAt, :reviewedAt
                        )
                        """)
                .params(params(request))
                .update();
        return request;
    }

    @Override
    public PromptChangeRequest updateChangeRequest(PromptChangeRequest request) {
        jdbcClient
                .sql(
                        """
                        UPDATE expense_prompt_change_request
                           SET status = :status,
                               risk_level = :riskLevel,
                               evaluation_report = CAST(:evaluationReport AS jsonb),
                               review_comment = :reviewComment,
                               reviewed_by = :reviewedBy,
                               reviewed_at = :reviewedAt
                         WHERE id = :id
                        """)
                .params(params(request))
                .update();
        return request;
    }

    @Override
    public Optional<PromptChangeRequest> findChangeRequest(UUID id) {
        return jdbcClient
                .sql(changeSelectSql() + " WHERE id = :id")
                .param("id", id)
                .query(this::changeRequest)
                .optional();
    }

    @Override
    public List<PromptChangeRequest> changeRequests(UUID promptTemplateId) {
        return jdbcClient
                .sql(
                        changeSelectSql()
                                + " WHERE prompt_template_id = :promptTemplateId"
                                + " ORDER BY submitted_at DESC")
                .param("promptTemplateId", promptTemplateId)
                .query(this::changeRequest)
                .list();
    }

    @Override
    public List<PromptAuditEvent> auditLog(String promptKey, String version, int limit) {
        StringBuilder sql =
                new StringBuilder(
                        """
                        SELECT id, prompt_key, version, action, actor_subject,
                               payload::text AS payload, occurred_at
                        FROM expense_prompt_audit_log
                        WHERE prompt_key = :promptKey
                        """);
        if (version != null && !version.isBlank()) {
            sql.append(" AND version = :version");
        }
        sql.append(" ORDER BY occurred_at DESC, id DESC LIMIT :limit");
        var spec =
                jdbcClient
                        .sql(sql.toString())
                        .param("promptKey", promptKey)
                        .param("limit", Math.max(1, Math.min(limit, 100)));
        if (version != null && !version.isBlank()) {
            spec = spec.param("version", version.trim());
        }
        return spec
                .query(
                        (rs, row) ->
                                new PromptAuditEvent(
                                        rs.getObject("id", UUID.class),
                                        rs.getString("prompt_key"),
                                        rs.getString("version"),
                                        rs.getString("action"),
                                        rs.getString("actor_subject"),
                                        readMap(rs.getString("payload")),
                                        rs.getTimestamp("occurred_at").toInstant()))
                .list();
    }

    @Override
    public void appendAudit(
            String promptKey,
            String version,
            String action,
            String actorSubject,
            Map<String, Object> payload) {
        jdbcClient
                .sql(
                        """
                        INSERT INTO expense_prompt_audit_log (
                            id, prompt_key, version, action, actor_subject, payload, occurred_at
                        ) VALUES (
                            :id, :promptKey, :version, :action, :actorSubject,
                            CAST(:payload AS jsonb), CURRENT_TIMESTAMP
                        )
                        """)
                .param("id", UUID.randomUUID())
                .param("promptKey", promptKey)
                .param("version", version)
                .param("action", action)
                .param("actorSubject", actorSubject)
                .param("payload", write(payload))
                .update();
    }

    private Map<String, Object> params(PromptTemplate template) {
        Map<String, Object> params = new java.util.LinkedHashMap<>();
        params.put("id", template.id());
        params.put("promptKey", template.promptKey());
        params.put("version", template.version());
        params.put("name", template.name());
        params.put("description", template.description());
        params.put("content", template.content());
        params.put("variableSchema", write(template.variableSchema()));
        params.put("modelName", template.modelName());
        params.put("temperature", template.temperature());
        params.put("maxTokens", template.maxTokens());
        params.put("status", template.status().name());
        params.put("promptHash", template.promptHash());
        params.put("createdBy", template.createdBy());
        params.put("updatedBy", template.updatedBy());
        params.put("approvedBy", template.approvedBy());
        params.put("createdAt", Timestamp.from(template.createdAt()));
        params.put("updatedAt", Timestamp.from(template.updatedAt()));
        params.put("approvedAt", timestamp(template.approvedAt()));
        params.put("activatedAt", timestamp(template.activatedAt()));
        params.put("replacedVersion", template.replacedVersion());
        return params;
    }

    private Map<String, Object> params(PromptChangeRequest request) {
        Map<String, Object> params = new java.util.LinkedHashMap<>();
        params.put("id", request.id());
        params.put("promptTemplateId", request.promptTemplateId());
        params.put("requestType", request.requestType().name());
        params.put("status", request.status().name());
        params.put("diffSummary", request.diffSummary());
        params.put("riskLevel", request.riskLevel());
        params.put("evaluationReport", write(request.evaluationReport()));
        params.put("reviewComment", request.reviewComment());
        params.put("submittedBy", request.submittedBy());
        params.put("reviewedBy", request.reviewedBy());
        params.put("submittedAt", Timestamp.from(request.submittedAt()));
        params.put("reviewedAt", timestamp(request.reviewedAt()));
        return params;
    }

    private PromptTemplate template(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new PromptTemplate(
                rs.getObject("id", UUID.class),
                rs.getString("prompt_key"),
                rs.getString("version"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getString("content"),
                readMap(rs.getString("variable_schema")),
                rs.getString("model_name"),
                rs.getBigDecimal("temperature"),
                rs.getInt("max_tokens"),
                PromptStatus.valueOf(rs.getString("status")),
                rs.getString("prompt_hash"),
                rs.getString("created_by"),
                rs.getString("updated_by"),
                rs.getString("approved_by"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant(),
                instant(rs.getTimestamp("approved_at")),
                instant(rs.getTimestamp("activated_at")),
                rs.getString("replaced_version"));
    }

    private PromptChangeRequest changeRequest(java.sql.ResultSet rs, int row)
            throws java.sql.SQLException {
        return new PromptChangeRequest(
                rs.getObject("id", UUID.class),
                rs.getObject("prompt_template_id", UUID.class),
                PromptChangeRequestType.valueOf(rs.getString("request_type")),
                PromptChangeRequestStatus.valueOf(rs.getString("status")),
                rs.getString("diff_summary"),
                rs.getString("risk_level"),
                readMap(rs.getString("evaluation_report")),
                rs.getString("review_comment"),
                rs.getString("submitted_by"),
                rs.getString("reviewed_by"),
                rs.getTimestamp("submitted_at").toInstant(),
                instant(rs.getTimestamp("reviewed_at")));
    }

    private static String selectSql() {
        return """
                SELECT id, prompt_key, version, name, description, content,
                       variable_schema::text AS variable_schema, model_name, temperature, max_tokens,
                       status, prompt_hash, created_by, updated_by, approved_by,
                       created_at, updated_at, approved_at, activated_at, replaced_version
                  FROM expense_prompt_template
                """;
    }

    private static String changeSelectSql() {
        return """
                SELECT id, prompt_template_id, request_type, status, diff_summary,
                       risk_level, evaluation_report::text AS evaluation_report, review_comment,
                       submitted_by, reviewed_by, submitted_at, reviewed_at
                  FROM expense_prompt_change_request
                """;
    }

    private Map<String, Object> readMap(String json) {
        try {
            return objectMapper.readValue(json == null ? "{}" : json, new TypeReference<>() {});
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Prompt JSON 字段格式无效", exception);
        }
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Prompt JSON 字段序列化失败", exception);
        }
    }

    private static Timestamp timestamp(java.time.Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static java.time.Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}

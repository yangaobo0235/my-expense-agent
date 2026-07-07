package com.yangaobo.expense.backend.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangaobo.expense.backend.domain.model.ExpensePolicy;
import com.yangaobo.expense.backend.domain.model.PolicyChunk;
import com.yangaobo.expense.backend.domain.model.PolicyStatus;
import com.yangaobo.expense.backend.domain.repository.ExpensePolicyRepository;
import com.yangaobo.expense.backend.domain.repository.PolicyCatalogEntry;
import com.yangaobo.expense.backend.domain.repository.PolicySearchMatch;
import com.yangaobo.expense.common.error.ExpenseFlowErrorCode;
import com.yangaobo.expense.common.error.ExpenseFlowException;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcExpensePolicyRepository implements ExpensePolicyRepository {

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;

    public JdbcExpensePolicyRepository(JdbcClient jdbcClient, ObjectMapper objectMapper) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<PolicyCatalogEntry> listCatalog() {
        return jdbcClient
                .sql(
                        """
                        SELECT p.id, p.policy_code, p.name, p.category, p.region,
                               p.employee_grade, p.version, p.effective_from,
                               p.effective_to, p.status, p.source_uri, p.updated_at,
                               COUNT(c.id) AS chunk_count,
                               COUNT(c.id) FILTER (WHERE c.embedding IS NOT NULL)
                                   AS indexed_chunk_count
                        FROM expense_policy p
                        LEFT JOIN expense_policy_chunk c ON c.policy_id = p.id
                        GROUP BY p.id
                        ORDER BY p.category, p.policy_code, p.effective_from DESC,
                                 p.version DESC
                        """)
                .query(JdbcExpensePolicyRepository::mapCatalogEntry)
                .list();
    }

    @Override
    public ExpensePolicy insert(ExpensePolicy policy, List<PolicyChunk> chunks) {
        try {
            jdbcClient
                    .sql(
                            """
                            INSERT INTO expense_policy (
                                id, policy_code, name, category, region, employee_grade,
                                version, effective_from, effective_to, status, source_uri,
                                content_hash, created_at, updated_at
                            ) VALUES (
                                :id, :policyCode, :name, :category, :region, :employeeGrade,
                                :version, :effectiveFrom, :effectiveTo, :status, :sourceUri,
                                :contentHash, :createdAt, :updatedAt
                            )
                            """)
                    .param("id", policy.id())
                    .param("policyCode", policy.policyCode())
                    .param("name", policy.name())
                    .param("category", policy.category())
                    .param("region", policy.region())
                    .param("employeeGrade", policy.employeeGrade())
                    .param("version", policy.version())
                    .param("effectiveFrom", Date.valueOf(policy.effectiveFrom()))
                    .param(
                            "effectiveTo",
                            policy.effectiveTo() == null
                                    ? null
                                    : Date.valueOf(policy.effectiveTo()))
                    .param("status", policy.status().name())
                    .param("sourceUri", policy.sourceUri())
                    .param("contentHash", policy.contentHash())
                    .param("createdAt", Timestamp.from(policy.createdAt()))
                    .param("updatedAt", Timestamp.from(policy.updatedAt()))
                    .update();
            for (PolicyChunk chunk : chunks) {
                insertChunk(chunk);
            }
            return policy;
        } catch (DataIntegrityViolationException exception) {
            throw new ExpenseFlowException(
                    ExpenseFlowErrorCode.DUPLICATE_REQUEST,
                    "相同制度编码和版本已经存在");
        }
    }

    @Override
    public List<PolicySearchMatch> search(
            float[] queryEmbedding,
            String category,
            String region,
            String employeeGrade,
            LocalDate expenseDate,
            int limit,
            double minimumScore) {
        return jdbcClient
                .sql(
                        """
                        SELECT p.id AS policy_id, p.policy_code, p.name AS policy_name,
                               p.version AS policy_version, p.category, p.region,
                               p.employee_grade, p.effective_from, p.effective_to,
                               p.source_uri, c.id AS chunk_id, c.chunk_index, c.section,
                               c.content, 1 - (c.embedding <=> CAST(:embedding AS vector)) AS score
                        FROM expense_policy_chunk c
                        JOIN expense_policy p ON p.id = c.policy_id
                        WHERE p.status = 'ACTIVE'
                          AND p.category = :category
                          AND p.region IN (:region, 'ALL')
                          AND p.employee_grade IN (:employeeGrade, 'ALL')
                          AND p.effective_from <= :expenseDate
                          AND (p.effective_to IS NULL OR p.effective_to >= :expenseDate)
                          AND c.embedding IS NOT NULL
                          AND 1 - (c.embedding <=> CAST(:embedding AS vector)) >= :minimumScore
                        ORDER BY c.embedding <=> CAST(:embedding AS vector)
                        LIMIT :limit
                        """)
                .param("embedding", vectorLiteral(queryEmbedding))
                .param("category", category)
                .param("region", region)
                .param("employeeGrade", employeeGrade)
                .param("expenseDate", Date.valueOf(expenseDate))
                .param("minimumScore", minimumScore)
                .param("limit", limit)
                .query(JdbcExpensePolicyRepository::mapSearchMatch)
                .list();
    }

    private void insertChunk(PolicyChunk chunk) {
        jdbcClient
                .sql(
                        """
                        INSERT INTO expense_policy_chunk (
                            id, policy_id, chunk_index, section, content, token_count,
                            metadata, embedding, created_at
                        ) VALUES (
                            :id, :policyId, :chunkIndex, :section, :content, :tokenCount,
                            CAST(:metadata AS jsonb), CAST(:embedding AS vector), :createdAt
                        )
                        """)
                .param("id", chunk.id())
                .param("policyId", chunk.policyId())
                .param("chunkIndex", chunk.chunkIndex())
                .param("section", chunk.section())
                .param("content", chunk.content())
                .param("tokenCount", chunk.tokenCount())
                .param("metadata", json(chunk.metadata()))
                .param("embedding", vectorLiteral(chunk.embedding()))
                .param("createdAt", Timestamp.from(chunk.createdAt()))
                .update();
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("制度元数据序列化失败", exception);
        }
    }

    private static String vectorLiteral(float[] vector) {
        if (vector.length != 1024) {
            throw new ExpenseFlowException(
                    ExpenseFlowErrorCode.VALIDATION_FAILED, "制度向量维度必须为 1024");
        }
        StringBuilder builder = new StringBuilder(vector.length * 10).append('[');
        for (int index = 0; index < vector.length; index++) {
            if (index > 0) {
                builder.append(',');
            }
            builder.append(vector[index]);
        }
        return builder.append(']').toString();
    }

    private static PolicySearchMatch mapSearchMatch(ResultSet resultSet, int rowNumber)
            throws SQLException {
        Date effectiveTo = resultSet.getDate("effective_to");
        return new PolicySearchMatch(
                resultSet.getObject("policy_id", java.util.UUID.class),
                resultSet.getString("policy_code"),
                resultSet.getString("policy_name"),
                resultSet.getString("policy_version"),
                resultSet.getString("category"),
                resultSet.getString("region"),
                resultSet.getString("employee_grade"),
                resultSet.getDate("effective_from").toLocalDate(),
                effectiveTo == null ? null : effectiveTo.toLocalDate(),
                resultSet.getString("source_uri"),
                resultSet.getObject("chunk_id", java.util.UUID.class),
                resultSet.getInt("chunk_index"),
                resultSet.getString("section"),
                resultSet.getString("content"),
                resultSet.getDouble("score"));
    }

    private static PolicyCatalogEntry mapCatalogEntry(ResultSet resultSet, int rowNumber)
            throws SQLException {
        Date effectiveTo = resultSet.getDate("effective_to");
        return new PolicyCatalogEntry(
                resultSet.getObject("id", java.util.UUID.class),
                resultSet.getString("policy_code"),
                resultSet.getString("name"),
                resultSet.getString("category"),
                resultSet.getString("region"),
                resultSet.getString("employee_grade"),
                resultSet.getString("version"),
                resultSet.getDate("effective_from").toLocalDate(),
                effectiveTo == null ? null : effectiveTo.toLocalDate(),
                PolicyStatus.valueOf(resultSet.getString("status")),
                resultSet.getString("source_uri"),
                resultSet.getInt("chunk_count"),
                resultSet.getInt("indexed_chunk_count"),
                resultSet.getTimestamp("updated_at").toInstant());
    }
}

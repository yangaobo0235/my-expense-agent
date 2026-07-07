package com.yangaobo.expense.audithistory.application;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditHistoryService {

    private final JdbcClient jdbcClient;

    public AuditHistoryService(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public DuplicateResult checkDuplicateDocument(String sha256) {
        return checkDuplicateDocument(sha256, null);
    }

    public DuplicateResult checkDuplicateDocument(
            String sha256, UUID excludeCaseId) {
        String hash = required(sha256, "sha256");
        String sql =
                """
                SELECT case_id, employee_id, seller_name, amount, currency, expense_date, document_sha256
                FROM expense_audit_history_item
                WHERE document_sha256 = :sha256
                """
                        + (excludeCaseId == null ? "" : " AND case_id <> :excludeCaseId")
                        + " ORDER BY expense_date DESC";
        JdbcClient.StatementSpec statement = jdbcClient.sql(sql).param("sha256", hash);
        if (excludeCaseId != null) {
            statement = statement.param("excludeCaseId", excludeCaseId);
        }
        List<ExpenseHistory> matches =
                statement
                        .query((rs, rowNum) -> history(rs))
                        .list();
        return new DuplicateResult(hash, !matches.isEmpty(), matches);
    }

    public List<ExpenseHistory> getExpenseHistory(String employeeId) {
        return jdbcClient
                .sql(
                        """
                        SELECT case_id, employee_id, seller_name, amount, currency, expense_date, document_sha256
                        FROM expense_audit_history_item
                        WHERE employee_id = :employeeId
                        ORDER BY expense_date DESC
                        """)
                .param("employeeId", required(employeeId, "employeeId"))
                .query((rs, rowNum) -> history(rs))
                .list();
    }

    @Transactional
    public EvidenceRecord saveReviewEvidence(
            String requestId, UUID caseId, String evidenceType, String contentHash) {
        String normalizedRequestId = required(requestId, "requestId");
        if (caseId == null) {
            throw new IllegalArgumentException("caseId不能为空");
        }
        return findEvidence(normalizedRequestId)
                .orElseGet(
                        () -> {
                            EvidenceRecord created =
                                    new EvidenceRecord(
                                            UUID.randomUUID(),
                                            normalizedRequestId,
                                            caseId,
                                            required(evidenceType, "evidenceType"),
                                            required(contentHash, "contentHash"),
                                            Instant.now());
                            jdbcClient
                                    .sql(
                                            """
                                            INSERT INTO expense_audit_review_evidence (
                                                evidence_id, request_id, case_id, evidence_type, content_hash, created_at
                                            ) VALUES (
                                                :evidenceId, :requestId, :caseId, :evidenceType, :contentHash, :createdAt
                                            )
                                            """)
                                    .param("evidenceId", created.evidenceId())
                                    .param("requestId", created.requestId())
                                    .param("caseId", created.caseId())
                                    .param("evidenceType", created.evidenceType())
                                    .param("contentHash", created.contentHash())
                                    .param("createdAt", Timestamp.from(created.createdAt()))
                                    .update();
                            return created;
                        });
    }

    private java.util.Optional<EvidenceRecord> findEvidence(String requestId) {
        return jdbcClient
                .sql(
                        """
                        SELECT evidence_id, request_id, case_id, evidence_type, content_hash, created_at
                        FROM expense_audit_review_evidence
                        WHERE request_id = :requestId
                        """)
                .param("requestId", requestId)
                .query(
                        (rs, rowNum) ->
                                new EvidenceRecord(
                                        rs.getObject("evidence_id", UUID.class),
                                        rs.getString("request_id"),
                                        rs.getObject("case_id", UUID.class),
                                        rs.getString("evidence_type"),
                                        rs.getString("content_hash"),
                                        rs.getTimestamp("created_at").toInstant()))
                .optional();
    }

    private static ExpenseHistory history(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new ExpenseHistory(
                rs.getObject("case_id", UUID.class),
                rs.getString("employee_id"),
                rs.getString("seller_name"),
                rs.getBigDecimal("amount"),
                rs.getString("currency"),
                rs.getObject("expense_date", LocalDate.class),
                rs.getString("document_sha256"));
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + "不能为空");
        }
        return value.trim();
    }

    public record ExpenseHistory(
            UUID caseId,
            String employeeId,
            String sellerName,
            BigDecimal amount,
            String currency,
            LocalDate expenseDate,
            String documentSha256) {}
    public record DuplicateResult(
            String sha256, boolean duplicate, List<ExpenseHistory> matches) {}
    public record EvidenceRecord(
            UUID evidenceId,
            String requestId,
            UUID caseId,
            String evidenceType,
            String contentHash,
            Instant createdAt) {}
}

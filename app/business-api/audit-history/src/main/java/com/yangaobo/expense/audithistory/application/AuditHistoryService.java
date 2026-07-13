package com.yangaobo.expense.audithistory.application;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
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
                SELECT case_id, applicant_id, seller_name, amount, currency, expense_date, document_sha256
                FROM campus_fund_reimbursement_history_item
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

    public List<ExpenseHistory> getFundReimbursementHistory(String applicantId) {
        return jdbcClient
                .sql(
                        """
                        SELECT case_id, applicant_id, seller_name, amount, currency, expense_date, document_sha256
                        FROM campus_fund_reimbursement_history_item
                        WHERE applicant_id = :applicantId
                        ORDER BY expense_date DESC
                        """)
                .param("applicantId", required(applicantId, "applicantId"))
                .query((rs, rowNum) -> history(rs))
                .list();
    }

    @Transactional
    public HistoryRecord recordFundReimbursementHistory(
            String requestId,
            UUID caseId,
            String applicantId,
            String sellerName,
            BigDecimal amount,
            String currency,
            LocalDate expenseDate,
            String documentSha256) {
        String normalizedRequestId = required(requestId, "requestId");
        if (caseId == null || amount == null || amount.signum() < 0 || expenseDate == null) {
            throw new IllegalArgumentException("caseId、金额和支出日期不能为空，金额不能为负数");
        }
        String normalizedApplicantId = required(applicantId, "applicantId");
        String normalizedSellerName = required(sellerName, "sellerName");
        String normalizedCurrency = required(currency, "currency").toUpperCase();
        String normalizedSha256 = required(documentSha256, "documentSha256");

        Optional<HistoryRecord> byRequest = findHistoryByRequestId(normalizedRequestId);
        if (byRequest.isPresent()) {
            assertSameHistory(
                    byRequest.get(),
                    caseId,
                    normalizedApplicantId,
                    normalizedSellerName,
                    amount,
                    normalizedCurrency,
                    expenseDate,
                    normalizedSha256);
            return byRequest.get();
        }
        Optional<HistoryRecord> byDocument = findHistoryByDocument(caseId, normalizedSha256);
        if (byDocument.isPresent()) {
            return byDocument.get();
        }

        HistoryRecord created =
                new HistoryRecord(
                        UUID.randomUUID(),
                        normalizedRequestId,
                        caseId,
                        normalizedApplicantId,
                        normalizedSellerName,
                        amount,
                        normalizedCurrency,
                        expenseDate,
                        normalizedSha256,
                        Instant.now());
        jdbcClient
                .sql(
                        """
                        INSERT INTO campus_fund_reimbursement_history_item (
                            history_id, request_id, case_id, applicant_id, seller_name,
                            amount, currency, expense_date, document_sha256, created_at
                        ) VALUES (
                            :historyId, :requestId, :caseId, :applicantId, :sellerName,
                            :amount, :currency, :expenseDate, :documentSha256, :createdAt
                        )
                        """)
                .param("historyId", created.historyId())
                .param("requestId", created.requestId())
                .param("caseId", created.caseId())
                .param("applicantId", created.applicantId())
                .param("sellerName", created.sellerName())
                .param("amount", created.amount())
                .param("currency", created.currency())
                .param("expenseDate", created.expenseDate())
                .param("documentSha256", created.documentSha256())
                .param("createdAt", Timestamp.from(created.createdAt()))
                .update();
        return created;
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
                                            INSERT INTO campus_fund_review_evidence (
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
                        FROM campus_fund_review_evidence
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

    private Optional<HistoryRecord> findHistoryByRequestId(String requestId) {
        return historyRecordQuery("request_id = :value", requestId);
    }

    private Optional<HistoryRecord> findHistoryByDocument(UUID caseId, String documentSha256) {
        return jdbcClient
                .sql(
                        """
                        SELECT history_id, request_id, case_id, applicant_id, seller_name,
                               amount, currency, expense_date, document_sha256, created_at
                        FROM campus_fund_reimbursement_history_item
                        WHERE case_id = :caseId AND document_sha256 = :documentSha256
                        """)
                .param("caseId", caseId)
                .param("documentSha256", documentSha256)
                .query((rs, rowNum) -> historyRecord(rs))
                .optional();
    }

    private Optional<HistoryRecord> historyRecordQuery(String whereClause, String value) {
        return jdbcClient
                .sql(
                        """
                        SELECT history_id, request_id, case_id, applicant_id, seller_name,
                               amount, currency, expense_date, document_sha256, created_at
                        FROM campus_fund_reimbursement_history_item
                        WHERE %s
                        """
                                .formatted(whereClause))
                .param("value", value)
                .query((rs, rowNum) -> historyRecord(rs))
                .optional();
    }

    private static ExpenseHistory history(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new ExpenseHistory(
                rs.getObject("case_id", UUID.class),
                rs.getString("applicant_id"),
                rs.getString("seller_name"),
                rs.getBigDecimal("amount"),
                rs.getString("currency"),
                rs.getObject("expense_date", LocalDate.class),
                rs.getString("document_sha256"));
    }

    private static HistoryRecord historyRecord(java.sql.ResultSet rs)
            throws java.sql.SQLException {
        return new HistoryRecord(
                rs.getObject("history_id", UUID.class),
                rs.getString("request_id"),
                rs.getObject("case_id", UUID.class),
                rs.getString("applicant_id"),
                rs.getString("seller_name"),
                rs.getBigDecimal("amount"),
                rs.getString("currency"),
                rs.getObject("expense_date", LocalDate.class),
                rs.getString("document_sha256"),
                rs.getTimestamp("created_at").toInstant());
    }

    private static void assertSameHistory(
            HistoryRecord existing,
            UUID caseId,
            String applicantId,
            String sellerName,
            BigDecimal amount,
            String currency,
            LocalDate expenseDate,
            String documentSha256) {
        if (!existing.caseId().equals(caseId)
                || !existing.applicantId().equals(applicantId)
                || !existing.sellerName().equals(sellerName)
                || existing.amount().compareTo(amount) != 0
                || !existing.currency().equalsIgnoreCase(currency)
                || !existing.expenseDate().equals(expenseDate)
                || !existing.documentSha256().equals(documentSha256)) {
            throw new IllegalArgumentException("requestId 已被其他报销历史写入请求使用");
        }
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + "不能为空");
        }
        return value.trim();
    }

    public record ExpenseHistory(
            UUID caseId,
            String applicantId,
            String sellerName,
            BigDecimal amount,
            String currency,
            LocalDate expenseDate,
            String documentSha256) {}
    public record DuplicateResult(
            String sha256, boolean duplicate, List<ExpenseHistory> matches) {}
    public record HistoryRecord(
            UUID historyId,
            String requestId,
            UUID caseId,
            String applicantId,
            String sellerName,
            BigDecimal amount,
            String currency,
            LocalDate expenseDate,
            String documentSha256,
            Instant createdAt) {}
    public record EvidenceRecord(
            UUID evidenceId,
            String requestId,
            UUID caseId,
            String evidenceType,
            String contentHash,
            Instant createdAt) {}
}

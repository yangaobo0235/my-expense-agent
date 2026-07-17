package com.yangaobo.expense.service.application;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExpenseBusinessService {

    private final JdbcClient jdbcClient;

    public ExpenseBusinessService(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public InvoiceValidation validateInvoiceNumber(String invoiceNumber) {
        String normalized = required(invoiceNumber, "invoiceNumber").replaceAll("\\s+", "");
        boolean valid = normalized.matches("[A-Za-z0-9-]{8,32}");
        return new InvoiceValidation(normalized, valid, valid ? "VALID" : "INVALID_FORMAT");
    }

    public AllowedAmount calculateAllowedAmount(
            BigDecimal claimedAmount, BigDecimal policyLimit) {
        if (claimedAmount == null || policyLimit == null
                || claimedAmount.signum() < 0 || policyLimit.signum() < 0) {
            throw new IllegalArgumentException("金额不能为空或为负数");
        }
        BigDecimal allowed = claimedAmount.min(policyLimit);
        return new AllowedAmount(
                claimedAmount,
                policyLimit,
                allowed,
                claimedAmount.subtract(allowed).max(BigDecimal.ZERO));
    }

    @Transactional
    public SubmissionResult submitReimbursement(
            String requestId, UUID caseId, BigDecimal amount, String currency) {
        String normalizedRequestId = required(requestId, "requestId");
        if (caseId == null || amount == null || amount.signum() < 0) {
            throw new IllegalArgumentException("经费申请和金额参数不合法");
        }
        return findSubmission(normalizedRequestId)
                .orElseGet(
                        () -> {
                            SubmissionResult created =
                                    new SubmissionResult(
                                            UUID.randomUUID(),
                                            normalizedRequestId,
                                            caseId,
                                            amount,
                                            required(currency, "currency").toUpperCase(),
                                            "SUBMITTED",
                                            Instant.now());
                            jdbcClient
                                    .sql(
                                            """
                                            INSERT INTO my_expense_agent_reimbursement_record (
                                                reimbursement_id, request_id, case_id, amount, currency, status, submitted_at
                                            ) VALUES (
                                                :reimbursementId, :requestId, :caseId, :amount, :currency, :status, :submittedAt
                                            )
                                            """)
                                    .param("reimbursementId", created.reimbursementId())
                                    .param("requestId", created.requestId())
                                    .param("caseId", created.caseId())
                                    .param("amount", created.amount())
                                    .param("currency", created.currency())
                                    .param("status", created.status())
                                    .param("submittedAt", Timestamp.from(created.submittedAt()))
                                    .update();
                            return created;
                        });
    }

    @Transactional
    public PostingResult submitFundPosting(
            String requestId, UUID reimbursementId, BigDecimal amount, String currency) {
        String normalizedRequestId = required(requestId, "requestId");
        if (reimbursementId == null || amount == null || amount.signum() < 0) {
            throw new IllegalArgumentException("报销登记和金额参数不合法");
        }
        ensureReimbursementExists(reimbursementId);
        return findPosting(normalizedRequestId)
                .orElseGet(
                        () -> {
                            PostingResult created =
                                    new PostingResult(
                                            UUID.randomUUID(),
                                            normalizedRequestId,
                                            reimbursementId,
                                            amount,
                                            required(currency, "currency").toUpperCase(),
                                            "SUBMITTED",
                                            Instant.now());
                            jdbcClient
                                    .sql(
                                            """
                                            INSERT INTO my_expense_agent_posting_record (
                                                posting_id, request_id, reimbursement_id, amount, currency, status, posted_at
                                            ) VALUES (
                                                :postingId, :requestId, :reimbursementId, :amount, :currency, :status, :postedAt
                                            )
                                            """)
                                    .param("postingId", created.postingId())
                                    .param("requestId", created.requestId())
                                    .param("reimbursementId", created.reimbursementId())
                                    .param("amount", created.amount())
                                    .param("currency", created.currency())
                                    .param("status", created.status())
                                    .param("postedAt", Timestamp.from(created.postedAt()))
                                    .update();
                            return created;
                        });
    }

    private java.util.Optional<SubmissionResult> findSubmission(String requestId) {
        return jdbcClient
                .sql(
                        """
                        SELECT reimbursement_id, request_id, case_id, amount, currency, status, submitted_at
                        FROM my_expense_agent_reimbursement_record
                        WHERE request_id = :requestId
                        """)
                .param("requestId", requestId)
                .query(
                        (rs, rowNum) ->
                                new SubmissionResult(
                                        rs.getObject("reimbursement_id", UUID.class),
                                        rs.getString("request_id"),
                                        rs.getObject("case_id", UUID.class),
                                        rs.getBigDecimal("amount"),
                                        rs.getString("currency"),
                                        rs.getString("status"),
                                        rs.getTimestamp("submitted_at").toInstant()))
                .optional();
    }

    private java.util.Optional<PostingResult> findPosting(String requestId) {
        return jdbcClient
                .sql(
                        """
                        SELECT posting_id, request_id, reimbursement_id, amount, currency, status, posted_at
                        FROM my_expense_agent_posting_record
                        WHERE request_id = :requestId
                        """)
                .param("requestId", requestId)
                .query(
                        (rs, rowNum) ->
                                new PostingResult(
                                        rs.getObject("posting_id", UUID.class),
                                        rs.getString("request_id"),
                                        rs.getObject("reimbursement_id", UUID.class),
                                        rs.getBigDecimal("amount"),
                                        rs.getString("currency"),
                                        rs.getString("status"),
                                        rs.getTimestamp("posted_at").toInstant()))
                .optional();
    }

    private void ensureReimbursementExists(UUID reimbursementId) {
        Boolean exists =
                jdbcClient
                        .sql(
                                """
                                SELECT EXISTS (
                                    SELECT 1
                                    FROM my_expense_agent_reimbursement_record
                                    WHERE reimbursement_id = :reimbursementId
                                )
                                """)
                        .param("reimbursementId", reimbursementId)
                        .query(Boolean.class)
                        .single();
        if (!Boolean.TRUE.equals(exists)) {
            throw new IllegalArgumentException("报销登记不存在，不能提交入账");
        }
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + "不能为空");
        }
        return value.trim();
    }

    public record InvoiceValidation(String invoiceNumber, boolean valid, String reason) {}
    public record AllowedAmount(
            BigDecimal claimedAmount,
            BigDecimal policyLimit,
            BigDecimal allowedAmount,
            BigDecimal excessAmount) {}
    public record SubmissionResult(
            UUID reimbursementId,
            String requestId,
            UUID caseId,
            BigDecimal amount,
            String currency,
            String status,
            Instant submittedAt) {}
    public record PostingResult(
            UUID postingId,
            String requestId,
            UUID reimbursementId,
            BigDecimal amount,
            String currency,
            String status,
            Instant postedAt) {

    }
}

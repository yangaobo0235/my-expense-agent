package com.yangaobo.expense.audithistory.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

class AuditHistoryServiceTest {

    private static final UUID EXISTING_CASE =
            UUID.fromString("10000000-0000-0000-0000-000000000001");

    private AuditHistoryService service;

    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL(
                "jdbc:h2:mem:audit_history_"
                        + UUID.randomUUID()
                        + ";MODE=PostgreSQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1");
        JdbcClient jdbcClient = JdbcClient.create(dataSource);
        jdbcClient
                .sql(
                        """
                        CREATE TABLE expense_audit_history_item (
                            case_id UUID PRIMARY KEY,
                            employee_id VARCHAR(80) NOT NULL,
                            seller_name VARCHAR(160) NOT NULL,
                            amount NUMERIC(18,2) NOT NULL,
                            currency VARCHAR(8) NOT NULL,
                            expense_date DATE NOT NULL,
                            document_sha256 VARCHAR(128) NOT NULL
                        )
                        """)
                .update();
        jdbcClient
                .sql(
                        """
                        CREATE TABLE expense_audit_review_evidence (
                            evidence_id UUID PRIMARY KEY,
                            request_id VARCHAR(128) NOT NULL UNIQUE,
                            case_id UUID NOT NULL,
                            evidence_type VARCHAR(80) NOT NULL,
                            content_hash VARCHAR(128) NOT NULL,
                            created_at TIMESTAMP NOT NULL
                        )
                        """)
                .update();
        jdbcClient
                .sql(
                        """
                        INSERT INTO expense_audit_history_item (
                            case_id, employee_id, seller_name, amount, currency, expense_date, document_sha256
                        ) VALUES (
                            :caseId, :employeeId, :sellerName, :amount, :currency, :expenseDate, :documentSha256
                        )
                        """)
                .param("caseId", EXISTING_CASE)
                .param("employeeId", "employee01")
                .param("sellerName", "酒店")
                .param("amount", new BigDecimal("880.00"))
                .param("currency", "CNY")
                .param("expenseDate", LocalDate.of(2026, 5, 20))
                .param("documentSha256", "abc123")
                .update();
        service = new AuditHistoryService(jdbcClient);
    }

    @Test
    void shouldFindHistoricalDuplicate() {
        assertThat(service.checkDuplicateDocument("abc123").duplicate())
                .isTrue();
    }

    @Test
    void shouldExcludeCurrentCaseFromDuplicateMatches() {
        var result =
                service.checkDuplicateDocument(
                        "abc123", EXISTING_CASE);

        assertThat(result.duplicate()).isFalse();
        assertThat(result.matches()).isEmpty();
    }

    @Test
    void shouldSaveReviewEvidenceIdempotently() {
        UUID caseId = UUID.randomUUID();

        var first =
                service.saveReviewEvidence(
                        "request-1", caseId, "RISK_SUMMARY", "content-hash-1");
        var second =
                service.saveReviewEvidence(
                        "request-1", caseId, "RISK_SUMMARY", "content-hash-1");

        assertThat(second.evidenceId()).isEqualTo(first.evidenceId());
        assertThat(second.requestId()).isEqualTo("request-1");
    }
}

package com.yangaobo.expense.backend.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.yangaobo.expense.backend.application.settlement.ExpenseSettlementService;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(properties = "spring.datasource.hikari.maximum-pool-size=2")
@EnabledIfEnvironmentVariable(
        named = "EXPENSE_SETTLEMENT_EXTERNAL_TEST",
        matches = "true")
class SettlementExternalIntegrationTest {

    @Autowired private ExpenseSettlementService settlementService;
    @Autowired private JdbcClient jdbcClient;

    private UUID caseId;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.datasource.url",
                () -> required("EXPENSE_IT_DATABASE_URL"));
        registry.add(
                "spring.datasource.username",
                () -> required("EXPENSE_IT_DATABASE_USERNAME"));
        registry.add(
                "spring.datasource.password",
                () -> required("EXPENSE_IT_DATABASE_PASSWORD"));
        registry.add("expense.mcp.client.enabled", () -> "true");
        registry.add(
                "expense.mcp.client.service-token",
                () -> required("EXPENSE_MCP_SERVICE_TOKEN"));
        registry.add(
                "expense.mcp.client.account-url",
                () -> required("EXPENSE_ACCOUNT_MCP_URL"));
        registry.add(
                "expense.mcp.client.expense-url",
                () -> required("EXPENSE_EXPENSE_MCP_URL"));
        registry.add(
                "expense.mcp.client.audit-history-url",
                () -> required("EXPENSE_AUDIT_HISTORY_MCP_URL"));
    }

    @AfterEach
    void cleanup() {
        if (caseId != null) {
            jdbcClient
                    .sql("DELETE FROM expense_case WHERE id = :id")
                    .param("id", caseId)
                    .update();
        }
    }

    @Test
    void shouldSettleApprovedAmountAndReplayWithoutDuplicateWrites() {
        caseId = UUID.randomUUID();
        Instant now = Instant.now();
        String requestId = "settlement-it-" + UUID.randomUUID();
        insertApprovedCase(now);

        var first =
                settlementService.settle(
                        caseId, requestId, "finance-admin");
        var replay =
                settlementService.settle(
                        caseId, requestId, "finance-admin");

        assertThat(first.amount()).isEqualByComparingTo("500.00");
        assertThat(first.status()).isEqualTo("SUBMITTED");
        assertThat(replay.reimbursementId())
                .isEqualTo(first.reimbursementId());
        assertThat(replay.postingId()).isEqualTo(first.postingId());
        assertThat(
                        jdbcClient
                                .sql(
                                        """
                                        SELECT count(*)::int
                                        FROM expense_tool_call
                                        WHERE case_id = :caseId
                                          AND status = 'SUCCEEDED'
                                        """)
                                .param("caseId", caseId)
                                .query(Integer.class)
                                .single())
                .isEqualTo(4);
        assertThat(
                        jdbcClient
                                .sql(
                                        """
                                        SELECT count(*)::int
                                        FROM my_expense_agent_audit_log
                                        WHERE case_id = :caseId
                                          AND action = 'FUND_POSTED'
                                        """)
                                .param("caseId", caseId)
                                .query(Integer.class)
                                .single())
                .isEqualTo(1);
    }

    private void insertApprovedCase(Instant now) {
        jdbcClient
                .sql(
                        """
                        INSERT INTO expense_case (
                            id, case_number, owner_subject, applicant_name,
                            project_code, title, currency, claimed_amount,
                            status, version, created_at, updated_at
                        ) VALUES (
                            :id, :caseNumber, 'student01', '李明',
                            'CS-SRTP', '入账集成测试', 'CNY', 600.00,
                            'APPROVED', 0, :now, :now
                        )
                        """)
                .param("id", caseId)
                .param("caseNumber", "CF-SET-" + caseId.toString().substring(0, 8))
                .param("now", Timestamp.from(now))
                .update();
        jdbcClient
                .sql(
                        """
                        INSERT INTO expense_decision (
                            id, case_id, decision, total_amount,
                            approved_amount, currency, risk_level, risk_score,
                            policy_findings, risk_findings, evidence,
                            decided_by, request_id, created_at
                        ) VALUES (
                            :id, :caseId, 'APPROVED', 600.00,
                            500.00, 'CNY', 'MEDIUM', 30,
                            '[]'::jsonb, '[]'::jsonb, '{}'::jsonb,
                            'collegeReviewer01', :requestId, :now
                        )
                        """)
                .param("id", UUID.randomUUID())
                .param("caseId", caseId)
                .param("requestId", "review-" + caseId)
                .param("now", Timestamp.from(now))
                .update();
        UUID documentId = UUID.randomUUID();
        jdbcClient
                .sql(
                        """
                        INSERT INTO expense_document (
                            id, case_id, original_filename, content_type, file_size,
                            sha256, object_key, document_type, extraction_confidence,
                            extraction_result, validation_errors, model_name,
                            prompt_version, raw_response_hash, created_at, updated_at
                        ) VALUES (
                            :id, :caseId, 'settlement-test.pdf', 'application/pdf', 100,
                            :sha256, :objectKey, 'HOTEL_INVOICE', 0.95,
                            CAST(:result AS jsonb), '[]'::jsonb, 'integration-test',
                            'receipt-extraction-v2', :responseHash, :now, :now
                        )
                        """)
                .param("id", documentId)
                .param("caseId", caseId)
                .param("sha256", "a".repeat(64))
                .param("objectKey", caseId + "/" + documentId + ".pdf")
                .param(
                        "result",
                        """
                        {"documentType":"HOTEL_INVOICE","invoiceCode":"INV",
                         "invoiceNumber":"SETTLEMENT-001","sellerName":"测试酒店",
                         "buyerName":"测试高校","issueDate":"2026-07-30",
                         "totalAmount":500.00,"currency":"CNY","items":[],
                         "confidence":0.95,"warnings":[]}
                        """)
                .param("responseHash", "b".repeat(64))
                .param("now", Timestamp.from(now))
                .update();
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + "不能为空");
        }
        return value;
    }
}

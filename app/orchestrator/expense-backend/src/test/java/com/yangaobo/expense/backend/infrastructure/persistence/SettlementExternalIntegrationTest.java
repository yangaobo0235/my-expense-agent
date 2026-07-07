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

@SpringBootTest
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
                "expense.mcp.client.token-uri",
                () -> required("EXPENSE_MCP_TOKEN_URI"));
        registry.add(
                "expense.mcp.client.client-id",
                () -> required("EXPENSE_MCP_CLIENT_ID"));
        registry.add(
                "expense.mcp.client.client-secret",
                () -> required("EXPENSE_MCP_CLIENT_SECRET"));
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
        assertThat(first.status()).isEqualTo("SIMULATED_PAID");
        assertThat(replay.reimbursementId())
                .isEqualTo(first.reimbursementId());
        assertThat(replay.paymentId()).isEqualTo(first.paymentId());
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
                .isEqualTo(2);
        assertThat(
                        jdbcClient
                                .sql(
                                        """
                                        SELECT count(*)::int
                                        FROM expense_audit_log
                                        WHERE case_id = :caseId
                                          AND action = 'EXPENSE_SETTLED'
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
                            department_code, title, currency, claimed_amount,
                            status, version, created_at, updated_at
                        ) VALUES (
                            :id, :caseNumber, 'employee01', '费用员工',
                            'IT', '结算集成测试', 'CNY', 600.00,
                            'APPROVED', 0, :now, :now
                        )
                        """)
                .param("id", caseId)
                .param("caseNumber", "EF-SET-" + caseId.toString().substring(0, 8))
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
                            'reviewer01', :requestId, :now
                        )
                        """)
                .param("id", UUID.randomUUID())
                .param("caseId", caseId)
                .param("requestId", "review-" + caseId)
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

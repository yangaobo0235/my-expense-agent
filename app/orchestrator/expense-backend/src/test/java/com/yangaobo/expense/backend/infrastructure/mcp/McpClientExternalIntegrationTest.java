package com.yangaobo.expense.backend.infrastructure.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import com.yangaobo.expense.agents.mcp.ApprovedMcpWriteRequest;
import com.yangaobo.expense.agents.mcp.ExpenseMcpClientFactory;
import com.yangaobo.expense.agents.mcp.ExpenseMcpGateway;
import dev.langchain4j.mcp.client.McpClient;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(
        named = "EXPENSE_MCP_EXTERNAL_TEST",
        matches = "true")
class McpClientExternalIntegrationTest {

    @Test
    void shouldConnectListAndCallAuthenticatedMcpTools() {
        ExpenseMcpClientProperties properties =
                new ExpenseMcpClientProperties(
                        true,
                        required("EXPENSE_MCP_SERVICE_TOKEN"),
                        value(
                                "EXPENSE_ACCOUNT_MCP_URL",
                                "http://localhost:25102/mcp"),
                        value(
                                "EXPENSE_EXPENSE_MCP_URL",
                                "http://localhost:25103/mcp"),
                        value(
                                "EXPENSE_AUDIT_HISTORY_MCP_URL",
                                "http://localhost:25104/mcp"),
                        Duration.ofSeconds(10),
                        Duration.ofSeconds(15),
                        2,
                        Duration.ZERO);
        ServiceTokenProvider tokens = new ServiceTokenProvider(properties);
        List<McpClient> clients =
                List.of(
                        client(
                                "account",
                                properties.accountUrl(),
                                tokens,
                                properties),
                        client(
                                "expense",
                                properties.expenseUrl(),
                                tokens,
                                properties),
                        client(
                                "audit-history",
                                properties.auditHistoryUrl(),
                                tokens,
                                properties));
        ExpenseMcpGateway gateway = new ExpenseMcpGateway(clients);
        try {
            assertThat(
                            clients.stream()
                                    .flatMap(
                                            client ->
                                                    client.listTools()
                                                            .stream())
                                    .map(
                                            dev.langchain4j.agent.tool
                                                            .ToolSpecification
                                                    ::name))
                    .containsExactlyInAnyOrder(
                            "get_applicant_profile",
                            "get_reimbursement_accounts",
                            "get_project_budget_balance",
                            "debit_project_budget",
                            "validate_invoice_number",
                            "calculate_allowed_amount",
                            "submit_fund_reimbursement",
                            "submit_fund_posting",
                            "check_duplicate_document",
                            "get_fund_reimbursement_history",
                            "save_review_evidence",
                            "record_fund_reimbursement_history");
            assertThat(
                            gateway.executeReadOnly(
                                            "get_applicant_profile",
                                            "{\"applicantId\":\"student01\"}")
                                    .isError())
                    .isFalse();
            assertThat(
                            gateway.executeReadOnly(
                                            "calculate_allowed_amount",
                                            "{\"claimedAmount\":680,\"policyLimit\":500}")
                                    .isError())
                    .isFalse();
            assertThat(
                            gateway.executeReadOnly(
                                            "check_duplicate_document",
                                            "{\"sha256\":\"abc123\"}")
                                    .isError())
                    .isFalse();
            String writeRequestId =
                    "mcp-it-review-evidence-" + UUID.randomUUID();
            assertThat(
                            gateway.executeApprovedWrite(
                                            new ApprovedMcpWriteRequest(
                                                    "save_review_evidence",
                                                    """
                                                    {"requestId":"%s","caseId":"%s","evidenceType":"DUPLICATE_CHECK","contentHash":"%s"}
                                                    """
                                                            .formatted(
                                                                    writeRequestId,
                                                                    UUID.randomUUID(),
                                                                    "external-integration-test"),
                                                    writeRequestId,
                                                    "collegeReviewer01",
                                                    "external-integration-test"))
                                    .isError())
                    .isFalse();
        } finally {
            gateway.close();
        }
    }

    private static McpClient client(
            String key,
            String endpoint,
            ServiceTokenProvider tokens,
            ExpenseMcpClientProperties properties) {
        return ExpenseMcpClientFactory.create(
                key,
                endpoint,
                tokens::accessToken,
                properties.connectionTimeout(),
                properties.toolTimeout());
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + "不能为空");
        }
        return value;
    }

    private static String value(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}

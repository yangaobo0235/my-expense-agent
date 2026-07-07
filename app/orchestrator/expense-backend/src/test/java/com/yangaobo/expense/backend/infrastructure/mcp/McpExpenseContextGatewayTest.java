package com.yangaobo.expense.backend.infrastructure.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangaobo.expense.agents.mcp.ExpenseMcpGateway;
import dev.langchain4j.service.tool.ToolExecutionResult;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class McpExpenseContextGatewayTest {

    private final ExpenseMcpGateway mcpGateway =
            mock(ExpenseMcpGateway.class);
    private final McpExpenseContextGateway gateway =
            new McpExpenseContextGateway(
                    mcpGateway,
                    new ObjectMapper(),
                    new McpRetryExecutor(2, java.time.Duration.ZERO));

    @Test
    void shouldBuildEmployeeContextFromMcpTools() {
        when(mcpGateway.executeReadOnly(
                        eq("get_employee_profile"), anyString()))
                .thenReturn(
                        result(
                                """
                                {"employeeId":"employee01","departmentCode":"IT",
                                 "employeeGrade":"G6","region":"CN"}
                                """));
        when(mcpGateway.executeReadOnly(
                        eq("get_payment_methods"), anyString()))
                .thenReturn(
                        result(
                                """
                                ["CORPORATE_CARD","PERSONAL_ADVANCE"]
                                """));

        var context =
                gateway.employeeContext(
                        "employee01", "fallback", "fallback");

        assertThat(context.departmentCode()).isEqualTo("IT");
        assertThat(context.employeeGrade()).isEqualTo("G6");
        assertThat(context.region()).isEqualTo("CN");
        assertThat(context.paymentMethods())
                .containsExactly(
                        "CORPORATE_CARD", "PERSONAL_ADVANCE");
        assertThat(context.source()).isEqualTo("MCP");
    }

    @Test
    void shouldAggregateDuplicateEvidenceAcrossDocuments() {
        when(mcpGateway.executeReadOnly(
                        eq("check_duplicate_document"),
                        anyString()))
                .thenReturn(
                        result(
                                """
                                {"sha256":"abc123","duplicate":true,
                                 "matches":[{"caseId":"10000000-0000-0000-0000-000000000001"}]}
                                """));

        var check =
                gateway.duplicateCheck(
                        UUID.randomUUID(),
                        List.of("abc123"),
                        false);

        assertThat(check.duplicate()).isTrue();
        assertThat(check.duplicateSha256())
                .containsExactly("abc123");
        assertThat(check.evidence()).containsKey("abc123");
        assertThat(check.source()).isEqualTo("MCP");
    }

    private static ToolExecutionResult result(String json) {
        return ToolExecutionResult.builder().resultText(json).build();
    }
}

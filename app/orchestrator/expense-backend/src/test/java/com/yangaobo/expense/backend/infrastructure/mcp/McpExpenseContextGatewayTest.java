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
    void shouldBuildApplicantContextFromMcpTools() {
        when(mcpGateway.executeReadOnly(
                        eq("get_applicant_profile"), anyString()))
                .thenReturn(
                        result(
                                """
                                {"applicantId":"student01","projectCode":"CS-SRTP",
                                 "campusLevel":"UNDERGRADUATE","region":"CN"}
                                """));
        when(mcpGateway.executeReadOnly(
                        eq("get_reimbursement_accounts"), anyString()))
                .thenReturn(
                        result(
                                """
                                ["CAMPUS_CARD","PERSONAL_ADVANCE"]
                                """));

        var context =
                gateway.applicantContext(
                        "student01", "CS-SRTP");

        assertThat(context.projectCode()).isEqualTo("CS-SRTP");
        assertThat(context.applicantType()).isEqualTo("UNDERGRADUATE");
        assertThat(context.region()).isEqualTo("CN");
        assertThat(context.reimbursementAccounts())
                .containsExactly(
                        "CAMPUS_CARD", "PERSONAL_ADVANCE");
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
                        List.of("abc123"));

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

package com.yangaobo.expense.agents.mcp;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.service.tool.ToolExecutionResult;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExpenseMcpGatewayTest {

    private final McpClient account = client("account");
    private final McpClient expense = client("expense");
    private final McpClient audit = client("audit-history");
    private final ExpenseMcpGateway gateway =
            new ExpenseMcpGateway(List.of(account, expense, audit));

    @Test
    void shouldRouteReadToolToOwningClient() {
        gateway.executeReadOnly(
                "get_applicant_profile", "{\"applicantId\":\"student01\"}");

        verify(account)
                .executeTool(
                        argThat(
                                request ->
                                        request.name()
                                                .equals(
                                                        "get_applicant_profile")));
    }

    @Test
    void shouldBlockWriteToolFromReadOnlyEntry() {
        assertThatThrownBy(
                        () ->
                                gateway.executeReadOnly(
                                        "submit_fund_posting", "{}"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("写 Tool");
    }

    @Test
    void shouldAllowWriteOnlyWithApprovalEnvelope() {
        gateway.executeApprovedWrite(
                new ApprovedMcpWriteRequest(
                        "save_review_evidence",
                        "{\"requestId\":\"r1\"}",
                        "r1",
                        "collegeReviewer01",
                        "review-task-1"));

        verify(audit)
                .executeTool(
                        argThat(
                                request ->
                                        request.name()
                                                .equals(
                                                        "save_review_evidence")));
    }

    @Test
    void shouldRejectReadToolFromApprovedWriteEntry() {
        assertThatThrownBy(
                        () ->
                                gateway.executeApprovedWrite(
                                        new ApprovedMcpWriteRequest(
                                                "get_fund_reimbursement_history",
                                                "{}",
                                                "r1",
                                                "collegeReviewer01",
                                                "review-task-1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("只允许调用写 Tool");
    }

    private static McpClient client(String key) {
        McpClient client = mock(McpClient.class);
        when(client.key()).thenReturn(key);
        when(client.executeTool(org.mockito.ArgumentMatchers.any()))
                .thenReturn(
                        ToolExecutionResult.builder()
                                .resultText("{}")
                                .build());
        return client;
    }
}

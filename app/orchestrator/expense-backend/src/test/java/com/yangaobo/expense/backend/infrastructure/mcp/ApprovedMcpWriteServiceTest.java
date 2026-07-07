package com.yangaobo.expense.backend.infrastructure.mcp;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangaobo.expense.agents.mcp.ExpenseMcpGateway;
import com.yangaobo.expense.backend.application.ExpenseCaseApplicationService;
import com.yangaobo.expense.backend.domain.model.ExpenseCase;
import com.yangaobo.expense.backend.domain.model.Money;
import com.yangaobo.expense.common.domain.ExpenseCaseStatus;
import com.yangaobo.expense.common.error.ExpenseFlowException;
import dev.langchain4j.service.tool.ToolExecutionResult;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ApprovedMcpWriteServiceTest {

    private final ExpenseMcpGateway gateway =
            mock(ExpenseMcpGateway.class);
    private final ExpenseCaseApplicationService caseService =
            mock(ExpenseCaseApplicationService.class);
    private final ApprovedMcpWriteService service =
            new ApprovedMcpWriteService(
                    gateway,
                    caseService,
                    new ObjectMapper(),
                    new McpRetryExecutor(
                            2, java.time.Duration.ZERO));

    @Test
    void shouldRejectReimbursementBeforeApproval() {
        UUID caseId = UUID.randomUUID();
        when(caseService.getById(caseId))
                .thenReturn(
                        expenseCase(
                                caseId,
                                ExpenseCaseStatus.WAITING_HUMAN));

        assertThatThrownBy(
                        () ->
                                service.submitReimbursement(
                                        caseId,
                                        new BigDecimal("100"),
                                        "CNY",
                                        "request-1",
                                        "reviewer01",
                                        "review-task-1"))
                .isInstanceOf(ExpenseFlowException.class)
                .hasMessageContaining("只有已批准");
    }

    @Test
    void shouldCallWriteGatewayForApprovedCase() {
        UUID caseId = UUID.randomUUID();
        when(caseService.getById(caseId))
                .thenReturn(
                        expenseCase(
                                caseId, ExpenseCaseStatus.APPROVED));
        when(gateway.executeApprovedWrite(any()))
                .thenReturn(
                        ToolExecutionResult.builder()
                                .resultText(
                                        "{\"status\":\"SUBMITTED\"}")
                                .build());

        service.submitReimbursement(
                caseId,
                new BigDecimal("100"),
                "CNY",
                "request-1",
                "reviewer01",
                "review-task-1");

        verify(gateway).executeApprovedWrite(any());
    }

    private static ExpenseCase expenseCase(
            UUID id, ExpenseCaseStatus status) {
        return new ExpenseCase(
                id,
                "EF-20260619-001",
                "employee01",
                "费用员工",
                "IT",
                "测试报销",
                new Money(new BigDecimal("500"), "CNY"),
                status,
                null,
                null,
                null,
                null,
                0,
                Instant.parse("2026-06-19T00:00:00Z"),
                Instant.parse("2026-06-19T00:00:00Z"));
    }
}

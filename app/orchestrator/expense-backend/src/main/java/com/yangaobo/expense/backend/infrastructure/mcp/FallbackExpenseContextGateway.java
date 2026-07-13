package com.yangaobo.expense.backend.infrastructure.mcp;

import com.yangaobo.expense.backend.application.workflow.ExpenseContextGateway;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "expense.mcp.client",
        name = "enabled",
        havingValue = "false",
        matchIfMissing = false)
public class FallbackExpenseContextGateway
        implements ExpenseContextGateway {

    @Override
    public ApplicantContext applicantContext(
            String applicantId,
            String projectCode) {
        return new ApplicantContext(
                applicantId,
                projectCode,
                "UNKNOWN",
                "CN",
                List.of(),
                "MCP_DISABLED",
                true,
                "MCP Client 未启用");
    }

    @Override
    public ProjectBudget projectBudget(String applicantId, String projectCode) {
        return new ProjectBudget(
                applicantId,
                projectCode,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "CNY",
                0,
                Instant.EPOCH,
                "MCP_DISABLED",
                true,
                "MCP Client 未启用");
    }

    @Override
    public ReimbursementHistory reimbursementHistory(String applicantId) {
        return new ReimbursementHistory(
                List.of(), "MCP_DISABLED", true, "MCP Client 未启用");
    }

    @Override
    public DuplicateCheck duplicateCheck(
            UUID currentCaseId,
            List<String> documentSha256) {
        return new DuplicateCheck(
                false,
                List.of(),
                Map.of("documentSha256", List.copyOf(documentSha256)),
                "MCP_DISABLED",
                true,
                "MCP Client 未启用");
    }
}

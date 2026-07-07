package com.yangaobo.expense.backend.infrastructure.mcp;

import com.yangaobo.expense.backend.application.workflow.ExpenseContextGateway;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
    public EmployeeContext employeeContext(
            String employeeId,
            String fallbackRegion,
            String fallbackEmployeeGrade) {
        return new EmployeeContext(
                employeeId,
                "",
                fallbackEmployeeGrade,
                fallbackRegion,
                List.of(),
                "WORKFLOW_COMMAND",
                false,
                "");
    }

    @Override
    public DuplicateCheck duplicateCheck(
            UUID currentCaseId,
            List<String> documentSha256,
            boolean fallbackDuplicate) {
        return new DuplicateCheck(
                fallbackDuplicate,
                fallbackDuplicate ? List.copyOf(documentSha256) : List.of(),
                Map.of("commandDuplicate", fallbackDuplicate),
                "WORKFLOW_COMMAND",
                false,
                "");
    }
}

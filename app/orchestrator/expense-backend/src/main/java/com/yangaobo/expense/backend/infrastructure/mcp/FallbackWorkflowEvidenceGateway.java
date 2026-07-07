package com.yangaobo.expense.backend.infrastructure.mcp;

import com.yangaobo.expense.backend.application.workflow.WorkflowEvidenceGateway;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "expense.mcp.client",
        name = "enabled",
        havingValue = "false",
        matchIfMissing = false)
public class FallbackWorkflowEvidenceGateway
        implements WorkflowEvidenceGateway {

    @Override
    public EvidenceResult saveDuplicateEvidence(
            UUID caseId,
            UUID workflowRunId,
            String requestId,
            String actorSubject,
            String contentHash) {
        return new EvidenceResult(false, "", "MCP_DISABLED");
    }
}

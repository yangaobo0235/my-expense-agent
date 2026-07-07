package com.yangaobo.expense.backend.infrastructure.mcp;

import com.yangaobo.expense.backend.application.workflow.WorkflowEvidenceGateway;
import java.util.UUID;

public class McpWorkflowEvidenceGateway
        implements WorkflowEvidenceGateway {

    private final ApprovedMcpWriteService writeService;

    public McpWorkflowEvidenceGateway(
            ApprovedMcpWriteService writeService) {
        this.writeService = writeService;
    }

    @Override
    public EvidenceResult saveDuplicateEvidence(
            UUID caseId,
            UUID workflowRunId,
            String requestId,
            String actorSubject,
            String contentHash) {
        var result =
                writeService.saveReviewEvidence(
                        caseId,
                        "DUPLICATE_CHECK",
                        contentHash,
                        requestId,
                        actorSubject,
                        workflowRunId);
        return new EvidenceResult(
                result.success(), result.resultText(), "MCP");
    }
}

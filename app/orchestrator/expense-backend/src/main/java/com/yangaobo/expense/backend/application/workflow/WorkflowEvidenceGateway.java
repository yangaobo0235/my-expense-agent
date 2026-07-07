package com.yangaobo.expense.backend.application.workflow;

import java.util.UUID;

public interface WorkflowEvidenceGateway {

    EvidenceResult saveDuplicateEvidence(
            UUID caseId,
            UUID workflowRunId,
            String requestId,
            String actorSubject,
            String contentHash);

    record EvidenceResult(
            boolean saved, String reference, String source) {}
}

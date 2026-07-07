package com.yangaobo.expense.backend.interfaces.rest;

import com.yangaobo.expense.backend.application.policy.PolicyImportResult;
import java.util.UUID;

public record PolicyImportResponse(
        UUID policyId,
        String policyCode,
        String version,
        int chunkCount,
        String embeddingModel,
        String contentHash) {

    static PolicyImportResponse from(PolicyImportResult result) {
        return new PolicyImportResponse(
                result.policyId(),
                result.policyCode(),
                result.version(),
                result.chunkCount(),
                result.embeddingModel(),
                result.contentHash());
    }
}

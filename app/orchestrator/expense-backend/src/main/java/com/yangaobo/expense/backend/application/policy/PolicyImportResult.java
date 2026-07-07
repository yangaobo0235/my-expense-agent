package com.yangaobo.expense.backend.application.policy;

import java.util.UUID;

public record PolicyImportResult(
        UUID policyId,
        String policyCode,
        String version,
        int chunkCount,
        String embeddingModel,
        String contentHash) {}

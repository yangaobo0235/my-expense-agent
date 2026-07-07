package com.yangaobo.expense.backend.application.prompt;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record PromptTemplate(
        UUID id,
        String promptKey,
        String version,
        String name,
        String description,
        String content,
        Map<String, Object> variableSchema,
        String modelName,
        BigDecimal temperature,
        int maxTokens,
        PromptStatus status,
        String promptHash,
        String createdBy,
        String updatedBy,
        String approvedBy,
        Instant createdAt,
        Instant updatedAt,
        Instant approvedAt,
        Instant activatedAt,
        String replacedVersion) {

    public PromptTemplate {
        variableSchema = variableSchema == null ? Map.of() : Map.copyOf(variableSchema);
    }
}

package com.yangaobo.expense.backend.interfaces.rest;

import com.yangaobo.expense.backend.application.prompt.PromptTemplate;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record PromptTemplateResponse(
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
        String status,
        String promptHash,
        String createdBy,
        String updatedBy,
        String approvedBy,
        Instant createdAt,
        Instant updatedAt,
        Instant approvedAt,
        Instant activatedAt,
        String replacedVersion) {

    static PromptTemplateResponse from(PromptTemplate template) {
        return new PromptTemplateResponse(
                template.id(),
                template.promptKey(),
                template.version(),
                template.name(),
                template.description(),
                template.content(),
                template.variableSchema(),
                template.modelName(),
                template.temperature(),
                template.maxTokens(),
                template.status().name(),
                template.promptHash(),
                template.createdBy(),
                template.updatedBy(),
                template.approvedBy(),
                template.createdAt(),
                template.updatedAt(),
                template.approvedAt(),
                template.activatedAt(),
                template.replacedVersion());
    }
}

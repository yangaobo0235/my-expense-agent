package com.yangaobo.expense.backend.interfaces.rest;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.util.Map;

public record PromptTemplateRequest(
        @NotBlank String promptKey,
        @NotBlank String version,
        @NotBlank String name,
        String description,
        @NotBlank String content,
        Map<String, Object> variableSchema,
        @NotBlank String modelName,
        @DecimalMin("0.0") @DecimalMax("2.0") BigDecimal temperature,
        @Min(1) @Max(128000) int maxTokens) {}

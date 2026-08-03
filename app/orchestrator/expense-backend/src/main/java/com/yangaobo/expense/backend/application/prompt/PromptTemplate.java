package com.yangaobo.expense.backend.application.prompt;

import java.math.BigDecimal;

public record PromptTemplate(
        String promptKey,
        String version,
        String content,
        String modelName,
        BigDecimal temperature,
        int maxTokens) {}

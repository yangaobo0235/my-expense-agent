package com.yangaobo.expense.backend.application.prompt;

import java.math.BigDecimal;

public record RenderedPrompt(
        String promptKey,
        String version,
        String content,
        String promptHash,
        String modelName,
        BigDecimal temperature,
        int maxTokens) {}

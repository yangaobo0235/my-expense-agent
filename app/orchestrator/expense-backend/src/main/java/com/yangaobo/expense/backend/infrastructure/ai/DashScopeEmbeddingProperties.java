package com.yangaobo.expense.backend.infrastructure.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "expense.ai.embedding")
public record DashScopeEmbeddingProperties(
        String provider,
        String baseUrl,
        String apiKey,
        String model,
        int dimensions) {

    public DashScopeEmbeddingProperties {
        provider = provider == null ? "dashscope" : provider;
        baseUrl =
                baseUrl == null
                        ? "https://dashscope.aliyuncs.com/compatible-mode/v1"
                        : baseUrl.replaceAll("/+$", "");
        model = model == null ? "text-embedding-v4" : model;
        dimensions = dimensions <= 0 ? 1024 : dimensions;
    }
}

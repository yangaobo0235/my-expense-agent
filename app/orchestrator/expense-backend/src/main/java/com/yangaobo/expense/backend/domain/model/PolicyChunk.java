package com.yangaobo.expense.backend.domain.model;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record PolicyChunk(
        UUID id,
        UUID policyId,
        int chunkIndex,
        String section,
        String content,
        int tokenCount,
        Map<String, String> metadata,
        float[] embedding,
        Instant createdAt) {

    public PolicyChunk {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(policyId, "policyId");
        if (chunkIndex < 0) {
            throw new IllegalArgumentException("chunkIndex must not be negative");
        }
        if (section == null || section.isBlank()) {
            throw new IllegalArgumentException("section is required");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content is required");
        }
        if (tokenCount <= 0) {
            throw new IllegalArgumentException("tokenCount must be positive");
        }
        metadata = Map.copyOf(metadata);
        embedding = embedding.clone();
        Objects.requireNonNull(createdAt, "createdAt");
    }

    @Override
    public float[] embedding() {
        return embedding.clone();
    }
}

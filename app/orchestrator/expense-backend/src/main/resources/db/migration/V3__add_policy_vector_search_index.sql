CREATE INDEX idx_expense_policy_chunk_embedding_hnsw
    ON expense_policy_chunk
    USING hnsw (embedding vector_cosine_ops)
    WHERE embedding IS NOT NULL;

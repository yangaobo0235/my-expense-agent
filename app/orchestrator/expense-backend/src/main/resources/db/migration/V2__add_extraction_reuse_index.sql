CREATE INDEX idx_expense_document_extraction_reuse
    ON expense_document (sha256, prompt_version, updated_at DESC)
    WHERE extraction_result <> '{}'::jsonb
      AND validation_errors = '[]'::jsonb;

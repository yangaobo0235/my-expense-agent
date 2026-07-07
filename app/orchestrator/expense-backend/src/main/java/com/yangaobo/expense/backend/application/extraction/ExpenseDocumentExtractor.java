package com.yangaobo.expense.backend.application.extraction;

public interface ExpenseDocumentExtractor {

    String promptVersion();

    ExtractionCandidate extract(PreparedDocument document);
}

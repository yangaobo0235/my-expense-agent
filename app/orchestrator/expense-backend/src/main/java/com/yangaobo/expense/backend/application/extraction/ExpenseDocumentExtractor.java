package com.yangaobo.expense.backend.application.extraction;

import java.util.List;

public interface ExpenseDocumentExtractor {

    String promptVersion();

    ExtractionCandidate extract(PreparedDocument document);

    default ExtractionCandidate repair(
            PreparedDocument document,
            ExtractionCandidate candidate,
            List<ExtractionValidationError> errors) {
        throw new UnsupportedOperationException("当前抽取器不支持语义修正");
    }

    default boolean supportsRepair() {
        return false;
    }
}

package com.yangaobo.expense.backend.interfaces.rest;

import com.yangaobo.expense.backend.application.extraction.CaseExtractionResult;
import com.yangaobo.expense.backend.application.extraction.ExtractedExpenseDocument;
import java.util.List;
import java.util.UUID;

public record ExtractionResponse(
        UUID caseId, boolean requiresHumanReview, List<DocumentResult> documents) {

    public static ExtractionResponse from(CaseExtractionResult result) {
        return new ExtractionResponse(
                result.caseId(),
                result.requiresHumanReview(),
                result.documents().stream()
                        .map(
                                outcome ->
                                        new DocumentResult(
                                                outcome.documentId(),
                                                outcome.result(),
                                                outcome.validationErrors(),
                                                outcome.reused()))
                        .toList());
    }

    public record DocumentResult(
            UUID documentId,
            ExtractedExpenseDocument result,
            List<String> validationErrors,
            boolean reused) {}
}

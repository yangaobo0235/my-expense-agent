package com.yangaobo.expense.backend.interfaces.rest;

import com.yangaobo.expense.backend.application.document.DocumentQueryService;
import com.yangaobo.expense.backend.application.extraction.ExtractedExpenseDocument;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ExpenseDocumentDetailResponse(
        UUID id,
        String originalFilename,
        String contentType,
        long fileSize,
        String sha256,
        URI previewUrl,
        Instant previewExpiresAt,
        Extraction extraction,
        Instant createdAt) {

    static ExpenseDocumentDetailResponse from(DocumentQueryService.DocumentView view) {
        Extraction extraction =
                view.extraction() == null
                        ? null
                        : new Extraction(
                                view.extraction().result(),
                                view.extraction().validationErrors(),
                                view.extraction().modelName(),
                                view.extraction().promptVersion(),
                                view.extraction().tokenUsage(),
                                view.extraction().extractionLatencyMs(),
                                view.extraction().extractorMode());
        return new ExpenseDocumentDetailResponse(
                view.id(),
                view.originalFilename(),
                view.contentType(),
                view.fileSize(),
                view.sha256(),
                view.previewUrl(),
                view.previewExpiresAt(),
                extraction,
                view.createdAt());
    }

    public record Extraction(
            ExtractedExpenseDocument result,
            List<String> validationErrors,
            String modelName,
            String promptVersion,
            int tokenUsage,
            long extractionLatencyMs,
            String extractorMode) {}
}

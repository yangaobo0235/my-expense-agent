package com.yangaobo.expense.backend.interfaces.rest;

import com.yangaobo.expense.backend.domain.model.ExpenseDocument;
import java.time.Instant;
import java.util.UUID;

public record ExpenseDocumentResponse(
        UUID id,
        UUID caseId,
        String originalFilename,
        String contentType,
        long fileSize,
        String sha256,
        Instant createdAt) {

    static ExpenseDocumentResponse from(ExpenseDocument document) {
        return new ExpenseDocumentResponse(
                document.id(),
                document.caseId(),
                document.originalFilename(),
                document.contentType(),
                document.fileSize(),
                document.sha256(),
                document.createdAt());
    }
}

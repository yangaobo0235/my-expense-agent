package com.yangaobo.expense.backend.domain.repository;

import com.yangaobo.expense.backend.domain.model.ExpenseDocument;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface ExpenseDocumentRepository {

    ExpenseDocument insert(ExpenseDocument document);

    Optional<ExpenseDocument> findByCaseIdAndSha256(UUID caseId, String sha256);

    int countByCaseId(UUID caseId);

    Optional<ExpenseDocument> findById(UUID documentId);

    List<ExpenseDocument> findByCaseId(UUID caseId);

    default List<StoredExtractionResult> findExtractionResultsByCaseId(UUID caseId) {
        return List.of();
    }

    Optional<StoredExtractionResult> findExtractionByDocumentId(UUID documentId);

    Optional<StoredExtractionResult> findReusableExtraction(
            String sha256, String promptVersion);

    void saveExtraction(UUID documentId, StoredExtractionResult result);
}

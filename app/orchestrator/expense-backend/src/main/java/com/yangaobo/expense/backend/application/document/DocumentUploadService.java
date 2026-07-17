package com.yangaobo.expense.backend.application.document;

import com.yangaobo.expense.backend.application.ExpenseCaseApplicationService;
import com.yangaobo.expense.backend.application.storage.DocumentObjectStorage;
import com.yangaobo.expense.backend.domain.model.ExpenseCase;
import com.yangaobo.expense.backend.domain.model.ExpenseDocument;
import com.yangaobo.expense.backend.domain.repository.ExpenseDocumentRepository;
import com.yangaobo.expense.common.domain.ExpenseCaseStatus;
import com.yangaobo.expense.common.error.MyExpenseAgentErrorCode;
import com.yangaobo.expense.common.error.MyExpenseAgentException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentUploadService {

    static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    static final int MAX_DOCUMENTS_PER_CASE = 20;

    private final ExpenseCaseApplicationService caseApplicationService;
    private final ExpenseDocumentRepository documentRepository;
    private final DocumentPersistenceService persistenceService;
    private final DocumentObjectStorage objectStorage;
    private final DocumentContentInspector contentInspector;
    private final Clock clock;

    public DocumentUploadService(
            ExpenseCaseApplicationService caseApplicationService,
            ExpenseDocumentRepository documentRepository,
            DocumentPersistenceService persistenceService,
            DocumentObjectStorage objectStorage,
            DocumentContentInspector contentInspector,
            Clock clock) {
        this.caseApplicationService = caseApplicationService;
        this.documentRepository = documentRepository;
        this.persistenceService = persistenceService;
        this.objectStorage = objectStorage;
        this.contentInspector = contentInspector;
        this.clock = clock;
    }

    public ExpenseDocument upload(UUID caseId, String ownerSubject, MultipartFile file) {
        ExpenseCase expenseCase = caseApplicationService.getOwned(caseId, ownerSubject);
        requireUploadable(expenseCase);
        byte[] content = readAndValidateSize(file);
        DetectedDocumentType detectedType = contentInspector.detect(content);
        String sha256 = contentInspector.sha256(content);

        var existing = documentRepository.findByCaseIdAndSha256(caseId, sha256);
        if (existing.isPresent()) {
            return existing.get();
        }
        if (documentRepository.countByCaseId(caseId) >= MAX_DOCUMENTS_PER_CASE) {
            throw rejected("An expense case can contain at most 20 documents");
        }

        UUID documentId = UUID.randomUUID();
        String objectKey =
                "%s/%s/%s.%s"
                        .formatted(
                                caseId,
                                documentId,
                                UUID.randomUUID(),
                                detectedType.extension());
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        ExpenseDocument document =
                new ExpenseDocument(
                        documentId,
                        caseId,
                        safeFilename(file.getOriginalFilename()),
                        detectedType.contentType(),
                        content.length,
                        sha256,
                        objectKey,
                        now,
                        now);

        objectStorage.put(
                objectKey,
                detectedType.contentType(),
                content.length,
                new ByteArrayInputStream(content));
        try {
            return persistenceService.save(expenseCase, document);
        } catch (RuntimeException exception) {
            try {
                objectStorage.delete(objectKey);
            } catch (RuntimeException cleanupFailure) {
                exception.addSuppressed(cleanupFailure);
            }
            throw exception;
        }
    }

    private static byte[] readAndValidateSize(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw rejected("Document must not be empty");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw rejected("Document exceeds the 10 MB limit");
        }
        try {
            byte[] content = file.getBytes();
            if (content.length == 0 || content.length > MAX_FILE_SIZE) {
                throw rejected("Document size is invalid");
            }
            return content;
        } catch (IOException exception) {
            MyExpenseAgentException failure =
                    rejected("Could not read the uploaded document");
            failure.initCause(exception);
            throw failure;
        }
    }

    private static void requireUploadable(ExpenseCase expenseCase) {
        if (expenseCase.status() != ExpenseCaseStatus.DRAFT
                && expenseCase.status() != ExpenseCaseStatus.UPLOADED) {
            throw rejected("Documents cannot be added after analysis has started");
        }
    }

    private static String safeFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "document";
        }
        String normalized = originalFilename.replace('\\', '/');
        String filename = normalized.substring(normalized.lastIndexOf('/') + 1).trim();
        if (filename.isBlank()) {
            return "document";
        }
        return filename.length() <= 512 ? filename : filename.substring(filename.length() - 512);
    }

    private static MyExpenseAgentException rejected(String message) {
        return new MyExpenseAgentException(MyExpenseAgentErrorCode.DOCUMENT_REJECTED, message);
    }
}

package com.yangaobo.expense.backend.application.extraction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangaobo.expense.backend.application.ExpenseCaseApplicationService;
import com.yangaobo.expense.backend.application.observability.ModelCallRecorder;
import com.yangaobo.expense.backend.application.storage.DocumentObjectStorage;
import com.yangaobo.expense.backend.domain.model.ExpenseCase;
import com.yangaobo.expense.backend.domain.model.ExpenseDocument;
import com.yangaobo.expense.backend.domain.repository.ExpenseDocumentRepository;
import com.yangaobo.expense.backend.domain.repository.StoredExtractionResult;
import com.yangaobo.expense.common.domain.ExpenseCaseStatus;
import com.yangaobo.expense.common.error.ExpenseFlowErrorCode;
import com.yangaobo.expense.common.error.ExpenseFlowException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ExpenseExtractionService {

    private final ExpenseCaseApplicationService caseService;
    private final ExpenseDocumentRepository documentRepository;
    private final DocumentObjectStorage objectStorage;
    private final DocumentPreprocessor preprocessor;
    private final ExpenseDocumentExtractor extractor;
    private final ExpenseExtractionValidator validator;
    private final ObjectMapper objectMapper;
    private final ModelCallRecorder modelCallRecorder;

    public ExpenseExtractionService(
            ExpenseCaseApplicationService caseService,
            ExpenseDocumentRepository documentRepository,
            DocumentObjectStorage objectStorage,
            DocumentPreprocessor preprocessor,
            ExpenseDocumentExtractor extractor,
            ExpenseExtractionValidator validator,
            ObjectMapper objectMapper,
            ModelCallRecorder modelCallRecorder) {
        this.caseService = caseService;
        this.documentRepository = documentRepository;
        this.objectStorage = objectStorage;
        this.preprocessor = preprocessor;
        this.extractor = extractor;
        this.validator = validator;
        this.objectMapper = objectMapper;
        this.modelCallRecorder = modelCallRecorder;
    }

    public CaseExtractionResult extract(UUID caseId, String ownerSubject) {
        ExpenseCase expenseCase = caseService.getOwned(caseId, ownerSubject);
        if (expenseCase.status() != ExpenseCaseStatus.UPLOADED
                && expenseCase.status() != ExpenseCaseStatus.FAILED) {
            throw new ExpenseFlowException(
                    ExpenseFlowErrorCode.INVALID_STATE_TRANSITION,
                    "Only UPLOADED or FAILED cases can start extraction");
        }
        List<ExpenseDocument> documents = documentRepository.findByCaseId(caseId);
        if (documents.isEmpty()) {
            throw new ExpenseFlowException(
                    ExpenseFlowErrorCode.VALIDATION_FAILED,
                    "At least one document is required before extraction");
        }

        caseService.transition(caseId, ExpenseCaseStatus.EXTRACTING);
        try {
            List<DocumentExtractionOutcome> outcomes = new ArrayList<>();
            for (ExpenseDocument document : documents) {
                outcomes.add(extractDocument(document));
            }
            caseService.transition(caseId, ExpenseCaseStatus.EXTRACTED);
            return new CaseExtractionResult(caseId, outcomes);
        } catch (RuntimeException exception) {
            caseService.fail(caseId, "DOCUMENT_EXTRACTION", safeMessage(exception));
            throw exception;
        }
    }

    private DocumentExtractionOutcome extractDocument(ExpenseDocument document) {
        var reusable =
                documentRepository.findReusableExtraction(
                        document.sha256(), extractor.promptVersion());
        if (reusable.isPresent()) {
            StoredExtractionResult stored = reusable.get();
            documentRepository.saveExtraction(document.id(), stored);
            return new DocumentExtractionOutcome(
                    document.id(),
                    readDocument(stored.resultJson()),
                    stored.validationErrors(),
                    true);
        }

        byte[] content = objectStorage.get(document.objectKey());
        PreparedDocument prepared = preprocessor.prepare(content, document.contentType());
        long startedNanos = System.nanoTime();
        ExtractionCandidate candidate;
        try {
            candidate = extractor.extract(prepared);
        } catch (RuntimeException exception) {
            modelCallRecorder.failed(
                    document.caseId(),
                    null,
                    "DOCUMENT_EXTRACTION",
                    "expense-document-extractor",
                    extractor.promptVersion(),
                    extractor.promptVersion(),
                    document.sha256(),
                    elapsedMs(startedNanos),
                    0,
                    exception instanceof ExpenseFlowException flowException
                            ? flowException.code().name()
                            : ExpenseFlowErrorCode.INTERNAL_ERROR.name());
            throw exception;
        }
        ExtractionValidationResult validation = validator.validate(candidate.document());
        String resultJson = writeDocument(candidate.document());
        modelCallRecorder.succeeded(
                document.caseId(),
                null,
                "DOCUMENT_EXTRACTION",
                candidate.modelName(),
                candidate.promptVersion(),
                candidate.promptVersion(),
                document.sha256(),
                resultJson,
                candidate.promptTokens(),
                candidate.completionTokens(),
                candidate.latencyMs() > 0 ? candidate.latencyMs() : elapsedMs(startedNanos),
                0);
        StoredExtractionResult stored =
                new StoredExtractionResult(
                        candidate.document().documentType(),
                        normalizedConfidence(candidate.document().confidence()),
                        resultJson,
                        validation.errors(),
                        candidate.modelName(),
                        candidate.promptVersion(),
                        candidate.rawResponseHash(),
                        candidate.totalTokens(),
                        candidate.latencyMs() > 0 ? candidate.latencyMs() : elapsedMs(startedNanos),
                        candidate.extractorMode());
        documentRepository.saveExtraction(document.id(), stored);
        return new DocumentExtractionOutcome(
                document.id(), candidate.document(), validation.errors(), false);
    }

    private String writeDocument(ExtractedExpenseDocument document) {
        try {
            return objectMapper.writeValueAsString(document);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize extraction result", exception);
        }
    }

    private ExtractedExpenseDocument readDocument(String json) {
        try {
            return objectMapper.readValue(json, ExtractedExpenseDocument.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored extraction result is invalid", exception);
        }
    }

    private static double normalizedConfidence(double confidence) {
        return Math.max(0, Math.min(1, confidence));
    }

    private static long elapsedMs(long startedNanos) {
        return Math.max(0, java.time.Duration.ofNanos(System.nanoTime() - startedNanos).toMillis());
    }

    private static String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }
}

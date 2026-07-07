package com.yangaobo.expense.backend.application.document;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangaobo.expense.backend.application.ExpenseCaseApplicationService;
import com.yangaobo.expense.backend.application.extraction.ExtractedExpenseDocument;
import com.yangaobo.expense.backend.application.storage.DocumentObjectStorage;
import com.yangaobo.expense.backend.domain.repository.ExpenseDocumentRepository;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentQueryService {

    private static final Duration PREVIEW_VALIDITY = Duration.ofMinutes(5);

    private final ExpenseCaseApplicationService caseService;
    private final ExpenseDocumentRepository documentRepository;
    private final DocumentObjectStorage objectStorage;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public DocumentQueryService(
            ExpenseCaseApplicationService caseService,
            ExpenseDocumentRepository documentRepository,
            DocumentObjectStorage objectStorage,
            ObjectMapper objectMapper,
            Clock clock) {
        this.caseService = caseService;
        this.documentRepository = documentRepository;
        this.objectStorage = objectStorage;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<DocumentView> list(
            UUID caseId, String subject, boolean privileged) {
        if (privileged) {
            caseService.getById(caseId);
        } else {
            caseService.getOwned(caseId, subject);
        }
        return documentRepository.findByCaseId(caseId).stream()
                .map(
                        document -> {
                            var extraction =
                                    documentRepository
                                            .findExtractionByDocumentId(document.id())
                                            .map(
                                                    stored ->
                                                            new ExtractionView(
                                                                    readExtraction(
                                                                            stored.resultJson()),
                                                                    stored.validationErrors(),
                                                                    stored.modelName(),
                                                                    stored.promptVersion(),
                                                                    stored.tokenUsage(),
                                                                    stored.extractionLatencyMs(),
                                                                    stored.extractorMode()))
                                            .orElse(null);
                            URI previewUrl =
                                    objectStorage.presignedGetUrl(
                                            document.objectKey(), PREVIEW_VALIDITY);
                            return new DocumentView(
                                    document.id(),
                                    document.originalFilename(),
                                    document.contentType(),
                                    document.fileSize(),
                                    document.sha256(),
                                    previewUrl,
                                    clock.instant().plus(PREVIEW_VALIDITY),
                                    extraction,
                                    document.createdAt());
                        })
                .toList();
    }

    private ExtractedExpenseDocument readExtraction(String json) {
        try {
            return objectMapper.readValue(json, ExtractedExpenseDocument.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored extraction result is invalid", exception);
        }
    }

    public record DocumentView(
            UUID id,
            String originalFilename,
            String contentType,
            long fileSize,
            String sha256,
            URI previewUrl,
            Instant previewExpiresAt,
            ExtractionView extraction,
            Instant createdAt) {}

    public record ExtractionView(
            ExtractedExpenseDocument result,
            List<String> validationErrors,
            String modelName,
            String promptVersion,
            int tokenUsage,
            long extractionLatencyMs,
            String extractorMode) {}
}

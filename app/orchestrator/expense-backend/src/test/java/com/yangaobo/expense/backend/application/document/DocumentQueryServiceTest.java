package com.yangaobo.expense.backend.application.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangaobo.expense.backend.application.ExpenseCaseApplicationService;
import com.yangaobo.expense.backend.application.storage.DocumentObjectStorage;
import com.yangaobo.expense.backend.domain.model.ExpenseDocument;
import com.yangaobo.expense.backend.domain.repository.ExpenseDocumentRepository;
import com.yangaobo.expense.backend.domain.repository.StoredExtractionResult;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DocumentQueryServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-22T00:00:00Z");
    private final ExpenseCaseApplicationService caseService =
            mock(ExpenseCaseApplicationService.class);
    private final ExpenseDocumentRepository repository =
            mock(ExpenseDocumentRepository.class);
    private final DocumentObjectStorage storage = mock(DocumentObjectStorage.class);
    private final DocumentQueryService service =
            new DocumentQueryService(
                    caseService,
                    repository,
                    storage,
                    new ObjectMapper().findAndRegisterModules(),
                    Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void listsOwnedDocumentsWithShortLivedPreviewAndExtraction() {
        UUID caseId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        ExpenseDocument document =
                new ExpenseDocument(
                        documentId,
                        caseId,
                        "invoice.pdf",
                        "application/pdf",
                        128,
                        "a".repeat(64),
                        caseId + "/invoice.pdf",
                        NOW,
                        NOW);
        when(repository.findByCaseId(caseId)).thenReturn(List.of(document));
        when(repository.findExtractionByDocumentId(documentId))
                .thenReturn(
                        Optional.of(
                                new StoredExtractionResult(
                                        "INVOICE",
                                        0.95,
                                        """
                                        {
                                          "documentType":"INVOICE",
                                          "invoiceNumber":"INV-1",
                                          "sellerName":"ExpenseFlow Hotel",
                                          "totalAmount":288.00,
                                          "currency":"CNY",
                                          "items":[],
                                          "confidence":0.95,
                                          "warnings":[]
                                        }
                                        """,
                                        List.of(),
                                        "deterministic",
                                        "expense-extraction-v1",
                                        "b".repeat(64))));
        when(storage.presignedGetUrl(document.objectKey(), Duration.ofMinutes(5)))
                .thenReturn(URI.create("https://storage.test/invoice.pdf?signature=redacted"));

        var result = service.list(caseId, "employee-1", false);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().previewExpiresAt())
                .isEqualTo(NOW.plus(Duration.ofMinutes(5)));
        assertThat(result.getFirst().extraction().result().sellerName())
                .isEqualTo("ExpenseFlow Hotel");
        verify(caseService).getOwned(caseId, "employee-1");
    }

    @Test
    void privilegedReaderUsesUnscopedCaseLookup() {
        UUID caseId = UUID.randomUUID();
        when(repository.findByCaseId(caseId)).thenReturn(List.of());

        assertThat(service.list(caseId, "reviewer-1", true)).isEmpty();

        verify(caseService).getById(caseId);
    }
}

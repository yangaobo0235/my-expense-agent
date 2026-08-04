package com.yangaobo.expense.backend.application.extraction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangaobo.expense.backend.application.ExpenseCaseApplicationService;
import com.yangaobo.expense.backend.application.observability.ModelCallRecorder;
import com.yangaobo.expense.backend.application.storage.DocumentObjectStorage;
import com.yangaobo.expense.backend.domain.model.ExpenseCase;
import com.yangaobo.expense.backend.domain.model.ExpenseDocument;
import com.yangaobo.expense.backend.domain.model.Money;
import com.yangaobo.expense.backend.domain.repository.ExpenseDocumentRepository;
import com.yangaobo.expense.common.domain.ExpenseCaseStatus;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ExpenseExtractionServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-28T12:00:00Z");

    @Test
    void recordsOriginalAndRepairWhenExtractorRepairsInvalidJsonInternally() {
        UUID caseId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        ExpenseCaseApplicationService cases = mock(ExpenseCaseApplicationService.class);
        ExpenseDocumentRepository documents = mock(ExpenseDocumentRepository.class);
        DocumentObjectStorage storage = mock(DocumentObjectStorage.class);
        DocumentPreprocessor preprocessor = mock(DocumentPreprocessor.class);
        ExpenseDocumentExtractor extractor = mock(ExpenseDocumentExtractor.class);
        ExpenseExtractionValidator validator = mock(ExpenseExtractionValidator.class);
        ModelCallRecorder modelCalls = mock(ModelCallRecorder.class);
        ExtractionAttemptRepository attempts = mock(ExtractionAttemptRepository.class);
        ExpenseDocument document =
                new ExpenseDocument(
                        documentId,
                        caseId,
                        "invoice.json",
                        "application/json",
                        128,
                        "a".repeat(64),
                        "cases/" + caseId + "/invoice.json",
                        NOW,
                        NOW);
        ExtractedExpenseDocument repaired =
                new ExtractedExpenseDocument(
                        "INVOICE",
                        "INV",
                        "INV-2026-001",
                        "测试酒店",
                        "江南大学",
                        LocalDate.of(2026, 7, 28),
                        new BigDecimal("500.00"),
                        "CNY",
                        List.of(),
                        0.95,
                        List.of());
        ExtractionValidationError jsonError =
                ExtractionValidationError.repairable(
                        ExtractionErrorCode.JSON_INVALID, "response", "JSON 被截断");
        ExtractionCandidate candidate =
                new ExtractionCandidate(
                        repaired,
                        "test-model",
                        "receipt-v1",
                        "b".repeat(64),
                        80,
                        40,
                        200,
                        "llm-repair",
                        0,
                        true,
                        List.of(
                                new ExtractionAttemptMetadata(
                                        "c".repeat(64),
                                        60,
                                        20,
                                        100,
                                        0,
                                        List.of(jsonError),
                                        "VALIDATION_FAILED")));

        when(cases.getOwned(caseId, "student-1")).thenReturn(uploadedCase(caseId));
        when(documents.findByCaseId(caseId)).thenReturn(List.of(document));
        when(documents.findReusableExtraction(document.sha256(), "receipt-v1"))
                .thenReturn(Optional.empty());
        when(storage.get(document.objectKey())).thenReturn(new byte[] {1});
        PreparedDocument prepared =
                new PreparedDocument(
                        DocumentInputKind.TEXT,
                        "invoice",
                        new byte[0],
                        "text/plain",
                        1);
        when(preprocessor.prepare(any(), any())).thenReturn(prepared);
        when(extractor.promptVersion()).thenReturn("receipt-v1");
        when(extractor.extract(prepared)).thenReturn(candidate);
        when(validator.validate(repaired))
                .thenReturn(new ExtractionValidationResult(repaired, List.of()));

        ExpenseExtractionService service =
                new ExpenseExtractionService(
                        cases,
                        documents,
                        storage,
                        preprocessor,
                        extractor,
                        validator,
                        new ObjectMapper().findAndRegisterModules(),
                        modelCalls,
                        new ExtractionRepairPolicy(),
                        attempts,
                        Clock.fixed(NOW, ZoneOffset.UTC));

        CaseExtractionResult result = service.extract(caseId, "student-1");

        assertThat(result.documents()).singleElement().satisfies(outcome -> {
            assertThat(outcome.repairUsed()).isTrue();
            assertThat(outcome.manualReviewRequired()).isFalse();
        });
        ArgumentCaptor<ExtractionAttemptRepository.ExtractionAttempt> captor =
                ArgumentCaptor.forClass(ExtractionAttemptRepository.ExtractionAttempt.class);
        verify(attempts, org.mockito.Mockito.times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(
                        ExtractionAttemptRepository.ExtractionAttempt::attemptNo,
                        ExtractionAttemptRepository.ExtractionAttempt::attemptType,
                        ExtractionAttemptRepository.ExtractionAttempt::status)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(1, "ORIGINAL", "VALIDATION_FAILED"),
                        org.assertj.core.groups.Tuple.tuple(2, "REPAIR", "SUCCEEDED"));
    }

    private static ExpenseCase uploadedCase(UUID caseId) {
        return new ExpenseCase(
                caseId,
                "CF-20260728-0001",
                "student-1",
                "李明",
                "CS-SRTP",
                "竞赛住宿报销",
                new Money(new BigDecimal("500.00"), "CNY"),
                ExpenseCaseStatus.UPLOADED,
                null,
                null,
                null,
                null,
                1,
                NOW,
                NOW);
    }
}

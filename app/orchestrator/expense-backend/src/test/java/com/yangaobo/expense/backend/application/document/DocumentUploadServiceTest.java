package com.yangaobo.expense.backend.application.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yangaobo.expense.backend.application.CreateExpenseCaseCommand;
import com.yangaobo.expense.backend.application.ExpenseCaseApplicationService;
import com.yangaobo.expense.backend.application.storage.DocumentObjectStorage;
import com.yangaobo.expense.backend.domain.model.ExpenseCase;
import com.yangaobo.expense.backend.domain.model.ExpenseDocument;
import com.yangaobo.expense.backend.domain.repository.ExpenseCaseRepository;
import com.yangaobo.expense.backend.domain.repository.ExpenseDocumentRepository;
import com.yangaobo.expense.common.domain.ExpenseCaseStatus;
import com.yangaobo.expense.common.error.ExpenseFlowException;
import java.io.InputStream;
import java.net.URI;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.List;
import com.yangaobo.expense.backend.domain.repository.StoredExtractionResult;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class DocumentUploadServiceTest {

    private final Clock clock =
            Clock.fixed(Instant.parse("2026-06-18T00:00:00Z"), ZoneOffset.UTC);
    private InMemoryCaseRepository caseRepository;
    private InMemoryDocumentRepository documentRepository;
    private RecordingObjectStorage objectStorage;
    private ExpenseCaseApplicationService caseService;
    private DocumentUploadService uploadService;
    private ExpenseCase expenseCase;

    @BeforeEach
    void setUp() {
        caseRepository = new InMemoryCaseRepository();
        documentRepository = new InMemoryDocumentRepository();
        objectStorage = new RecordingObjectStorage();
        caseService =
                new ExpenseCaseApplicationService(
                        caseRepository, (now, id) -> "EF-TEST-" + id.toString().substring(0, 8), clock);
        DocumentPersistenceService persistenceService =
                new DocumentPersistenceService(documentRepository, caseService);
        uploadService =
                new DocumentUploadService(
                        caseService,
                        documentRepository,
                        persistenceService,
                        objectStorage,
                        new DocumentContentInspector(),
                        clock);
        expenseCase =
                caseService.create(
                        new CreateExpenseCaseCommand(
                                "user-a",
                                "Alice",
                                "TEST",
                                "Document upload",
                                new BigDecimal("100.00"),
                                "CNY"));
    }

    @Test
    void storesPdfAndMovesDraftCaseToUploaded() {
        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        "..\\unsafe\\invoice.pdf",
                        "text/plain",
                        "%PDF-1.7 invoice".getBytes());

        ExpenseDocument uploaded = uploadService.upload(expenseCase.id(), "user-a", file);

        assertThat(uploaded.contentType()).isEqualTo("application/pdf");
        assertThat(uploaded.originalFilename()).isEqualTo("invoice.pdf");
        assertThat(objectStorage.putCount).isEqualTo(1);
        assertThat(caseRepository.findById(expenseCase.id()).orElseThrow().status())
                .isEqualTo(ExpenseCaseStatus.UPLOADED);
    }

    @Test
    void reusesDuplicateDocumentWithoutSecondObjectWrite() {
        MockMultipartFile file =
                new MockMultipartFile(
                        "file", "invoice.pdf", "application/pdf", "%PDF-1.7 invoice".getBytes());

        ExpenseDocument first = uploadService.upload(expenseCase.id(), "user-a", file);
        ExpenseDocument duplicate = uploadService.upload(expenseCase.id(), "user-a", file);

        assertThat(duplicate.id()).isEqualTo(first.id());
        assertThat(objectStorage.putCount).isEqualTo(1);
        assertThat(documentRepository.countByCaseId(expenseCase.id())).isEqualTo(1);
    }

    @Test
    void rejectsSpoofedFileBeforeObjectWrite() {
        MockMultipartFile file =
                new MockMultipartFile(
                        "file", "invoice.pdf", "application/pdf", "plain text".getBytes());

        assertThatThrownBy(() -> uploadService.upload(expenseCase.id(), "user-a", file))
                .isInstanceOf(ExpenseFlowException.class);
        assertThat(objectStorage.putCount).isZero();
    }

    @Test
    void removesObjectWhenDatabaseWriteFails() {
        documentRepository.failInsert = true;
        MockMultipartFile file =
                new MockMultipartFile(
                        "file", "invoice.pdf", "application/pdf", "%PDF-1.7 invoice".getBytes());

        assertThatThrownBy(() -> uploadService.upload(expenseCase.id(), "user-a", file))
                .isInstanceOf(IllegalStateException.class);
        assertThat(objectStorage.putCount).isEqualTo(1);
        assertThat(objectStorage.deleteCount).isEqualTo(1);
    }

    private static final class InMemoryCaseRepository implements ExpenseCaseRepository {

        private final Map<UUID, ExpenseCase> cases = new HashMap<>();

        @Override
        public ExpenseCase insert(ExpenseCase expenseCase) {
            cases.put(expenseCase.id(), expenseCase);
            return expenseCase;
        }

        @Override
        public Optional<ExpenseCase> findById(UUID id) {
            return Optional.ofNullable(cases.get(id));
        }

        @Override
        public ExpenseCasePage search(ExpenseCaseSearchCriteria criteria) {
            List<ExpenseCase> items =
                    cases.values().stream()
                            .filter(
                                    expenseCase ->
                                            criteria.ownerSubject() == null
                                                    || criteria.ownerSubject()
                                                            .equals(
                                                                    expenseCase
                                                                            .ownerSubject()))
                            .toList();
            return new ExpenseCasePage(items, items.size());
        }

        @Override
        public ExpenseCase update(ExpenseCase expenseCase, long expectedVersion) {
            ExpenseCase current = cases.get(expenseCase.id());
            if (current == null || current.version() != expectedVersion) {
                throw new IllegalStateException("stale case");
            }
            cases.put(expenseCase.id(), expenseCase);
            return expenseCase;
        }

        @Override
        public void deleteById(UUID id, long expectedVersion) {
            ExpenseCase current = cases.get(id);
            if (current == null || current.version() != expectedVersion) {
                throw new IllegalStateException("stale case");
            }
            cases.remove(id);
        }
    }

    private static final class InMemoryDocumentRepository
            implements ExpenseDocumentRepository {

        private final Map<UUID, ExpenseDocument> documents = new HashMap<>();
        private boolean failInsert;

        @Override
        public ExpenseDocument insert(ExpenseDocument document) {
            if (failInsert) {
                throw new IllegalStateException("simulated database failure");
            }
            documents.put(document.id(), document);
            return document;
        }

        @Override
        public Optional<ExpenseDocument> findByCaseIdAndSha256(UUID caseId, String sha256) {
            return documents.values().stream()
                    .filter(
                            document ->
                                    document.caseId().equals(caseId)
                                            && document.sha256().equals(sha256))
                    .findFirst();
        }

        @Override
        public int countByCaseId(UUID caseId) {
            return (int)
                    documents.values().stream()
                            .filter(document -> document.caseId().equals(caseId))
                            .count();
        }

        @Override
        public Optional<ExpenseDocument> findById(UUID documentId) {
            return Optional.ofNullable(documents.get(documentId));
        }

        @Override
        public List<ExpenseDocument> findByCaseId(UUID caseId) {
            return documents.values().stream()
                    .filter(document -> document.caseId().equals(caseId))
                    .toList();
        }

        @Override
        public Optional<StoredExtractionResult> findReusableExtraction(
                String sha256, String promptVersion) {
            return Optional.empty();
        }

        @Override
        public Optional<StoredExtractionResult> findExtractionByDocumentId(UUID documentId) {
            return Optional.empty();
        }

        @Override
        public void saveExtraction(UUID documentId, StoredExtractionResult result) {}
    }

    private static final class RecordingObjectStorage implements DocumentObjectStorage {

        private int putCount;
        private int deleteCount;

        @Override
        public void put(
                String objectKey, String contentType, long contentLength, InputStream content) {
            putCount++;
        }

        @Override
        public void delete(String objectKey) {
            deleteCount++;
        }

        @Override
        public byte[] get(String objectKey) {
            return new byte[0];
        }

        @Override
        public URI presignedGetUrl(String objectKey, Duration validity) {
            return URI.create("https://example.test/" + objectKey);
        }
    }
}

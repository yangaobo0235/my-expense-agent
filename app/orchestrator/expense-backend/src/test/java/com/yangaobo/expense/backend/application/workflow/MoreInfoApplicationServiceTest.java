package com.yangaobo.expense.backend.application.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yangaobo.expense.backend.application.ExpenseCaseApplicationService;
import com.yangaobo.expense.backend.application.document.DocumentUploadService;
import com.yangaobo.expense.backend.application.extraction.ExpenseExtractionService;
import com.yangaobo.expense.backend.domain.model.ExpenseDocument;
import com.yangaobo.expense.backend.domain.model.RiskLevel;
import com.yangaobo.expense.common.domain.ExpenseCaseStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.multipart.MultipartFile;

class MoreInfoApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-28T12:00:00Z");

    @Test
    void submissionCreatesHigherDocumentVersionAndChildReviewRun() {
        UUID caseId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID previousRunId = UUID.randomUUID();
        UUID newRunId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        MoreInfoTaskRepository repository = mock(MoreInfoTaskRepository.class);
        WorkflowRunRepository runs = mock(WorkflowRunRepository.class);
        ExpenseCaseApplicationService cases = mock(ExpenseCaseApplicationService.class);
        DocumentUploadService uploads = mock(DocumentUploadService.class);
        ExpenseExtractionService extraction = mock(ExpenseExtractionService.class);
        ExpenseCoordinator coordinator = mock(ExpenseCoordinator.class);
        MultipartFile file = mock(MultipartFile.class);
        ExpenseDocument document =
                new ExpenseDocument(
                        documentId,
                        caseId,
                        "supplement.pdf",
                        "application/pdf",
                        128,
                        "a".repeat(64),
                        "cases/" + caseId + "/supplement.pdf",
                        NOW,
                        NOW);
        when(repository.findById(taskId))
                .thenReturn(
                        Optional.of(
                                new MoreInfoTaskRepository.MoreInfoTask(
                                        taskId,
                                        caseId,
                                        previousRunId,
                                        List.of("住宿明细"),
                                        List.of("MISSING_REQUIRED_DOCUMENT"),
                                        "OPEN",
                                        "reviewer-1",
                                        NOW.plusSeconds(3600),
                                        null,
                                        null,
                                        NOW)));
        when(uploads.upload(caseId, "student-1", file)).thenReturn(document);
        when(runs.currentDocumentVersion(caseId)).thenReturn(2);
        ExpenseWorkflowResult workflow =
                new ExpenseWorkflowResult(
                        caseId,
                        newRunId,
                        ExpenseCaseStatus.WAITING_HUMAN,
                        50,
                        RiskLevel.MEDIUM,
                        List.of(),
                        List.of(),
                        UUID.randomUUID());
        when(coordinator.analyze(eq(caseId), eq("student-1"), any()))
                .thenReturn(workflow);
        MoreInfoApplicationService service =
                new MoreInfoApplicationService(
                        repository,
                        runs,
                        cases,
                        uploads,
                        extraction,
                        coordinator,
                        Clock.fixed(NOW, ZoneOffset.UTC));

        MoreInfoApplicationService.MoreInfoSubmissionResult result =
                service.submit(
                        caseId,
                        taskId,
                        file,
                        "review-again-2",
                        "竞赛差旅费",
                        LocalDate.of(2026, 7, 28),
                        "student-1",
                        "补充住宿明细后重审");

        assertThat(result.documentVersion()).isEqualTo(2);
        assertThat(result.workflow()).isEqualTo(workflow);
        verify(cases).getOwned(caseId, "student-1");
        verify(repository).markSubmitted(taskId, 2, NOW);
        verify(repository).complete(taskId, NOW);
        ArgumentCaptor<ExpenseWorkflowCommand> command =
                ArgumentCaptor.forClass(ExpenseWorkflowCommand.class);
        verify(coordinator).analyze(eq(caseId), eq("student-1"), command.capture());
        assertThat(command.getValue().commandType()).isEqualTo(WorkflowCommandType.REVIEW_AGAIN);
        assertThat(command.getValue().documentVersion()).isEqualTo(2);
        assertThat(command.getValue().previousRunId()).isEqualTo(previousRunId);
        assertThat(command.getValue().reopenReason()).isEqualTo("补充住宿明细后重审");
    }
}

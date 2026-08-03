package com.yangaobo.expense.backend.application.workflow;

import com.yangaobo.expense.backend.application.ExpenseCaseApplicationService;
import com.yangaobo.expense.backend.application.document.DocumentUploadService;
import com.yangaobo.expense.backend.application.extraction.ExpenseExtractionService;
import com.yangaobo.expense.backend.domain.model.ExpenseDocument;
import com.yangaobo.expense.common.domain.ExpenseCaseStatus;
import com.yangaobo.expense.common.error.MyExpenseAgentErrorCode;
import com.yangaobo.expense.common.error.MyExpenseAgentException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class MoreInfoApplicationService {
    private final MoreInfoTaskRepository repository;
    private final WorkflowRunRepository runRepository;
    private final ExpenseCaseApplicationService caseService;
    private final DocumentUploadService uploadService;
    private final ExpenseExtractionService extractionService;
    private final ExpenseCoordinator coordinator;
    private final Clock clock;

    public MoreInfoApplicationService(
            MoreInfoTaskRepository repository,
            WorkflowRunRepository runRepository,
            ExpenseCaseApplicationService caseService,
            DocumentUploadService uploadService,
            ExpenseExtractionService extractionService,
            ExpenseCoordinator coordinator,
            Clock clock) {
        this.repository = repository;
        this.runRepository = runRepository;
        this.caseService = caseService;
        this.uploadService = uploadService;
        this.extractionService = extractionService;
        this.coordinator = coordinator;
        this.clock = clock;
    }

    public MoreInfoTaskRepository.MoreInfoTask request(
            UUID caseId,
            List<String> requiredMaterials,
            List<String> reasonCodes,
            String requestedBy) {
        var expenseCase = caseService.getById(caseId);
        WorkflowRunRepository.WorkflowRunDetail run = runRepository.latestRun(caseId)
                .orElseThrow(() -> validation("案例尚无可关联的审核 Run"));
        if (expenseCase.status() == ExpenseCaseStatus.WAITING_HUMAN) {
            caseService.transition(caseId, ExpenseCaseStatus.WAITING_MORE_INFO);
        } else if (expenseCase.status() != ExpenseCaseStatus.WAITING_MORE_INFO) {
            throw validation("只有等待人工复核的案例可以请求补材料");
        }
        return repository.create(
                caseId, run.id(), required(requiredMaterials), safe(reasonCodes), requestedBy,
                clock.instant().plus(Duration.ofHours(48)), clock.instant());
    }

    public Optional<MoreInfoTaskRepository.MoreInfoTask> current(UUID caseId) {
        return repository.findOpenByCaseId(caseId);
    }

    public MoreInfoSubmissionResult submit(
            UUID caseId,
            UUID taskId,
            MultipartFile file,
            String requestId,
            String category,
            LocalDate expenseDate,
            String ownerSubject,
            String reopenReason) {
        MoreInfoTaskRepository.MoreInfoTask task = repository.findById(taskId)
                .filter(item -> item.caseId().equals(caseId) && "OPEN".equals(item.status()))
                .orElseThrow(() -> validation("补材料任务不存在、已提交或不属于当前案例"));
        caseService.getOwned(caseId, ownerSubject);
        ExpenseDocument document = uploadService.upload(caseId, ownerSubject, file);
        extractionService.extract(caseId, ownerSubject);
        int version = runRepository.currentDocumentVersion(caseId);
        repository.markSubmitted(taskId, version, clock.instant());
        ExpenseWorkflowResult result = coordinator.analyze(
                caseId,
                ownerSubject,
                new ExpenseWorkflowCommand(
                        requestId, category, expenseDate, WorkflowCommandType.REVIEW_AGAIN,
                        version, task.runId(),
                        reopenReason == null || reopenReason.isBlank()
                                ? "补充材料后重新审核" : reopenReason));
        repository.complete(taskId, clock.instant());
        return new MoreInfoSubmissionResult(taskId, document.id(), version, result);
    }

    private static List<String> required(List<String> values) {
        List<String> result = safe(values);
        if (result.isEmpty()) throw validation("requiredMaterials不能为空");
        return result;
    }

    private static List<String> safe(List<String> values) {
        return values == null ? List.of() : values.stream()
                .filter(value -> value != null && !value.isBlank()).map(String::trim).distinct().toList();
    }

    private static MyExpenseAgentException validation(String message) {
        return new MyExpenseAgentException(MyExpenseAgentErrorCode.VALIDATION_FAILED, message);
    }

    public record MoreInfoSubmissionResult(
            UUID taskId, UUID documentId, int documentVersion, ExpenseWorkflowResult workflow) {}
}

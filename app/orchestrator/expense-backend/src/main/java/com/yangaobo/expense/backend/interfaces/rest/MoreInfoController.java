package com.yangaobo.expense.backend.interfaces.rest;

import com.yangaobo.expense.backend.application.ExpenseCaseApplicationService;
import com.yangaobo.expense.backend.application.workflow.MoreInfoApplicationService;
import com.yangaobo.expense.backend.application.workflow.MoreInfoTaskRepository;
import com.yangaobo.expense.backend.application.workflow.ExpenseCoordinator;
import com.yangaobo.expense.backend.application.workflow.ExpenseWorkflowCommand;
import com.yangaobo.expense.backend.application.workflow.WorkflowCommandType;
import com.yangaobo.expense.backend.application.workflow.WorkflowRunRepository;
import jakarta.validation.Valid;
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/expense-cases/{caseId}")
public class MoreInfoController {
    private final MoreInfoApplicationService service;
    private final WorkflowRunRepository runRepository;
    private final ExpenseCaseApplicationService caseService;
    private final ExpenseCoordinator coordinator;

    public MoreInfoController(
            MoreInfoApplicationService service,
            WorkflowRunRepository runRepository,
            ExpenseCaseApplicationService caseService,
            ExpenseCoordinator coordinator) {
        this.service = service;
        this.runRepository = runRepository;
        this.caseService = caseService;
        this.coordinator = coordinator;
    }

    @PostMapping("/more-info-requests")
    public MoreInfoTaskRepository.MoreInfoTask request(
            @PathVariable UUID caseId,
            @Valid @RequestBody MoreInfoRequest request,
            Principal principal) {
        return service.request(caseId, request.requiredMaterials(), request.reasonCodes(), principal.getName());
    }

    @PostMapping(value = "/more-info-submissions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public MoreInfoApplicationService.MoreInfoSubmissionResult submit(
            @PathVariable UUID caseId,
            @RequestPart("file") MultipartFile file,
            @RequestParam UUID taskId,
            @RequestParam String requestId,
            @RequestParam String category,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expenseDate,
            @RequestParam(required = false) String reopenReason,
            Principal principal) {
        return service.submit(caseId, taskId, file, requestId, category, expenseDate,
                principal.getName(), reopenReason);
    }

    @GetMapping("/more-info-request")
    public ResponseEntity<MoreInfoTaskRepository.MoreInfoTask> currentRequest(
            @PathVariable UUID caseId, Principal principal) {
        requireCaseAccess(caseId, principal);
        return ResponseEntity.of(service.current(caseId));
    }

    @PostMapping("/review-runs")
    public ExpenseWorkflowResponse reviewAgain(
            @PathVariable UUID caseId,
            @Valid @RequestBody ReviewRunRequest request,
            Principal principal) {
        return ExpenseWorkflowResponse.from(coordinator.analyze(
                caseId,
                principal.getName(),
                new ExpenseWorkflowCommand(
                        request.requestId(), request.category(), request.expenseDate(),
                        WorkflowCommandType.REVIEW_AGAIN, request.documentVersion(),
                        request.previousRunId(), request.reopenReason())));
    }

    @GetMapping("/review-runs")
    public List<WorkflowRunRepository.WorkflowRunDetail> runs(
            @PathVariable UUID caseId, Principal principal) {
        requireCaseAccess(caseId, principal);
        return runRepository.findByCaseId(caseId);
    }

    @GetMapping("/document-versions")
    public List<WorkflowRunRepository.DocumentVersion> documentVersions(
            @PathVariable UUID caseId, Principal principal) {
        requireCaseAccess(caseId, principal);
        return runRepository.documentVersions(caseId);
    }

    private void requireCaseAccess(UUID caseId, Principal principal) {
        if (privileged(principal)) {
            caseService.getById(caseId);
        } else {
            caseService.getOwned(caseId, principal.getName());
        }
    }

    private static boolean privileged(Principal principal) {
        return principal instanceof Authentication authentication
                && authentication.getAuthorities().stream()
                        .map(org.springframework.security.core.GrantedAuthority::getAuthority)
                        .anyMatch(
                                authority ->
                                        "ROLE_COLLEGE_REVIEWER".equals(authority)
                                                || "ROLE_FINANCE_ADMIN".equals(authority)
                                                || "ROLE_AUDITOR".equals(authority));
    }
}

package com.yangaobo.expense.backend.interfaces.rest;

import com.yangaobo.expense.backend.application.CreateExpenseCaseCommand;
import com.yangaobo.expense.backend.application.ExpenseCaseApplicationService;
import com.yangaobo.expense.backend.application.UpdateExpenseCaseCommand;
import com.yangaobo.expense.backend.application.document.DocumentUploadService;
import com.yangaobo.expense.backend.application.document.DocumentQueryService;
import com.yangaobo.expense.backend.application.extraction.ExpenseExtractionService;
import com.yangaobo.expense.backend.application.settlement.ToolCallRepository;
import com.yangaobo.expense.backend.application.workflow.ExpenseCoordinator;
import com.yangaobo.expense.backend.application.workflow.ExpenseWorkflowCommand;
import com.yangaobo.expense.common.error.CampusFundFlowErrorCode;
import com.yangaobo.expense.common.error.CampusFundFlowException;
import jakarta.validation.Valid;
import java.net.URI;
import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.yangaobo.expense.common.domain.ExpenseCaseStatus;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/fund-applications")
public class ExpenseCaseController {

    private final ExpenseCaseApplicationService applicationService;
    private final DocumentUploadService documentUploadService;
    private final DocumentQueryService documentQueryService;
    private final ExpenseExtractionService extractionService;
    private final ExpenseCoordinator coordinator;
    private final ToolCallRepository toolCallRepository;

    public ExpenseCaseController(
            ExpenseCaseApplicationService applicationService,
            DocumentUploadService documentUploadService,
            DocumentQueryService documentQueryService,
            ExpenseExtractionService extractionService,
            ExpenseCoordinator coordinator,
            ToolCallRepository toolCallRepository) {
        this.applicationService = applicationService;
        this.documentUploadService = documentUploadService;
        this.documentQueryService = documentQueryService;
        this.extractionService = extractionService;
        this.coordinator = coordinator;
        this.toolCallRepository = toolCallRepository;
    }

    @PostMapping
    public ResponseEntity<ExpenseCaseResponse> create(
            @Valid @RequestBody CreateExpenseCaseRequest request, Principal principal) {
        String subject = authenticatedSubject(principal);
        var created =
                applicationService.create(
                        new CreateExpenseCaseCommand(
                                subject,
                                request.applicantName(),
                                request.projectCode(),
                                request.title(),
                                request.claimedAmount(),
                                request.currency()));
        return ResponseEntity.created(URI.create("/api/v1/fund-applications/" + created.id()))
                .body(ExpenseCaseResponse.from(created));
    }

    @GetMapping("/{caseId}")
    public ExpenseCaseResponse get(@PathVariable UUID caseId, Principal principal) {
        var expenseCase =
                privileged(principal)
                        ? applicationService.getById(caseId)
                        : applicationService.getOwned(caseId, authenticatedSubject(principal));
        return ExpenseCaseResponse.from(expenseCase, settlementStatus(expenseCase.id()));
    }

    @PutMapping("/{caseId}")
    public ExpenseCaseResponse updateDraft(
            @PathVariable UUID caseId,
            @Valid @RequestBody UpdateExpenseCaseRequest request,
            Principal principal) {
        String subject = authenticatedSubject(principal);
        return ExpenseCaseResponse.from(
                applicationService.updateDraft(
                        caseId,
                        new UpdateExpenseCaseCommand(
                                subject,
                                request.applicantName(),
                                request.projectCode(),
                                request.title(),
                                request.claimedAmount(),
                                request.currency())));
    }

    @DeleteMapping("/{caseId}")
    public ResponseEntity<Void> deleteDraft(@PathVariable UUID caseId, Principal principal) {
        if (financeAdmin(principal)) {
            applicationService.deleteAny(caseId);
        } else {
            applicationService.deleteDraft(caseId, authenticatedSubject(principal));
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ExpenseCasePageResponse search(
            @RequestParam(required = false) ExpenseCaseStatus status,
            @RequestParam(required = false) String riskLevel,
            @RequestParam(required = false) String applicant,
            @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    Instant createdFrom,
            @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    Instant createdTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Principal principal) {
        var result =
                applicationService.search(
                        new ExpenseCaseApplicationService.ExpenseCaseQuery(
                                authenticatedSubject(principal),
                                privileged(principal),
                                status,
                                riskLevel,
                                applicant,
                                createdFrom,
                                createdTo,
                                page,
                                size));
        List<ExpenseCaseResponse> items =
                result.items().stream()
                        .map(item -> ExpenseCaseResponse.from(item, settlementStatus(item.id())))
                        .toList();
        return new ExpenseCasePageResponse(items, result.page(), result.size(), result.total());
    }

    private String settlementStatus(UUID caseId) {
        var calls =
                toolCallRepository.findByCaseId(caseId).stream()
                        .filter(call ->
                                java.util.Set.of(
                                                "debit_project_budget",
                                                "submit_fund_reimbursement",
                                                "submit_fund_posting",
                                                "record_fund_reimbursement_history")
                                        .contains(call.toolName()))
                        .toList();
        boolean requiredWritesSucceeded =
                java.util.Set.of(
                                "debit_project_budget",
                                "submit_fund_reimbursement",
                                "submit_fund_posting",
                                "record_fund_reimbursement_history")
                        .stream()
                        .allMatch(
                                toolName ->
                                        calls.stream()
                                                .anyMatch(
                                                        call ->
                                                                toolName.equals(call.toolName())
                                                                        && "SUCCEEDED"
                                                                                .equals(
                                                                                        call.status())));
        if (requiredWritesSucceeded) {
            return "SUBMITTED";
        }
        if (calls.stream().anyMatch(call -> "FAILED".equals(call.status()))) {
            return "FAILED";
        }
        return "NOT_SUBMITTED";
    }

    @PostMapping(value = "/{caseId}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ExpenseDocumentResponse> uploadDocument(
            @PathVariable UUID caseId,
            @RequestPart("file") MultipartFile file,
            Principal principal) {
        var document =
                documentUploadService.upload(caseId, authenticatedSubject(principal), file);
        return ResponseEntity.created(
                        URI.create(
                                "/api/v1/fund-applications/%s/documents/%s"
                                        .formatted(caseId, document.id())))
                .body(ExpenseDocumentResponse.from(document));
    }

    @GetMapping("/{caseId}/documents")
    public List<ExpenseDocumentDetailResponse> documents(
            @PathVariable UUID caseId, Principal principal) {
        return documentQueryService
                .list(
                        caseId,
                        authenticatedSubject(principal),
                        privileged(principal))
                .stream()
                .map(ExpenseDocumentDetailResponse::from)
                .toList();
    }

    @PostMapping("/{caseId}/analyze")
    public ExtractionResponse analyze(@PathVariable UUID caseId, Principal principal) {
        return ExtractionResponse.from(
                extractionService.extract(caseId, authenticatedSubject(principal)));
    }

    @PostMapping("/{caseId}/workflow")
    public ExpenseWorkflowResponse runWorkflow(
            @PathVariable UUID caseId,
            @Valid @RequestBody ExpenseWorkflowRequest request,
            Principal principal) {
        return ExpenseWorkflowResponse.from(
                coordinator.analyze(
                        caseId,
                        authenticatedSubject(principal),
                        new ExpenseWorkflowCommand(
                                request.requestId(),
                                request.category(),
                                request.expenseDate())));
    }

    private static String authenticatedSubject(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new CampusFundFlowException(
                    CampusFundFlowErrorCode.ACCESS_DENIED, "Authentication is required");
        }
        return principal.getName();
    }

    private static boolean privileged(Principal principal) {
        if (!(principal instanceof Authentication authentication)) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .map(org.springframework.security.core.GrantedAuthority::getAuthority)
                .anyMatch(
                        authority ->
                                "ROLE_COLLEGE_REVIEWER".equals(authority)
                                        || "ROLE_FINANCE_ADMIN".equals(authority)
                                        || "ROLE_AUDITOR".equals(authority));
    }

    private static boolean financeAdmin(Principal principal) {
        if (!(principal instanceof Authentication authentication)) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .map(org.springframework.security.core.GrantedAuthority::getAuthority)
                .anyMatch("ROLE_FINANCE_ADMIN"::equals);
    }
}

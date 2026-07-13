package com.yangaobo.expense.backend.interfaces.rest;

import com.yangaobo.expense.backend.application.ExpenseCaseApplicationService;
import com.yangaobo.expense.backend.application.observability.CaseAuditRepository;
import com.yangaobo.expense.backend.application.observability.ModelCallRepository;
import com.yangaobo.expense.backend.application.workflow.WorkflowRunRepository;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.security.Principal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/observability")
public class ObservabilityController {

    private final WorkflowRunRepository runRepository;
    private final ModelCallRepository modelCallRepository;
    private final CaseAuditRepository auditRepository;
    private final ExpenseCaseApplicationService caseService;

    public ObservabilityController(
            WorkflowRunRepository runRepository,
            ModelCallRepository modelCallRepository,
            CaseAuditRepository auditRepository,
            ExpenseCaseApplicationService caseService) {
        this.runRepository = runRepository;
        this.modelCallRepository = modelCallRepository;
        this.auditRepository = auditRepository;
        this.caseService = caseService;
    }

    @GetMapping("/runs")
    public List<ObservableRunResponse> recentRuns(
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return runRepository.recentRuns(limit).stream()
                .map(run -> ObservableRunResponse.from(run, runRepository.steps(run.id())))
                .toList();
    }

    @GetMapping("/model-calls")
    public List<ModelCallRepository.ModelCallRecord> modelCalls(
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit) {
        return modelCallRepository.recent(limit);
    }

    @GetMapping("/model-summary")
    public ModelCallRepository.ModelCallSummary modelSummary() {
        return modelCallRepository.summary();
    }

    @GetMapping("/fund-applications/{caseId}")
    public CaseObservabilityResponse caseObservability(
            @PathVariable UUID caseId,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit,
            Principal principal) {
        if (privileged(principal)) {
            caseService.getById(caseId);
        } else {
            caseService.getOwned(caseId, principal.getName());
        }
        WorkflowRunRepository.WorkflowRunDetail run = runRepository.latestRun(caseId).orElse(null);
        List<WorkflowRunRepository.WorkflowStep> steps =
                run == null ? List.of() : runRepository.steps(run.id());
        List<ModelCallRepository.ModelCallRecord> modelCalls =
                modelCallRepository.findByCaseId(caseId, limit);
        long failedSteps = steps.stream().filter(step -> "FAILED".equals(step.status())).count();
        return new CaseObservabilityResponse(
                run == null ? null : ObservableRunResponse.from(run, steps),
                steps,
                modelCalls,
                auditRepository.findByCaseId(caseId, limit),
                modelCalls.size(),
                modelCalls.stream().mapToLong(ModelCallRepository.ModelCallRecord::totalTokens).sum(),
                failedSteps);
    }

    public record CaseObservabilityResponse(
            ObservableRunResponse latestRun,
            List<WorkflowRunRepository.WorkflowStep> steps,
            List<ModelCallRepository.ModelCallRecord> modelCalls,
            List<CaseAuditRepository.AuditEvent> auditEvents,
            int modelCallCount,
            long totalTokens,
            long failedStepCount) {}

    public record ObservableRunResponse(
            UUID runId,
            UUID caseId,
            String requestId,
            String status,
            Instant startedAt,
            Instant completedAt,
            String errorCode,
            String traceId,
            int stepCount,
            int succeededStepCount,
            int failedStepCount,
            Long durationMs,
            boolean agentPlanRecorded) {

        static ObservableRunResponse from(
                WorkflowRunRepository.WorkflowRunDetail run,
                List<WorkflowRunRepository.WorkflowStep> steps) {
            int succeededSteps =
                    (int) steps.stream().filter(step -> "SUCCEEDED".equals(step.status())).count();
            int failedSteps =
                    (int) steps.stream().filter(step -> "FAILED".equals(step.status())).count();
            boolean agentPlanRecorded =
                    steps.stream()
                            .anyMatch(
                                    step ->
                                            "AGENT_PLAN".equals(step.name())
                                                    && "SUCCEEDED".equals(step.status()));
            return new ObservableRunResponse(
                    run.id(),
                    run.caseId(),
                    run.requestId(),
                    run.status(),
                    run.startedAt(),
                    run.completedAt(),
                    run.errorCode(),
                    run.traceId(),
                    steps.size(),
                    succeededSteps,
                    failedSteps,
                    durationMs(run.startedAt(), run.completedAt()),
                    agentPlanRecorded);
        }

        private static Long durationMs(Instant startedAt, Instant completedAt) {
            if (startedAt == null || completedAt == null) {
                return null;
            }
            return Math.max(0, Duration.between(startedAt, completedAt).toMillis());
        }
    }

    private static boolean privileged(Principal principal) {
        return principal instanceof Authentication authentication
                && authentication.getAuthorities().stream()
                        .map(
                                org.springframework.security.core.GrantedAuthority
                                        ::getAuthority)
                        .anyMatch(
                                authority ->
                                        "ROLE_COLLEGE_REVIEWER".equals(authority)
                                                || "ROLE_FINANCE_ADMIN".equals(authority)
                                                || "ROLE_AUDITOR".equals(authority));
    }
}

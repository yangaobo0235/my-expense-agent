package com.yangaobo.expense.backend.application.workflow;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangaobo.expense.agents.ExpenseMultiAgentPlanner;
import com.yangaobo.expense.backend.application.ExpenseCaseApplicationService;
import com.yangaobo.expense.backend.application.extraction.ExtractedExpenseDocument;
import com.yangaobo.expense.backend.application.observability.WorkflowObservability;
import com.yangaobo.expense.backend.application.policy.PolicyRetrievalService;
import com.yangaobo.expense.backend.application.policy.PolicySearchQuery;
import com.yangaobo.expense.backend.domain.model.ExpenseCase;
import com.yangaobo.expense.backend.domain.model.ExpenseDocument;
import com.yangaobo.expense.backend.domain.model.RiskLevel;
import com.yangaobo.expense.backend.domain.repository.ExpenseDocumentRepository;
import com.yangaobo.expense.backend.domain.repository.PolicySearchMatch;
import com.yangaobo.expense.backend.domain.risk.DeterministicRiskEngine;
import com.yangaobo.expense.backend.domain.risk.RiskAssessment;
import com.yangaobo.expense.backend.domain.risk.RiskAssessmentInput;
import com.yangaobo.expense.backend.domain.risk.RiskSignal;
import com.yangaobo.expense.backend.domain.risk.RiskSignalCode;
import com.yangaobo.expense.common.domain.ExpenseCaseStatus;
import com.yangaobo.expense.common.error.MyExpenseAgentErrorCode;
import com.yangaobo.expense.common.error.MyExpenseAgentException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.springframework.stereotype.Component;

@Component
class ExpenseWorkflowSteps {

    private static final String APPLICANT_CONTEXT_STEP = "MCP_APPLICANT_CONTEXT";
    private static final String AGENT_PLAN_STEP = ExpenseWorkflowNodeNames.AGENT_PLAN;
    private static final String DUPLICATE_CHECK_STEP = "MCP_DUPLICATE_CHECK";
    private static final String PROJECT_BUDGET_STEP = "MCP_PROJECT_BUDGET";
    private static final String REIMBURSEMENT_HISTORY_STEP = "MCP_REIMBURSEMENT_HISTORY";
    private static final String EVIDENCE_STEP = "MCP_REVIEW_EVIDENCE";
    private static final String PARALLEL_EVIDENCE_STEP =
            ExpenseWorkflowNodeNames.PARALLEL_EVIDENCE_COLLECTION;
    private static final String POLICY_STEP = "POLICY_RETRIEVAL";
    private static final String RISK_STEP = ExpenseWorkflowNodeNames.RISK_ASSESSMENT;
    private static final String ROUTING_STEP = ExpenseWorkflowNodeNames.RISK_ROUTING;
    private static final String FINALIZE_STEP = ExpenseWorkflowNodeNames.FINALIZE;

    private final ExpenseCaseApplicationService caseService;
    private final ExpenseDocumentRepository documentRepository;
    private final PolicyRetrievalService policyService;
    private final ExpenseContextGateway contextGateway;
    private final WorkflowEvidenceGateway evidenceGateway;
    private final DeterministicRiskEngine riskEngine;
    private final WorkflowRunRepository runRepository;
    private final ReviewRepository reviewRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final WorkflowObservability observability;
    private final ExpenseMultiAgentPlanner agentPlanner = new ExpenseMultiAgentPlanner();

    ExpenseWorkflowSteps(
            ExpenseCaseApplicationService caseService,
            ExpenseDocumentRepository documentRepository,
            PolicyRetrievalService policyService,
            ExpenseContextGateway contextGateway,
            WorkflowEvidenceGateway evidenceGateway,
            DeterministicRiskEngine riskEngine,
            WorkflowRunRepository runRepository,
            ReviewRepository reviewRepository,
            ObjectMapper objectMapper,
            Clock clock,
            WorkflowObservability observability) {
        this.caseService = caseService;
        this.documentRepository = documentRepository;
        this.policyService = policyService;
        this.contextGateway = contextGateway;
        this.evidenceGateway = evidenceGateway;
        this.riskEngine = riskEngine;
        this.runRepository = runRepository;
        this.reviewRepository = reviewRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.observability = observability;
    }

    Map<String, Object> restoreResultNode(ExpenseWorkflowGraphState state) {
        ExpenseWorkflowResult result = restoreResult(state.expenseCase(), state.runId());
        return Map.of(ExpenseWorkflowGraphState.WORKFLOW_RESULT, result);
    }

    Map<String, Object> loadExtractedNode(ExpenseWorkflowGraphState state) {
        return Map.of(
                ExpenseWorkflowGraphState.EXTRACTED_DOCUMENTS,
                loadExtracted(state.caseId()));
    }

    Map<String, Object> agentPlanNode(ExpenseWorkflowGraphState state) {
        Map<String, Object> output =
                observability.step(
                        AGENT_PLAN_STEP,
                        state.caseId(),
                        state.runId(),
                        () -> agentPlanStep(state.runId(), state.caseId(), state.requestId()));
        return Map.of(ExpenseWorkflowGraphState.AGENT_PLAN, output.get("plan"));
    }

    Map<String, Object> parallelEvidenceNode(ExpenseWorkflowGraphState state) {
        ParallelEvidence evidence =
                observability.step(
                        PARALLEL_EVIDENCE_STEP,
                        state.caseId(),
                        state.runId(),
                        () ->
                                parallelEvidenceStep(
                                        state.runId(),
                                        state.caseId(),
                                        state.ownerSubject(),
                                        state.requestId(),
                                        state.expenseCase(),
                                        state.command(),
                                        state.extractedDocuments()));
        return Map.of(
                ExpenseWorkflowGraphState.APPLICANT_CONTEXT,
                evidence.applicantContext(),
                ExpenseWorkflowGraphState.DUPLICATE_CHECK,
                evidence.duplicateCheck(),
                ExpenseWorkflowGraphState.PROJECT_BUDGET,
                evidence.projectBudget(),
                ExpenseWorkflowGraphState.REIMBURSEMENT_HISTORY,
                evidence.reimbursementHistory(),
                ExpenseWorkflowGraphState.EVIDENCE_RESULT,
                evidence.evidenceResult(),
                ExpenseWorkflowGraphState.POLICY_FINDINGS,
                evidence.policyFindings());
    }

    Map<String, Object> riskAssessmentNode(ExpenseWorkflowGraphState state) {
        boolean dependencyFailure =
                state.applicantContext().dependencyFailure()
                        || state.duplicateCheck().dependencyFailure()
                        || state.projectBudget().dependencyFailure()
                        || state.reimbursementHistory().dependencyFailure()
                        || "MCP_FAILURE".equals(state.evidenceResult().source());
        RiskAssessment risk =
                observability.step(
                        RISK_STEP,
                        state.caseId(),
                        state.runId(),
                        () ->
                                riskStep(
                                        state.runId(),
                                        state.caseId(),
                                        state.expenseCase(),
                                        state.extractedDocuments(),
                                        state.command(),
                                        state.duplicateCheck(),
                                        state.projectBudget(),
                                        state.policyFindings(),
                                        dependencyFailure));
        return Map.of(ExpenseWorkflowGraphState.RISK_ASSESSMENT, risk);
    }

    Map<String, Object> riskRoutingNode(ExpenseWorkflowGraphState state) {
        RiskRoutingDecision routing =
                observability.step(
                        ROUTING_STEP,
                        state.caseId(),
                        state.runId(),
                        () -> routingStep(state.runId(), state.caseId(), state.riskAssessment()));
        return Map.of(ExpenseWorkflowGraphState.ROUTING_DECISION, routing);
    }

    Map<String, Object> finalizeNode(ExpenseWorkflowGraphState state) {
        ExpenseWorkflowResult result =
                observability.step(
                        FINALIZE_STEP,
                        state.caseId(),
                        state.runId(),
                        () ->
                                finalizeStep(
                                        state.runId(),
                                        state.expenseCase(),
                                        state.riskAssessment(),
                                        state.routingDecision(),
                                        state.policyFindings(),
                                        state.ownerSubject(),
                                        state.requestId()));
        return Map.of(ExpenseWorkflowGraphState.WORKFLOW_RESULT, result);
    }

    boolean hasRecoverableFailedEvidenceSteps(UUID runId) {
        return runRepository.steps(runId).stream()
                .anyMatch(
                        step ->
                                "FAILED".equals(step.status())
                                        && java.util.Set.of(
                                                        APPLICANT_CONTEXT_STEP,
                                                        DUPLICATE_CHECK_STEP,
                                                        PROJECT_BUDGET_STEP,
                                                        REIMBURSEMENT_HISTORY_STEP,
                                                        EVIDENCE_STEP,
                                                        PARALLEL_EVIDENCE_STEP)
                                                .contains(step.name()));
    }

    private Map<String, Object> agentPlanStep(UUID runId, UUID caseId, String requestId) {
        var stored = runRepository.successfulStep(runId, AGENT_PLAN_STEP);
        if (stored.isPresent()) {
            return stored.get();
        }
        runRepository.startStep(runId, caseId, AGENT_PLAN_STEP, 1, hash(caseId + "|" + requestId));
        try {
            Map<String, Object> plan = agentPlanner.plan(caseId.toString(), requestId).toEvidence();
            runRepository.succeedStep(runId, AGENT_PLAN_STEP, 1, Map.of("plan", plan));
            return Map.of("plan", plan);
        } catch (RuntimeException exception) {
            runRepository.failStep(
                    runId, AGENT_PLAN_STEP, 1, errorCode(exception), safeMessage(exception));
            throw exception;
        }
    }

    private ExpenseContextGateway.ApplicantContext applicantContextStep(
            UUID runId,
            UUID caseId,
            ExpenseCase expenseCase) {
        var stored = runRepository.successfulStep(runId, APPLICANT_CONTEXT_STEP);
        if (stored.isPresent()) {
            ExpenseContextGateway.ApplicantContext context =
                    objectMapper.convertValue(
                            stored.get().get("context"),
                            ExpenseContextGateway.ApplicantContext.class);
            if (!context.dependencyFailure()) {
                return context;
            }
        }
        runRepository.startStep(
                runId,
                caseId,
                APPLICANT_CONTEXT_STEP,
                1,
                hash(expenseCase.ownerSubject() + "|" + expenseCase.projectCode()));
        try {
            ExpenseContextGateway.ApplicantContext context =
                    contextGateway.applicantContext(
                            expenseCase.ownerSubject(),
                            expenseCase.projectCode());
            runRepository.succeedStep(runId, APPLICANT_CONTEXT_STEP, 1, Map.of("context", context));
            return context;
        } catch (RuntimeException exception) {
            runRepository.failStep(
                    runId,
                    APPLICANT_CONTEXT_STEP,
                    1,
                    errorCode(exception),
                    safeMessage(exception));
            return new ExpenseContextGateway.ApplicantContext(
                    expenseCase.ownerSubject(),
                    expenseCase.projectCode(),
                    "UNKNOWN",
                    "CN",
                    List.of(),
                    "MCP_FAILURE",
                    true,
                    safeMessage(exception));
        }
    }

    private ExpenseContextGateway.DuplicateCheck duplicateCheckStep(
            UUID runId, UUID caseId) {
        var stored = runRepository.successfulStep(runId, DUPLICATE_CHECK_STEP);
        if (stored.isPresent()) {
            ExpenseContextGateway.DuplicateCheck check =
                    objectMapper.convertValue(
                            stored.get().get("check"),
                            ExpenseContextGateway.DuplicateCheck.class);
            if (!check.dependencyFailure()) {
                return check;
            }
        }
        List<String> hashes =
                documentRepository.findByCaseId(caseId).stream()
                        .map(ExpenseDocument::sha256)
                        .distinct()
                        .sorted()
                        .toList();
        runRepository.startStep(
                runId,
                caseId,
                DUPLICATE_CHECK_STEP,
                1,
                hash(Map.of("sha256", hashes)));
        try {
            ExpenseContextGateway.DuplicateCheck check =
                    contextGateway.duplicateCheck(caseId, hashes);
            runRepository.succeedStep(runId, DUPLICATE_CHECK_STEP, 1, Map.of("check", check));
            return check;
        } catch (RuntimeException exception) {
            runRepository.failStep(
                    runId,
                    DUPLICATE_CHECK_STEP,
                    1,
                    errorCode(exception),
                    safeMessage(exception));
            return new ExpenseContextGateway.DuplicateCheck(
                    false,
                    List.of(),
                    Map.of("failure", safeMessage(exception)),
                    "MCP_FAILURE",
                    true,
                    safeMessage(exception));
        }
    }

    private ExpenseContextGateway.ProjectBudget projectBudgetStep(
            UUID runId, UUID caseId, ExpenseCase expenseCase) {
        var stored = runRepository.successfulStep(runId, PROJECT_BUDGET_STEP);
        if (stored.isPresent()) {
            ExpenseContextGateway.ProjectBudget budget =
                    objectMapper.convertValue(
                            stored.get().get("budget"),
                            ExpenseContextGateway.ProjectBudget.class);
            if (!budget.dependencyFailure()) {
                return budget;
            }
        }
        runRepository.startStep(
                runId,
                caseId,
                PROJECT_BUDGET_STEP,
                1,
                hash(expenseCase.ownerSubject() + "|" + expenseCase.projectCode()));
        try {
            ExpenseContextGateway.ProjectBudget budget =
                    contextGateway.projectBudget(
                            expenseCase.ownerSubject(), expenseCase.projectCode());
            runRepository.succeedStep(runId, PROJECT_BUDGET_STEP, 1, Map.of("budget", budget));
            return budget;
        } catch (RuntimeException exception) {
            runRepository.failStep(
                    runId,
                    PROJECT_BUDGET_STEP,
                    1,
                    errorCode(exception),
                    safeMessage(exception));
            return new ExpenseContextGateway.ProjectBudget(
                    expenseCase.ownerSubject(),
                    expenseCase.projectCode(),
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    expenseCase.claimedAmount().currency(),
                    0,
                    java.time.Instant.EPOCH,
                    "MCP_FAILURE",
                    true,
                    safeMessage(exception));
        }
    }

    private ExpenseContextGateway.ReimbursementHistory reimbursementHistoryStep(
            UUID runId, UUID caseId, ExpenseCase expenseCase) {
        var stored = runRepository.successfulStep(runId, REIMBURSEMENT_HISTORY_STEP);
        if (stored.isPresent()) {
            ExpenseContextGateway.ReimbursementHistory history =
                    objectMapper.convertValue(
                            stored.get().get("history"),
                            ExpenseContextGateway.ReimbursementHistory.class);
            if (!history.dependencyFailure()) {
                return history;
            }
        }
        runRepository.startStep(
                runId,
                caseId,
                REIMBURSEMENT_HISTORY_STEP,
                1,
                hash(expenseCase.ownerSubject()));
        try {
            ExpenseContextGateway.ReimbursementHistory history =
                    contextGateway.reimbursementHistory(expenseCase.ownerSubject());
            runRepository.succeedStep(
                    runId, REIMBURSEMENT_HISTORY_STEP, 1, Map.of("history", history));
            return history;
        } catch (RuntimeException exception) {
            runRepository.failStep(
                    runId,
                    REIMBURSEMENT_HISTORY_STEP,
                    1,
                    errorCode(exception),
                    safeMessage(exception));
            return new ExpenseContextGateway.ReimbursementHistory(
                    List.of(), "MCP_FAILURE", true, safeMessage(exception));
        }
    }

    private WorkflowEvidenceGateway.EvidenceResult evidenceStep(
            UUID runId,
            UUID caseId,
            String actorSubject,
            String requestId,
            ExpenseContextGateway.DuplicateCheck duplicateCheck) {
        var stored = runRepository.successfulStep(runId, EVIDENCE_STEP);
        if (stored.isPresent()) {
            WorkflowEvidenceGateway.EvidenceResult result =
                    objectMapper.convertValue(
                            stored.get().get("evidence"),
                            WorkflowEvidenceGateway.EvidenceResult.class);
            if (!"MCP_FAILURE".equals(result.source())) {
                return result;
            }
        }
        String contentHash = hash(duplicateCheck);
        runRepository.startStep(runId, caseId, EVIDENCE_STEP, 1, contentHash);
        try {
            WorkflowEvidenceGateway.EvidenceResult result =
                    evidenceGateway.saveDuplicateEvidence(
                            caseId,
                            runId,
                            requestId + ":duplicate-evidence",
                            actorSubject,
                            contentHash);
            runRepository.succeedStep(runId, EVIDENCE_STEP, 1, Map.of("evidence", result));
            return result;
        } catch (RuntimeException exception) {
            runRepository.failStep(
                    runId, EVIDENCE_STEP, 1, errorCode(exception), safeMessage(exception));
            return new WorkflowEvidenceGateway.EvidenceResult(
                    false, safeMessage(exception), "MCP_FAILURE");
        }
    }

    private ParallelEvidence parallelEvidenceStep(
            UUID runId,
            UUID caseId,
            String ownerSubject,
            String requestId,
            ExpenseCase expenseCase,
            ExpenseWorkflowCommand command,
            List<ExtractedExpenseDocument> extractedDocuments) {
        var stored = runRepository.successfulStep(runId, PARALLEL_EVIDENCE_STEP);
        if (stored.isPresent()) {
            if (containsRecoverableEvidenceFailure(stored.get())) {
                runRepository.startStep(runId, caseId, PARALLEL_EVIDENCE_STEP, 1, hash(command));
            } else {
                return new ParallelEvidence(
                        objectMapper.convertValue(
                                stored.get().get("applicantContext"),
                                ExpenseContextGateway.ApplicantContext.class),
                        objectMapper.convertValue(
                                stored.get().get("duplicateCheck"),
                                ExpenseContextGateway.DuplicateCheck.class),
                        objectMapper.convertValue(
                                stored.get().get("projectBudget"),
                                ExpenseContextGateway.ProjectBudget.class),
                        objectMapper.convertValue(
                                stored.get().get("reimbursementHistory"),
                                ExpenseContextGateway.ReimbursementHistory.class),
                        objectMapper.convertValue(
                                stored.get().get("evidence"),
                                WorkflowEvidenceGateway.EvidenceResult.class),
                        castListOfMaps(stored.get().get("policyFindings")));
            }
        }
        runRepository.startStep(runId, caseId, PARALLEL_EVIDENCE_STEP, 1, hash(command));
        try {
            CompletableFuture<ExpenseContextGateway.ApplicantContext> applicantContext =
                    CompletableFuture.supplyAsync(
                            () -> applicantContextStep(runId, caseId, expenseCase));
            CompletableFuture<ContextEvidence> contextEvidence =
                    applicantContext.thenApplyAsync(
                            context -> {
                                ExpenseContextGateway.DuplicateCheck duplicateCheck =
                                        duplicateCheckStep(runId, caseId);
                                ExpenseContextGateway.ProjectBudget projectBudget =
                                        projectBudgetStep(runId, caseId, expenseCase);
                                ExpenseContextGateway.ReimbursementHistory reimbursementHistory =
                                        reimbursementHistoryStep(runId, caseId, expenseCase);
                                WorkflowEvidenceGateway.EvidenceResult evidenceResult =
                                        evidenceStep(
                                                runId,
                                                caseId,
                                                ownerSubject,
                                                requestId,
                                                duplicateCheck);
                                return new ContextEvidence(
                                        context,
                                        duplicateCheck,
                                        projectBudget,
                                        reimbursementHistory,
                                        evidenceResult);
                            });
            CompletableFuture<List<Map<String, Object>>> policyFindings =
                    applicantContext.thenApplyAsync(
                            context ->
                                    policyStep(
                                            runId,
                                            caseId,
                                            command,
                                            context,
                                            extractedDocuments));
            ContextEvidence context = join(contextEvidence);
            List<Map<String, Object>> findings = join(policyFindings);
            ParallelEvidence evidence =
                    new ParallelEvidence(
                            context.applicantContext(),
                            context.duplicateCheck(),
                            context.projectBudget(),
                            context.reimbursementHistory(),
                            context.evidenceResult(),
                            findings);
            runRepository.succeedStep(
                    runId,
                    PARALLEL_EVIDENCE_STEP,
                    1,
                    Map.of(
                            "mode",
                            "PARALLEL",
                            "applicantContext",
                            evidence.applicantContext(),
                            "duplicateCheck",
                            evidence.duplicateCheck(),
                            "projectBudget",
                            evidence.projectBudget(),
                            "reimbursementHistory",
                            evidence.reimbursementHistory(),
                            "evidence",
                            evidence.evidenceResult(),
                            "policyFindings",
                            evidence.policyFindings()));
            return evidence;
        } catch (RuntimeException exception) {
            runRepository.failStep(
                    runId,
                    PARALLEL_EVIDENCE_STEP,
                    1,
                    errorCode(exception),
                    safeMessage(exception));
            throw exception;
        }
    }

    private List<Map<String, Object>> policyStep(
            UUID runId,
            UUID caseId,
            ExpenseWorkflowCommand command,
            ExpenseContextGateway.ApplicantContext applicantContext,
            List<ExtractedExpenseDocument> extractedDocuments) {
        var stored = runRepository.successfulStep(runId, POLICY_STEP);
        if (stored.isPresent()) {
            return castListOfMaps(stored.get().get("findings"));
        }
        runRepository.startStep(runId, caseId, POLICY_STEP, 1, hash(command));
        try {
            caseService.transition(caseId, ExpenseCaseStatus.POLICY_CHECKING);
            List<Map<String, Object>> findings =
                    policyService
                            .search(
                                    new PolicySearchQuery(
                                            command.category(),
                                            command.category(),
                                            applicantContext.region(),
                                            applicantContext.applicantType(),
                                            policyDate(extractedDocuments, command.expenseDate()),
                                            5,
                                            0.25))
                            .stream()
                            .map(ExpenseWorkflowSteps::policyFinding)
                            .toList();
            runRepository.succeedStep(runId, POLICY_STEP, 1, Map.of("findings", findings));
            return findings;
        } catch (RuntimeException exception) {
            runRepository.failStep(
                    runId, POLICY_STEP, 1, errorCode(exception), safeMessage(exception));
            throw exception;
        }
    }

    private static LocalDate policyDate(
            List<ExtractedExpenseDocument> documents, LocalDate fallback) {
        return documents.stream()
                .map(ExtractedExpenseDocument::issueDate)
                .filter(java.util.Objects::nonNull)
                .max(LocalDate::compareTo)
                .orElse(fallback);
    }

    private RiskRoutingDecision routingStep(UUID runId, UUID caseId, RiskAssessment risk) {
        var stored = runRepository.successfulStep(runId, ROUTING_STEP);
        if (stored.isPresent()) {
            return new RiskRoutingDecision(
                    RiskRoutingAction.valueOf(String.valueOf(stored.get().get("action"))),
                    Boolean.TRUE.equals(stored.get().get("requiresHumanReview")),
                    Boolean.TRUE.equals(stored.get().get("debateAssistEnabled")),
                    String.valueOf(stored.get().getOrDefault("queue", "")),
                    String.valueOf(stored.get().getOrDefault("assigneeRole", "")),
                    Integer.parseInt(String.valueOf(stored.get().getOrDefault("slaHours", "48"))),
                    stored.get().get("requiredEvidence") instanceof List<?> evidence
                            ? evidence.stream().map(String::valueOf).toList()
                            : List.of(),
                    String.valueOf(stored.get().getOrDefault("userFacingMessage", "")),
                    String.valueOf(stored.get().getOrDefault("fallbackStrategy", "")),
                    stored.get().get("reasons") instanceof List<?> reasons
                            ? reasons.stream().map(String::valueOf).toList()
                            : List.of());
        }
        runRepository.startStep(runId, caseId, ROUTING_STEP, 1, hash(risk));
        try {
            RiskRoutingDecision routing = RiskRoutingDecision.from(risk);
            runRepository.succeedStep(runId, ROUTING_STEP, 1, routing.toEvidence());
            return routing;
        } catch (RuntimeException exception) {
            runRepository.failStep(
                    runId, ROUTING_STEP, 1, errorCode(exception), safeMessage(exception));
            throw exception;
        }
    }

    private RiskAssessment riskStep(
            UUID runId,
            UUID caseId,
            ExpenseCase expenseCase,
            List<ExtractedExpenseDocument> extracted,
            ExpenseWorkflowCommand command,
            ExpenseContextGateway.DuplicateCheck duplicateCheck,
            ExpenseContextGateway.ProjectBudget projectBudget,
            List<Map<String, Object>> policyFindings,
            boolean dependencyFailure) {
        var stored = runRepository.successfulStep(runId, RISK_STEP);
        if (stored.isPresent()) {
            RiskAssessment assessment =
                    objectMapper.convertValue(
                            stored.get().get("assessment"), RiskAssessment.class);
            boolean containsDependencyFailure =
                    assessment.signals().stream()
                            .anyMatch(
                                    signal ->
                                            signal.code()
                                                    == RiskSignalCode.DEPENDENCY_UNAVAILABLE);
            if (!containsDependencyFailure || dependencyFailure) {
                return assessment;
            }
        }
        runRepository.startStep(runId, caseId, RISK_STEP, 1, hash(command));
        try {
            caseService.transition(caseId, ExpenseCaseStatus.RISK_CHECKING);
            BigDecimal extractedAmount =
                    extracted.stream()
                            .map(ExtractedExpenseDocument::totalAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
            double confidence =
                    extracted.stream()
                            .mapToDouble(ExtractedExpenseDocument::confidence)
                            .min()
                            .orElse(0);
            ComplianceFactDeriver.ComplianceFacts facts =
                    ComplianceFactDeriver.derive(
                            extracted,
                            command.expenseDate(),
                            expenseCase.claimedAmount().amount(),
                            expenseCase.claimedAmount().currency(),
                            projectBudget,
                            policyFindings,
                            LocalDate.now(clock));
            RiskAssessment risk =
                    riskEngine.assess(
                            new RiskAssessmentInput(
                                    expenseCase.claimedAmount().amount(),
                                    extractedAmount,
                                    confidence,
                                    duplicateCheck.duplicate(),
                                    facts.dateAnomaly(),
                                    facts.sellerAnomaly(),
                                    false,
                                    facts.missingRequiredDocument(),
                                    facts.forbiddenExpenseItem(),
                                    facts.projectBudgetExceeded(),
                                    facts.policyEvidenceMissing(),
                                    facts.promptInjectionDetected()));
            if (dependencyFailure) {
                risk = withDependencyFailure(risk);
            }
            caseService.recordRisk(caseId, risk.score());
            runRepository.succeedStep(
                    runId,
                    RISK_STEP,
                    1,
                    Map.of("assessment", risk, "complianceFacts", facts));
            return risk;
        } catch (RuntimeException exception) {
            runRepository.failStep(
                    runId, RISK_STEP, 1, errorCode(exception), safeMessage(exception));
            throw exception;
        }
    }

    private ExpenseWorkflowResult finalizeStep(
            UUID runId,
            ExpenseCase originalCase,
            RiskAssessment risk,
            RiskRoutingDecision routing,
            List<Map<String, Object>> policyFindings,
            String actorSubject,
            String requestId) {
        runRepository.startStep(runId, originalCase.id(), FINALIZE_STEP, 1, hash(risk));
        try {
            UUID reviewTaskId = null;
            ExpenseCaseStatus finalStatus;
            ExpenseCase current = caseService.getById(originalCase.id());
            if (routing.requiresHumanReview()) {
                ExpenseCase waiting =
                        current.status() == ExpenseCaseStatus.WAITING_HUMAN
                                ? current
                                : caseService.transition(
                                        originalCase.id(), ExpenseCaseStatus.WAITING_HUMAN);
                ReviewRepository.ReviewTask task =
                        reviewRepository.createOpenTask(
                                originalCase.id(),
                                risk.signals().stream()
                                        .map(signal -> signal.code().name())
                                        .toList(),
                                routing,
                                clock.instant().plus(Duration.ofHours(routing.slaHours())),
                                clock.instant());
                reviewTaskId = task.id();
                finalStatus = waiting.status();
            } else {
                if (current.status() == ExpenseCaseStatus.FAILED) {
                    current =
                            caseService.transition(
                                    originalCase.id(), ExpenseCaseStatus.RISK_CHECKING);
                }
                ExpenseCase approved =
                        current.status() == ExpenseCaseStatus.APPROVED
                                ? current
                                : caseService.transition(
                                        originalCase.id(), ExpenseCaseStatus.APPROVED);
                reviewRepository.saveDecision(
                        approved,
                        "APPROVED",
                        approved.claimedAmount().amount(),
                        risk,
                        policyFindings,
                        "SYSTEM",
                        requestId,
                        clock.instant());
                finalStatus = approved.status();
            }
            reviewRepository.appendAudit(
                    originalCase.id(),
                    actorSubject,
                    "WORKFLOW_COMPLETED",
                    "EXPENSE_CASE",
                    originalCase.id().toString(),
                    requestId,
                    Map.of(
                            "status",
                            finalStatus.name(),
                            "riskScore",
                            risk.score(),
                            "riskLevel",
                            risk.level().name(),
                            "routingAction",
                            routing.action().name(),
                            "debateAssistEnabled",
                            routing.debateAssistEnabled(),
                            "assigneeRole",
                            routing.assigneeRole(),
                            "slaHours",
                            routing.slaHours(),
                            "fallbackStrategy",
                            routing.fallbackStrategy()),
                    clock.instant());
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("status", finalStatus.name());
            output.put("reviewTaskId", reviewTaskId == null ? "" : reviewTaskId.toString());
            runRepository.succeedStep(runId, FINALIZE_STEP, 1, output);
            runRepository.succeedRun(runId);
            return new ExpenseWorkflowResult(
                    originalCase.id(),
                    runId,
                    finalStatus,
                    risk.score(),
                    risk.level(),
                    risk.signals(),
                    policyFindings,
                    reviewTaskId);
        } catch (RuntimeException exception) {
            runRepository.failStep(
                    runId, FINALIZE_STEP, 1, errorCode(exception), safeMessage(exception));
            throw exception;
        }
    }

    private ExpenseWorkflowResult restoreResult(ExpenseCase expenseCase, UUID runId) {
        RiskAssessment risk =
                runRepository
                        .successfulStep(runId, RISK_STEP)
                        .map(
                                output ->
                                        objectMapper.convertValue(
                                                output.get("assessment"), RiskAssessment.class))
                        .orElseThrow();
        List<Map<String, Object>> policies =
                runRepository
                        .successfulStep(runId, PARALLEL_EVIDENCE_STEP)
                        .map(output -> castListOfMaps(output.get("policyFindings")))
                        .filter(findings -> !findings.isEmpty())
                        .or(
                                () ->
                                        runRepository
                                                .successfulStep(runId, POLICY_STEP)
                                                .map(
                                                        output ->
                                                                castListOfMaps(
                                                                        output.get("findings"))))
                        .orElse(List.of());
        UUID taskId =
                reviewRepository.findOpenTasks().stream()
                        .filter(task -> task.caseId().equals(expenseCase.id()))
                        .map(ReviewRepository.ReviewTask::id)
                        .findFirst()
                        .orElse(null);
        ExpenseCase latest = caseService.getOwned(expenseCase.id(), expenseCase.ownerSubject());
        return new ExpenseWorkflowResult(
                latest.id(),
                runId,
                latest.status(),
                risk.score(),
                risk.level(),
                risk.signals(),
                policies,
                taskId);
    }

    private List<ExtractedExpenseDocument> loadExtracted(UUID caseId) {
        var results = documentRepository.findExtractionResultsByCaseId(caseId);
        if (results.isEmpty()) {
            throw new MyExpenseAgentException(
                    MyExpenseAgentErrorCode.VALIDATION_FAILED, "案例没有可用的票据提取结果");
        }
        List<ExtractedExpenseDocument> documents = new ArrayList<>();
        for (var result : results) {
            try {
                documents.add(
                        objectMapper.readValue(
                                result.resultJson(), ExtractedExpenseDocument.class));
            } catch (JsonProcessingException exception) {
                throw new IllegalStateException("票据提取结果不是合法 JSON", exception);
            }
        }
        return List.copyOf(documents);
    }

    private static RiskAssessment withDependencyFailure(RiskAssessment assessment) {
        List<RiskSignal> signals = new ArrayList<>(assessment.signals());
        if (signals.stream()
                .anyMatch(signal -> signal.code() == RiskSignalCode.DEPENDENCY_UNAVAILABLE)) {
            return assessment;
        }
        signals.add(
                new RiskSignal(
                        RiskSignalCode.DEPENDENCY_UNAVAILABLE,
                        30,
                        "MCP 依赖不可用，必须转人工核验",
                        Map.of("fallback", "WAITING_HUMAN")));
        int score = Math.min(100, signals.stream().mapToInt(RiskSignal::score).sum());
        return new RiskAssessment(score, RiskLevel.fromScore(score), true, signals);
    }

    private static Map<String, Object> policyFinding(PolicySearchMatch match) {
        return Map.ofEntries(
                Map.entry("policyId", match.policyId().toString()),
                Map.entry("policyCode", match.policyCode()),
                Map.entry("policyName", match.policyName()),
                Map.entry("version", match.policyVersion()),
                Map.entry("section", match.section()),
                Map.entry("chunkId", match.chunkId().toString()),
                Map.entry("content", match.content()),
                Map.entry("score", match.score()));
    }

    @SuppressWarnings("unchecked")
    private static boolean containsRecoverableEvidenceFailure(Map<String, Object> output) {
        Object applicantContext = output.get("applicantContext");
        if (!(applicantContext instanceof Map<?, ?>)) {
            return true;
        }
        if (applicantContext instanceof Map<?, ?> context
                && Boolean.TRUE.equals(context.get("dependencyFailure"))) {
            return true;
        }
        Object duplicateCheck = output.get("duplicateCheck");
        if (!(duplicateCheck instanceof Map<?, ?>)) {
            return true;
        }
        if (duplicateCheck instanceof Map<?, ?> check
                && Boolean.TRUE.equals(check.get("dependencyFailure"))) {
            return true;
        }
        Object projectBudget = output.get("projectBudget");
        if (!(projectBudget instanceof Map<?, ?>)) {
            return true;
        }
        if (projectBudget instanceof Map<?, ?> budget
                && Boolean.TRUE.equals(budget.get("dependencyFailure"))) {
            return true;
        }
        Object reimbursementHistory = output.get("reimbursementHistory");
        if (!(reimbursementHistory instanceof Map<?, ?>)) {
            return true;
        }
        if (reimbursementHistory instanceof Map<?, ?> history
                && Boolean.TRUE.equals(history.get("dependencyFailure"))) {
            return true;
        }
        Object evidence = output.get("evidence");
        if (evidence instanceof Map<?, ?> evidenceMap
                && "MCP_FAILURE".equals(String.valueOf(evidenceMap.get("source")))) {
            return true;
        }
        Object policyFindings = output.get("policyFindings");
        if (policyFindings instanceof List<?> findings) {
            return findings.stream()
                    .filter(Map.class::isInstance)
                    .map(item -> (Map<String, Object>) item)
                    .anyMatch(finding -> Boolean.TRUE.equals(finding.get("dependencyFailure")));
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> castListOfMaps(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(Map.class::isInstance)
                .map(item -> (Map<String, Object>) item)
                .toList();
    }

    private static <T> T join(CompletableFuture<T> future) {
        try {
            return future.join();
        } catch (CompletionException exception) {
            if (exception.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw exception;
        }
    }

    private static String hash(Object value) {
        try {
            byte[] bytes = String.valueOf(value).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    static String errorCode(RuntimeException exception) {
        return exception instanceof MyExpenseAgentException flowException
                ? flowException.code().name()
                : MyExpenseAgentErrorCode.INTERNAL_ERROR.name();
    }

    static String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }

    private record ContextEvidence(
            ExpenseContextGateway.ApplicantContext applicantContext,
            ExpenseContextGateway.DuplicateCheck duplicateCheck,
            ExpenseContextGateway.ProjectBudget projectBudget,
            ExpenseContextGateway.ReimbursementHistory reimbursementHistory,
            WorkflowEvidenceGateway.EvidenceResult evidenceResult) {}

    private record ParallelEvidence(
            ExpenseContextGateway.ApplicantContext applicantContext,
            ExpenseContextGateway.DuplicateCheck duplicateCheck,
            ExpenseContextGateway.ProjectBudget projectBudget,
            ExpenseContextGateway.ReimbursementHistory reimbursementHistory,
            WorkflowEvidenceGateway.EvidenceResult evidenceResult,
            List<Map<String, Object>> policyFindings) {}
}

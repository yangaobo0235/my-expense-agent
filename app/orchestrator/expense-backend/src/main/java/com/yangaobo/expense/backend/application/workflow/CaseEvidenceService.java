package com.yangaobo.expense.backend.application.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangaobo.expense.backend.application.ExpenseCaseApplicationService;
import com.yangaobo.expense.backend.application.settlement.ToolCallRepository;
import com.yangaobo.expense.backend.domain.risk.RiskAssessment;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CaseEvidenceService {

    private final ExpenseCaseApplicationService caseService;
    private final WorkflowRunRepository runRepository;
    private final ToolCallRepository toolCallRepository;
    private final ObjectMapper objectMapper;

    public CaseEvidenceService(
            ExpenseCaseApplicationService caseService,
            WorkflowRunRepository runRepository,
            ToolCallRepository toolCallRepository,
            ObjectMapper objectMapper) {
        this.caseService = caseService;
        this.runRepository = runRepository;
        this.toolCallRepository = toolCallRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public CaseEvidence get(UUID caseId, String subject, boolean privileged) {
        if (privileged) {
            caseService.getById(caseId);
        } else {
            caseService.getOwned(caseId, subject);
        }
        var run = runRepository.latestRun(caseId).orElse(null);
        List<WorkflowRunRepository.WorkflowStep> steps =
                run == null ? List.of() : runRepository.steps(run.id());
        List<Map<String, Object>> policies =
                stepOutput(steps, "PARALLEL_EVIDENCE_COLLECTION", "policyFindings");
        if (policies.isEmpty()) {
            policies = stepOutput(steps, "POLICY_RETRIEVAL", "findings");
        }
        RiskAssessment risk =
                steps.stream()
                        .filter(step -> "RISK_ASSESSMENT".equals(step.name()))
                        .filter(step -> "SUCCEEDED".equals(step.status()))
                        .findFirst()
                        .map(step -> step.output().get("assessment"))
                        .map(value -> objectMapper.convertValue(value, RiskAssessment.class))
                        .orElse(null);
        return new CaseEvidence(
                run,
                steps.stream().map(CaseEvidenceService::view).toList(),
                policies,
                risk,
                toolCallRepository.findByCaseId(caseId));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> stepOutput(
            List<WorkflowRunRepository.WorkflowStep> steps,
            String stepName,
            String key) {
        return steps.stream()
                .filter(step -> stepName.equals(step.name()))
                .filter(step -> "SUCCEEDED".equals(step.status()))
                .map(step -> step.output().get(key))
                .filter(List.class::isInstance)
                .map(value -> (List<?>) value)
                .flatMap(List::stream)
                .filter(Map.class::isInstance)
                .map(value -> (Map<String, Object>) value)
                .toList();
    }

    private static StepView view(WorkflowRunRepository.WorkflowStep step) {
        Map<String, Object> evidence =
                switch (step.name()) {
                    case "AGENT_PLAN" ->
                            selected(step.output(), "plan");
                    case "MCP_APPLICANT_CONTEXT" ->
                            selected(step.output(), "context");
                    case "MCP_DUPLICATE_CHECK" ->
                            selected(step.output(), "check");
                    case "MCP_PROJECT_BUDGET" ->
                            selected(step.output(), "budget");
                    case "MCP_REIMBURSEMENT_HISTORY" ->
                            selected(step.output(), "history");
                    case "MCP_REVIEW_EVIDENCE" ->
                            selected(step.output(), "evidence");
                    case "PARALLEL_EVIDENCE_COLLECTION" ->
                            selectedKeys(
                                    step.output(),
                                    List.of(
                                            "mode",
                                            "applicantContext",
                                            "duplicateCheck",
                                            "projectBudget",
                                            "reimbursementHistory",
                                            "evidence",
                                            "policyFindings"));
                    case "RISK_ASSESSMENT" ->
                            selectedKeys(
                                    step.output(),
                                    List.of("assessment", "complianceFacts"));
                    case "RISK_ROUTING" ->
                            selectedKeys(
                                    step.output(),
                                    List.of(
                                            "action",
                                            "requiresHumanReview",
                                            "debateAssistEnabled",
                                            "queue",
                                            "assigneeRole",
                                            "slaHours",
                                            "requiredEvidence",
                                            "userFacingMessage",
                                            "fallbackStrategy",
                                            "reasons"));
                    default -> Map.of();
                };
        return new StepView(
                step.id(),
                step.name(),
                step.attempt(),
                step.status(),
                duration(step.startedAt(), step.completedAt()),
                step.errorCode(),
                step.errorMessage(),
                evidence);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> selected(Map<String, Object> output, String key) {
        Object value = output.get(key);
        return value instanceof Map<?, ?> map
                ? java.util.Collections.unmodifiableMap(
                        new java.util.LinkedHashMap<>((Map<String, Object>) map))
                : Map.of();
    }

    private static Map<String, Object> selectedKeys(
            Map<String, Object> output, List<String> keys) {
        Map<String, Object> selected = new java.util.LinkedHashMap<>();
        for (String key : keys) {
            if (output.containsKey(key)) {
                selected.put(key, output.get(key));
            }
        }
        return java.util.Collections.unmodifiableMap(selected);
    }

    private static Long duration(Instant start, Instant end) {
        return start == null || end == null ? null : Duration.between(start, end).toMillis();
    }

    public record CaseEvidence(
            WorkflowRunRepository.WorkflowRunDetail run,
            List<StepView> steps,
            List<Map<String, Object>> policyFindings,
            RiskAssessment risk,
            List<ToolCallRepository.ToolCallDetail> toolCalls) {}

    public record StepView(
            UUID id,
            String name,
            int attempt,
            String status,
            Long durationMs,
            String errorCode,
            String errorMessage,
            Map<String, Object> evidence) {}
}

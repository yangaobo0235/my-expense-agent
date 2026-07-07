package com.yangaobo.expense.backend.application.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangaobo.expense.backend.application.ExpenseCaseApplicationService;
import com.yangaobo.expense.backend.application.settlement.ToolCallRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CaseEvidenceServiceTest {

    private final ExpenseCaseApplicationService caseService =
            mock(ExpenseCaseApplicationService.class);
    private final WorkflowRunRepository runRepository =
            mock(WorkflowRunRepository.class);
    private final ToolCallRepository toolRepository =
            mock(ToolCallRepository.class);
    private final CaseEvidenceService service =
            new CaseEvidenceService(
                    caseService,
                    runRepository,
                    toolRepository,
                    new ObjectMapper().findAndRegisterModules());

    @Test
    void aggregatesPolicyRiskAndSanitizedStepEvidence() {
        UUID caseId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        var run =
                new WorkflowRunRepository.WorkflowRunDetail(
                        runId,
                        caseId,
                        "workflow-1",
                        "SUCCEEDED",
                        Instant.parse("2026-06-22T00:00:00Z"),
                        Instant.parse("2026-06-22T00:00:02Z"),
                        null,
                        null,
                        "trace-1");
        when(runRepository.latestRun(caseId)).thenReturn(Optional.of(run));
        when(runRepository.steps(runId))
                .thenReturn(
                        List.of(
                                step(
                                        "AGENT_PLAN",
                                        Map.of(
                                                "plan",
                                                Map.of(
                                                        "planVersion",
                                                        "expenseflow-multi-agent-v1",
                                                        "agents",
                                                        List.of(
                                                                Map.of(
                                                                        "role",
                                                                        "POLICY_RAG_AGENT"))))),
                                step(
                                        "MCP_DUPLICATE_CHECK",
                                        Map.of(
                                                "check",
                                                Map.of(
                                                        "duplicate", true,
                                                        "source", "MCP"))),
                                step(
                                        "PARALLEL_EVIDENCE_COLLECTION",
                                        Map.of(
                                                "mode",
                                                "PARALLEL",
                                                "policyFindings",
                                                List.of(
                                                        Map.of(
                                                                "policyCode",
                                                                "HOTEL-CN",
                                                                "content",
                                                                "住宿上限")),
                                                "employeeContext",
                                                Map.of("source", "MCP"),
                                                "duplicateCheck",
                                                Map.of("duplicate", true),
                                                "evidence",
                                                Map.of("source", "MCP"))),
                                step(
                                        "RISK_ROUTING",
                                        Map.of(
                                                "action",
                                                "MEDIUM_RISK_HUMAN_REVIEW",
                                                "requiresHumanReview",
                                                true,
                                                "debateAssistEnabled",
                                                false,
                                                "queue",
                                                "STANDARD_REVIEW",
                                                "reasons",
                                                List.of("DUPLICATE_DOCUMENT"))),
                                step(
                                        "RISK_ASSESSMENT",
                                        Map.of(
                                                "assessment",
                                                Map.of(
                                                        "score",
                                                        30,
                                                        "level",
                                                        "MEDIUM",
                                                        "requiresHumanReview",
                                                        true,
                                                        "signals",
                                                        List.of(
                                                                Map.of(
                                                                        "code",
                                                                        "DUPLICATE_DOCUMENT",
                                                                        "score",
                                                                        30,
                                                                        "message",
                                                                        "重复凭证",
                                                                        "evidence",
                                                                        Map.of())))))));
        when(toolRepository.findByCaseId(caseId)).thenReturn(List.of());

        var result = service.get(caseId, "employee-1", false);

        assertThat(result.policyFindings()).hasSize(1);
        assertThat(result.risk().score()).isEqualTo(30);
        assertThat(result.steps().getFirst().evidence())
                .containsEntry("planVersion", "expenseflow-multi-agent-v1");
        assertThat(result.steps().get(1).evidence())
                .containsEntry("duplicate", true)
                .doesNotContainKey("inputHash");
        assertThat(result.steps())
                .filteredOn(step -> "PARALLEL_EVIDENCE_COLLECTION".equals(step.name()))
                .singleElement()
                .satisfies(
                        step ->
                                assertThat(step.evidence())
                                        .containsEntry("mode", "PARALLEL")
                                        .containsKeys("policyFindings", "employeeContext"));
        assertThat(result.steps())
                .filteredOn(step -> "RISK_ROUTING".equals(step.name()))
                .singleElement()
                .satisfies(
                        step ->
                                assertThat(step.evidence())
                                        .containsEntry("action", "MEDIUM_RISK_HUMAN_REVIEW")
                                        .containsEntry("queue", "STANDARD_REVIEW"));
        verify(caseService).getOwned(caseId, "employee-1");
    }

    @Test
    void privilegedReaderUsesUnscopedLookup() {
        UUID caseId = UUID.randomUUID();
        when(runRepository.latestRun(caseId)).thenReturn(Optional.empty());
        when(toolRepository.findByCaseId(caseId)).thenReturn(List.of());

        service.get(caseId, "reviewer-1", true);

        verify(caseService).getById(caseId);
    }

    private static WorkflowRunRepository.WorkflowStep step(
            String name, Map<String, Object> output) {
        return new WorkflowRunRepository.WorkflowStep(
                UUID.randomUUID(),
                name,
                1,
                "SUCCEEDED",
                output,
                Instant.parse("2026-06-22T00:00:00Z"),
                Instant.parse("2026-06-22T00:00:01Z"),
                null,
                null);
    }
}

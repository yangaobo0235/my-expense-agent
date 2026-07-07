package com.yangaobo.expense.backend.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.yangaobo.expense.backend.application.ExpenseCaseApplicationService;
import com.yangaobo.expense.backend.application.observability.CaseAuditRepository;
import com.yangaobo.expense.backend.application.observability.ModelCallRepository;
import com.yangaobo.expense.backend.application.workflow.WorkflowRunRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ObservabilityControllerTest {

    private final WorkflowRunRepository repository = mock(WorkflowRunRepository.class);
    private final ModelCallRepository modelCalls = mock(ModelCallRepository.class);
    private final CaseAuditRepository audits = mock(CaseAuditRepository.class);
    private final ExpenseCaseApplicationService cases = mock(ExpenseCaseApplicationService.class);
    private final ObservabilityController controller =
            new ObservabilityController(repository, modelCalls, audits, cases);

    @Test
    void enrichesRecentRunsWithWorkflowStepHealth() {
        UUID runId = UUID.randomUUID();
        UUID caseId = UUID.randomUUID();
        when(repository.recentRuns(20))
                .thenReturn(
                        List.of(
                                new WorkflowRunRepository.WorkflowRunDetail(
                                        runId,
                                        caseId,
                                        "request-1",
                                        "SUCCEEDED",
                                        Instant.parse("2026-06-22T00:00:00Z"),
                                        Instant.parse("2026-06-22T00:00:02Z"),
                                        null,
                                        null,
                                        "trace-1")));
        when(repository.steps(runId))
                .thenReturn(
                        List.of(
                                step("AGENT_PLAN", "SUCCEEDED"),
                                step("POLICY_RETRIEVAL", "SUCCEEDED"),
                                step("RISK_ASSESSMENT", "FAILED")));

        List<ObservabilityController.ObservableRunResponse> responses =
                controller.recentRuns(20);

        assertThat(responses).hasSize(1);
        ObservabilityController.ObservableRunResponse response = responses.getFirst();
        assertThat(response.runId()).isEqualTo(runId);
        assertThat(response.stepCount()).isEqualTo(3);
        assertThat(response.succeededStepCount()).isEqualTo(2);
        assertThat(response.failedStepCount()).isEqualTo(1);
        assertThat(response.durationMs()).isEqualTo(2000);
        assertThat(response.agentPlanRecorded()).isTrue();
    }

    private static WorkflowRunRepository.WorkflowStep step(String name, String status) {
        return new WorkflowRunRepository.WorkflowStep(
                UUID.randomUUID(),
                name,
                1,
                status,
                Map.of(),
                Instant.parse("2026-06-22T00:00:00Z"),
                "SUCCEEDED".equals(status) ? Instant.parse("2026-06-22T00:00:01Z") : null,
                "FAILED".equals(status) ? "DEPENDENCY_UNAVAILABLE" : null,
                "FAILED".equals(status) ? "依赖调用失败" : null);
    }
}

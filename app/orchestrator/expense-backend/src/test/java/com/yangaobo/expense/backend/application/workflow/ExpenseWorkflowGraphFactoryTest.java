package com.yangaobo.expense.backend.application.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yangaobo.expense.backend.application.extraction.ExtractedExpenseDocument;
import com.yangaobo.expense.backend.domain.model.ExpenseCase;
import com.yangaobo.expense.backend.domain.model.Money;
import com.yangaobo.expense.backend.domain.model.RiskLevel;
import com.yangaobo.expense.backend.domain.risk.RiskAssessment;
import com.yangaobo.expense.common.domain.ExpenseCaseStatus;
import com.yangaobo.expense.common.error.MyExpenseAgentErrorCode;
import com.yangaobo.expense.common.error.MyExpenseAgentException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class ExpenseWorkflowGraphFactoryTest {

    @Test
    void restoreRouteShouldSkipExecutionNodes() {
        ExpenseWorkflowSteps steps = mock(ExpenseWorkflowSteps.class);
        ExpenseWorkflowResult restored =
                new ExpenseWorkflowResult(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        ExpenseCaseStatus.WAITING_HUMAN,
                        50,
                        RiskLevel.MEDIUM,
                        List.of(),
                        List.of(),
                        UUID.randomUUID());
        when(steps.restoreResultNode(any()))
                .thenReturn(Map.of(ExpenseWorkflowGraphState.WORKFLOW_RESULT, restored));

        ExpenseWorkflowResult result =
                new ExpenseWorkflowGraphFactory(steps)
                        .graph()
                        .execute(initialState(true, restored.caseId(), restored.runId()));

        assertThat(result).isEqualTo(restored);
        verify(steps).restoreResultNode(any());
        verify(steps, never()).loadExtractedNode(any());
        verify(steps, never()).finalizeNode(any());
    }

    @Test
    void executionRouteShouldRunReviewWorkflowNodes() {
        ExpenseWorkflowSteps steps = mock(ExpenseWorkflowSteps.class);
        UUID caseId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        ExpenseWorkflowResult finalResult =
                new ExpenseWorkflowResult(
                        caseId,
                        runId,
                        ExpenseCaseStatus.APPROVED,
                        10,
                        RiskLevel.LOW,
                        List.of(),
                        List.of(Map.of("policyCode", "TRAVEL-CN")),
                        null);
        RiskAssessment risk = new RiskAssessment(10, RiskLevel.LOW, false, List.of());
        RiskRoutingDecision routing =
                new RiskRoutingDecision(
                        RiskRoutingAction.LOW_RISK_PATH,
                        false,
                        false,
                        "AUTO_APPROVAL",
                        "SYSTEM",
                        1,
                        List.of(),
                        "",
                        "AUTO_APPROVE",
                        List.of());

        when(steps.loadExtractedNode(any()))
                .thenReturn(
                        Map.of(
                                ExpenseWorkflowGraphState.EXTRACTED_DOCUMENTS,
                                List.of(extractedDocument())));
        when(steps.executionPolicyNode(any()))
                .thenReturn(Map.of(ExpenseWorkflowGraphState.EXECUTION_POLICY, Map.of("version", "v1")));
        when(steps.parallelEvidenceNode(any()))
                .thenReturn(
                        Map.of(
                                ExpenseWorkflowGraphState.APPLICANT_CONTEXT,
                                new ExpenseContextGateway.ApplicantContext(
                                        "student-1",
                                        "CS-SRTP",
                                        "STUDENT",
                                        "CN",
                                        List.of(),
                                        "TEST",
                                        false,
                                        ""),
                                ExpenseWorkflowGraphState.DUPLICATE_CHECK,
                                new ExpenseContextGateway.DuplicateCheck(
                                        false, List.of(), Map.of(), "TEST", false, ""),
                                ExpenseWorkflowGraphState.PROJECT_BUDGET,
                                new ExpenseContextGateway.ProjectBudget(
                                        "student-1",
                                        "CS-SRTP",
                                        new BigDecimal("50000.00"),
                                        new BigDecimal("49000.00"),
                                        "CNY",
                                        1,
                                        Instant.parse("2026-07-11T00:00:00Z"),
                                        "TEST",
                                        false,
                                        ""),
                                ExpenseWorkflowGraphState.REIMBURSEMENT_HISTORY,
                                new ExpenseContextGateway.ReimbursementHistory(
                                        List.of(), "TEST", false, ""),
                                ExpenseWorkflowGraphState.EVIDENCE_RESULT,
                                new WorkflowEvidenceGateway.EvidenceResult(
                                        true, "evidence-1", "TEST"),
                                ExpenseWorkflowGraphState.POLICY_FINDINGS,
                                finalResult.policyFindings()));
        when(steps.evidenceQualityNode(any()))
                .thenReturn(Map.of(
                        ExpenseWorkflowGraphState.EVIDENCE_QUALITY,
                        new EvidenceQualityResult(EvidenceQuality.COMPLETE, List.of(), List.of())));
        when(steps.riskAssessmentNode(any()))
                .thenReturn(Map.of(ExpenseWorkflowGraphState.RISK_ASSESSMENT, risk));
        when(steps.riskRoutingNode(any()))
                .thenReturn(Map.of(ExpenseWorkflowGraphState.ROUTING_DECISION, routing));
        when(steps.finalizeNode(any()))
                .thenReturn(Map.of(ExpenseWorkflowGraphState.WORKFLOW_RESULT, finalResult));

        ExpenseWorkflowResult result =
                new ExpenseWorkflowGraphFactory(steps)
                        .graph()
                        .execute(initialState(false, caseId, runId));

        assertThat(result).isEqualTo(finalResult);
        verify(steps).loadExtractedNode(any());
        verify(steps).executionPolicyNode(any());
        verify(steps).parallelEvidenceNode(any());
        verify(steps).evidenceQualityNode(any());
        verify(steps).riskAssessmentNode(any());
        verify(steps).riskRoutingNode(any());
        verify(steps).finalizeNode(any());
        verify(steps, never()).restoreResultNode(any());
    }

    @Test
    void evidenceGateShouldCreateMoreInfoTaskWithoutRunningRiskRules() {
        ExpenseWorkflowSteps steps = mock(ExpenseWorkflowSteps.class);
        UUID caseId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        ExpenseWorkflowResult waiting =
                result(caseId, runId, ExpenseCaseStatus.WAITING_MORE_INFO);
        stubPathToQualityGate(steps);
        when(steps.evidenceQualityNode(any()))
                .thenReturn(
                        Map.of(
                                ExpenseWorkflowGraphState.EVIDENCE_QUALITY,
                                new EvidenceQualityResult(
                                        EvidenceQuality.MISSING_MATERIALS,
                                        List.of("住宿明细"),
                                        List.of())));
        when(steps.createMoreInfoTaskNode(any()))
                .thenReturn(Map.of(ExpenseWorkflowGraphState.WORKFLOW_RESULT, waiting));

        ExpenseWorkflowResult actual =
                new ExpenseWorkflowGraphFactory(steps)
                        .graph()
                        .execute(initialState(false, caseId, runId));

        assertThat(actual).isEqualTo(waiting);
        verify(steps).createMoreInfoTaskNode(any());
        verify(steps, never()).riskAssessmentNode(any());
        verify(steps, never()).createReviewTaskNode(any());
    }

    @Test
    void riskRouterShouldUseCollegeFinanceDependencyAndMoreInfoBranches() {
        assertRoute(RiskRoutingAction.REQUEST_MORE_INFO, false, false);
        assertRoute(RiskRoutingAction.COLLEGE_REVIEW, true, false);
        assertRoute(RiskRoutingAction.DEPENDENCY_REVIEW, true, false);
        assertRoute(RiskRoutingAction.FINANCE_REVIEW, true, true);
    }

    @Test
    void nodeFailureShouldExposeOriginalCause() {
        ExpenseWorkflowSteps steps = mock(ExpenseWorkflowSteps.class);
        when(steps.loadExtractedNode(any()))
                .thenThrow(
                        new MyExpenseAgentException(
                                MyExpenseAgentErrorCode.VALIDATION_FAILED,
                                "案例没有可用的票据提取结果"));

        Assertions.assertThatThrownBy(
                        () ->
                                new ExpenseWorkflowGraphFactory(steps)
                                        .graph()
                                        .execute(
                                                initialState(
                                                        false,
                                                        UUID.randomUUID(),
                                                        UUID.randomUUID())))
                .isInstanceOf(MyExpenseAgentException.class)
                .hasMessage("案例没有可用的票据提取结果");
    }

    private static Map<String, Object> initialState(boolean restoreOnly, UUID caseId, UUID runId) {
        return ExpenseWorkflowGraphState.initial(
                caseId,
                runId,
                "student-1",
                "request-1",
                new ExpenseWorkflowCommand(
                        "request-1",
                        "竞赛差旅费",
                        LocalDate.of(2026, 7, 11)),
                ExpenseCase.create(
                                caseId,
                                "CF-20260711-0001",
                                "student-1",
                                "李明",
                                "CS-SRTP",
                                "蓝桥杯竞赛差旅报销",
                                new Money(new BigDecimal("100.00"), "CNY"),
                                Instant.parse("2026-07-11T00:00:00Z"))
                        .transitionTo(
                                ExpenseCaseStatus.UPLOADED,
                                Instant.parse("2026-07-11T00:01:00Z"))
                        .transitionTo(
                                ExpenseCaseStatus.EXTRACTING,
                                Instant.parse("2026-07-11T00:02:00Z"))
                        .transitionTo(
                                ExpenseCaseStatus.EXTRACTED,
                                Instant.parse("2026-07-11T00:03:00Z")),
                restoreOnly,
                new WorkflowRunRepository.WorkflowRun(runId, caseId, "request-1", "RUNNING"));
    }

    private static void assertRoute(
            RiskRoutingAction action, boolean reviewTaskExpected, boolean summaryExpected) {
        ExpenseWorkflowSteps steps = mock(ExpenseWorkflowSteps.class);
        UUID caseId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        ExpenseWorkflowResult terminal =
                result(
                        caseId,
                        runId,
                        action == RiskRoutingAction.REQUEST_MORE_INFO
                                ? ExpenseCaseStatus.WAITING_MORE_INFO
                                : ExpenseCaseStatus.WAITING_HUMAN);
        stubPathToQualityGate(steps);
        when(steps.evidenceQualityNode(any()))
                .thenReturn(
                        Map.of(
                                ExpenseWorkflowGraphState.EVIDENCE_QUALITY,
                                new EvidenceQualityResult(
                                        EvidenceQuality.COMPLETE, List.of(), List.of())));
        when(steps.riskAssessmentNode(any()))
                .thenReturn(
                        Map.of(
                                ExpenseWorkflowGraphState.RISK_ASSESSMENT,
                                new RiskAssessment(50, RiskLevel.MEDIUM, true, List.of())));
        when(steps.riskRoutingNode(any()))
                .thenReturn(
                        Map.of(
                                ExpenseWorkflowGraphState.ROUTING_DECISION,
                                new RiskRoutingDecision(
                                        action,
                                        true,
                                        summaryExpected,
                                        "TEST",
                                        "COLLEGE_REVIEWER",
                                        24,
                                        List.of(),
                                        "",
                                        "HUMAN_REVIEW",
                                        List.of())));
        when(steps.createMoreInfoTaskNode(any()))
                .thenReturn(Map.of(ExpenseWorkflowGraphState.WORKFLOW_RESULT, terminal));
        when(steps.generateReviewSummaryNode(any())).thenReturn(Map.of());
        when(steps.verifySummaryReferencesNode(any())).thenReturn(Map.of());
        when(steps.createReviewTaskNode(any()))
                .thenReturn(Map.of(ExpenseWorkflowGraphState.WORKFLOW_RESULT, terminal));

        ExpenseWorkflowResult actual =
                new ExpenseWorkflowGraphFactory(steps)
                        .graph()
                        .execute(initialState(false, caseId, runId));

        assertThat(actual).isEqualTo(terminal);
        if (reviewTaskExpected) {
            verify(steps).createReviewTaskNode(any());
        } else {
            verify(steps).createMoreInfoTaskNode(any());
        }
        if (summaryExpected) {
            verify(steps).generateReviewSummaryNode(any());
            verify(steps).verifySummaryReferencesNode(any());
        } else {
            verify(steps, never()).generateReviewSummaryNode(any());
            verify(steps, never()).verifySummaryReferencesNode(any());
        }
    }

    private static void stubPathToQualityGate(ExpenseWorkflowSteps steps) {
        when(steps.loadExtractedNode(any())).thenReturn(Map.of());
        when(steps.executionPolicyNode(any())).thenReturn(Map.of());
        when(steps.parallelEvidenceNode(any())).thenReturn(Map.of());
    }

    private static ExpenseWorkflowResult result(
            UUID caseId, UUID runId, ExpenseCaseStatus status) {
        return new ExpenseWorkflowResult(
                caseId,
                runId,
                status,
                50,
                RiskLevel.MEDIUM,
                List.of(),
                List.of(),
                UUID.randomUUID());
    }

    private static ExtractedExpenseDocument extractedDocument() {
        return new ExtractedExpenseDocument(
                "INVOICE",
                "INV",
                "001",
                "南京青奥酒店",
                "江南大学",
                LocalDate.of(2026, 7, 11),
                new BigDecimal("100.00"),
                "CNY",
                List.of(),
                0.99,
                List.of());
    }
}

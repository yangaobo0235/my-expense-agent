package com.yangaobo.expense.agents;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ExpenseMultiAgentPlannerTest {

    private final ExpenseMultiAgentPlanner planner = new ExpenseMultiAgentPlanner();

    @Test
    void buildsAuditableMultiAgentPlanWithSeparatedWriteTools() {
        ExpenseMultiAgentPlan plan = planner.plan("case-1", "request-1");

        assertThat(plan.planVersion()).isEqualTo(ExpenseMultiAgentPlanner.PLAN_VERSION);
        assertThat(plan.steps())
                .extracting(AgentStepDefinition::role)
                .containsExactly(
                        AgentRole.RECEIPT_EXTRACTION_AGENT,
                        AgentRole.MCP_CONTEXT_AGENT,
                        AgentRole.POLICY_RAG_AGENT,
                        AgentRole.RISK_RULE_AGENT,
                        AgentRole.REVIEW_SUMMARY_AGENT,
                        AgentRole.APPROVED_SETTLEMENT_AGENT);
        assertThat(plan.steps().stream()
                        .filter(AgentStepDefinition::writeOperationAllowed)
                        .map(AgentStepDefinition::role))
                .containsExactly(AgentRole.APPROVED_SETTLEMENT_AGENT);
        assertThat(plan.steps())
                .extracting(AgentStepDefinition::sequence)
                .containsExactly(1, 2, 3, 4, 5, 6);
        assertThat(plan.steps())
                .filteredOn(step -> "EVIDENCE_COLLECTION".equals(step.executionGroup()))
                .extracting(AgentStepDefinition::orchestrationMode)
                .containsOnly("PARALLEL");
        assertThat(plan.steps())
                .filteredOn(step -> step.role() == AgentRole.RISK_RULE_AGENT)
                .singleElement()
                .satisfies(step -> {
                    assertThat(step.orchestrationMode()).isEqualTo("ROUTER");
                    assertThat(step.routeCondition()).contains("风险等级");
                });
        assertThat(plan.steps())
                .filteredOn(step -> step.role() == AgentRole.REVIEW_SUMMARY_AGENT)
                .singleElement()
                .satisfies(step -> {
                    assertThat(step.orchestrationMode()).isEqualTo("DEBATE_ASSIST");
                    assertThat(step.routeCondition()).contains("高风险");
                });
        assertThat(plan.steps())
                .filteredOn(AgentStepDefinition::writeOperationAllowed)
                .singleElement()
                .satisfies(step -> {
                    assertThat(step.failurePolicy()).isEqualTo(AgentFailurePolicy.IDEMPOTENT_WRITE_RETRY);
                    assertThat(step.maxAttempts()).isEqualTo(3);
                    assertThat(step.handoffTarget()).contains("财务复核");
                });
        assertThat(plan.toEvidence())
                .containsEntry("planVersion", ExpenseMultiAgentPlanner.PLAN_VERSION)
                .containsEntry("caseId", "case-1")
                .containsEntry("requestId", "request-1");
        assertThat(plan.toEvidence().get("architecture"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("primaryPattern", "HIERARCHICAL_ORCHESTRATOR")
                .containsEntry("backbonePattern", "SEQUENTIAL_CHAIN")
                .containsEntry("parallelEvidenceCollection", true)
                .containsEntry("riskRouting", true);
    }
}

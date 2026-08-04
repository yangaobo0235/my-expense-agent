package com.yangaobo.expense.agents;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ExpenseExecutionPolicyTest {

    @Test
    void separatesEvidenceCapabilitiesFromApprovalGatedWrites() {
        GovernedExecutionPlan plan = new ExpenseExecutionPolicy().plan("case-1", "request-1");

        assertThat(plan.planVersion()).isEqualTo(ExpenseExecutionPolicy.PLAN_VERSION);
        assertThat(plan.steps()).hasSize(6);
        assertThat(plan.steps().stream()
                        .filter(AgentStepDefinition::writeOperationAllowed)
                        .map(AgentStepDefinition::role))
                .containsExactly(AgentRole.APPROVED_SETTLEMENT_AGENT);
        assertThat(plan.toEvidence())
                .containsEntry("policyType", "GOVERNED_EXECUTION_POLICY")
                .containsEntry("modelAuthority", "EVIDENCE_AND_SUMMARY_ONLY")
                .containsEntry("writeAuthority", "APPROVAL_GATED_SERVER_SIDE_ONLY")
                .doesNotContainKeys("architecture", "agents", "debateAssistScope");
    }
}

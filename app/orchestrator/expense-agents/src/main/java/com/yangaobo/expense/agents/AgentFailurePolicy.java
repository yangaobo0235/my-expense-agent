package com.yangaobo.expense.agents;

public enum AgentFailurePolicy {
    REQUIRE_HUMAN_REVIEW,
    RETRY_THEN_HUMAN_REVIEW,
    STOP_AND_ESCALATE,
    IDEMPOTENT_WRITE_RETRY
}

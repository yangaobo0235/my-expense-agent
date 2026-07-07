package com.yangaobo.expense.agents;

import java.util.List;

public record AgentStepDefinition(
        int sequence,
        AgentRole role,
        String name,
        String phase,
        String orchestrationMode,
        String executionGroup,
        List<AgentRole> dependencies,
        String routeCondition,
        String responsibility,
        List<String> inputKeys,
        List<String> outputKeys,
        List<String> allowedTools,
        boolean writeOperationAllowed,
        AgentFailurePolicy failurePolicy,
        int maxAttempts,
        String handoffTarget) {

    public AgentStepDefinition {
        if (sequence <= 0) {
            throw new IllegalArgumentException("Agent sequence 必须为正数");
        }
        if (role == null) {
            throw new IllegalArgumentException("Agent role 不能为空");
        }
        name = required(name, "name");
        phase = required(phase, "phase");
        orchestrationMode = required(orchestrationMode, "orchestrationMode");
        executionGroup = required(executionGroup, "executionGroup");
        dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
        routeCondition = routeCondition == null ? "" : routeCondition.trim();
        responsibility = required(responsibility, "responsibility");
        inputKeys = inputKeys == null ? List.of() : List.copyOf(inputKeys);
        outputKeys = outputKeys == null ? List.of() : List.copyOf(outputKeys);
        allowedTools = allowedTools == null ? List.of() : List.copyOf(allowedTools);
        failurePolicy = failurePolicy == null
                ? AgentFailurePolicy.REQUIRE_HUMAN_REVIEW
                : failurePolicy;
        maxAttempts = Math.max(maxAttempts, 1);
        handoffTarget = required(handoffTarget, "handoffTarget");
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + "不能为空");
        }
        return value.trim();
    }
}

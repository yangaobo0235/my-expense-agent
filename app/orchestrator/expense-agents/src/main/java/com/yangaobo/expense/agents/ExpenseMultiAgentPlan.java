package com.yangaobo.expense.agents;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record ExpenseMultiAgentPlan(
        String planVersion,
        String caseId,
        String requestId,
        List<AgentStepDefinition> steps) {

    public ExpenseMultiAgentPlan {
        planVersion = required(planVersion, "planVersion");
        caseId = required(caseId, "caseId");
        requestId = required(requestId, "requestId");
        if (steps == null || steps.isEmpty()) {
            throw new IllegalArgumentException("多 Agent 计划至少需要一个步骤");
        }
        steps = List.copyOf(steps);
    }

    public Map<String, Object> toEvidence() {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("planVersion", planVersion);
        evidence.put("caseId", caseId);
        evidence.put("requestId", requestId);
        evidence.put(
                "architecture",
                Map.ofEntries(
                        Map.entry("orchestrator", "ExpenseCoordinator"),
                        Map.entry("primaryPattern", "HIERARCHICAL_ORCHESTRATOR"),
                        Map.entry("backbonePattern", "SEQUENTIAL_CHAIN"),
                        Map.entry("parallelEvidenceCollection", true),
                        Map.entry("riskRouting", true),
                        Map.entry("debateAssistScope", "HIGH_RISK_OR_CONFLICT_ONLY"),
                        Map.entry(
                                "governance",
                                "模型只生成证据和建议，审批、驳回和付款仍由确定性规则或人工决策控制")));
        evidence.put(
                "parallelGroups",
                List.of(
                        Map.of(
                                "group",
                                "EVIDENCE_COLLECTION",
                                "mode",
                                "PARALLEL",
                                "members",
                                List.of(
                                        AgentRole.MCP_CONTEXT_AGENT.name(),
                                        AgentRole.POLICY_RAG_AGENT.name()))));
        evidence.put(
                "routes",
                List.of(
                        "LOW_RISK_AUTO_APPROVE",
                        "MEDIUM_RISK_HUMAN_REVIEW",
                        "HIGH_RISK_ESCALATE_WITH_DEBATE_ASSIST",
                        "MISSING_INFO_REQUEST_MORE",
                        "DEPENDENCY_FAILURE_HUMAN_REVIEW"));
        evidence.put(
                "agents",
                steps.stream()
                        .map(ExpenseMultiAgentPlan::stepEvidence)
                        .toList());
        return evidence;
    }

    private static Map<String, Object> stepEvidence(AgentStepDefinition step) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("sequence", step.sequence());
        item.put("role", step.role().name());
        item.put("name", step.name());
        item.put("phase", step.phase());
        item.put("orchestrationMode", step.orchestrationMode());
        item.put("executionGroup", step.executionGroup());
        item.put(
                "dependencies",
                step.dependencies().stream().map(AgentRole::name).toList());
        item.put("routeCondition", step.routeCondition());
        item.put("responsibility", step.responsibility());
        item.put("inputKeys", step.inputKeys());
        item.put("outputKeys", step.outputKeys());
        item.put("allowedTools", step.allowedTools());
        item.put("writeOperationAllowed", step.writeOperationAllowed());
        item.put("failurePolicy", step.failurePolicy().name());
        item.put("maxAttempts", step.maxAttempts());
        item.put("handoffTarget", step.handoffTarget());
        return item;
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + "不能为空");
        }
        return value.trim();
    }
}

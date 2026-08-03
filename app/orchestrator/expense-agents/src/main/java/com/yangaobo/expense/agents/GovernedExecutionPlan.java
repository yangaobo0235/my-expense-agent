package com.yangaobo.expense.agents;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record GovernedExecutionPlan(
        String planVersion,
        String caseId,
        String requestId,
        List<AgentStepDefinition> steps) {

    public GovernedExecutionPlan {
        planVersion = required(planVersion, "planVersion");
        caseId = required(caseId, "caseId");
        requestId = required(requestId, "requestId");
        if (steps == null || steps.isEmpty()) {
            throw new IllegalArgumentException("受治理执行策略至少需要一个步骤");
        }
        steps = List.copyOf(steps);
    }

    public Map<String, Object> toEvidence() {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("planVersion", planVersion);
        evidence.put("caseId", caseId);
        evidence.put("requestId", requestId);
        evidence.put("policyType", "GOVERNED_EXECUTION_POLICY");
        evidence.put("parallelEvidenceCollection", true);
        evidence.put("deterministicRiskRouting", true);
        evidence.put("modelAuthority", "EVIDENCE_AND_SUMMARY_ONLY");
        evidence.put("writeAuthority", "APPROVAL_GATED_SERVER_SIDE_ONLY");
        evidence.put("steps", steps.stream().map(GovernedExecutionPlan::stepEvidence).toList());
        return evidence;
    }

    private static Map<String, Object> stepEvidence(AgentStepDefinition step) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("sequence", step.sequence());
        item.put("capability", step.role().name());
        item.put("name", step.name());
        item.put("phase", step.phase());
        item.put("executionGroup", step.executionGroup());
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

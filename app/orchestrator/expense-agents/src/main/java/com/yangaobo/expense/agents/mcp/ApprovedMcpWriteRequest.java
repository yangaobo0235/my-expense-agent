package com.yangaobo.expense.agents.mcp;

public record ApprovedMcpWriteRequest(
        String toolName,
        String argumentsJson,
        String requestId,
        String actorSubject,
        String approvalReference) {

    public ApprovedMcpWriteRequest {
        toolName = required(toolName, "toolName");
        argumentsJson = required(argumentsJson, "argumentsJson");
        requestId = required(requestId, "requestId");
        actorSubject = required(actorSubject, "actorSubject");
        approvalReference = required(approvalReference, "approvalReference");
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + "不能为空");
        }
        return value.trim();
    }
}

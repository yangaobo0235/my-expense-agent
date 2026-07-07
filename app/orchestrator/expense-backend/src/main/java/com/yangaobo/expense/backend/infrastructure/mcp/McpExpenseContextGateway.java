package com.yangaobo.expense.backend.infrastructure.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangaobo.expense.agents.mcp.ExpenseMcpGateway;
import com.yangaobo.expense.backend.application.governance.AgentInputGuard;
import com.yangaobo.expense.backend.application.governance.AgentInputGuard.GuardMode;
import com.yangaobo.expense.backend.application.workflow.ExpenseContextGateway;
import dev.langchain4j.service.tool.ToolExecutionResult;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class McpExpenseContextGateway implements ExpenseContextGateway {

    private final ExpenseMcpGateway gateway;
    private final ObjectMapper objectMapper;
    private final AgentInputGuard inputGuard;
    private final McpRetryExecutor retryExecutor;

    public McpExpenseContextGateway(
            ExpenseMcpGateway gateway,
            ObjectMapper objectMapper,
            McpRetryExecutor retryExecutor) {
        this(
                gateway,
                objectMapper,
                new AgentInputGuard(new com.yangaobo.expense.backend.application.governance.SensitiveDataMasker()),
                retryExecutor);
    }

    public McpExpenseContextGateway(
            ExpenseMcpGateway gateway,
            ObjectMapper objectMapper,
            AgentInputGuard inputGuard,
            McpRetryExecutor retryExecutor) {
        this.gateway = gateway;
        this.objectMapper = objectMapper;
        this.inputGuard = inputGuard;
        this.retryExecutor = retryExecutor;
    }

    @Override
    public EmployeeContext employeeContext(
            String employeeId,
            String fallbackRegion,
            String fallbackEmployeeGrade) {
        JsonNode profile =
                call(
                        "get_employee_profile",
                        Map.of("employeeId", employeeId));
        JsonNode methods =
                call(
                        "get_payment_methods",
                        Map.of("employeeId", employeeId));
        return new EmployeeContext(
                text(profile, "employeeId", employeeId),
                text(profile, "departmentCode", ""),
                text(
                        profile,
                        "employeeGrade",
                        fallbackEmployeeGrade),
                text(profile, "region", fallbackRegion),
                readStrings(methods),
                "MCP",
                false,
                "");
    }

    @Override
    public DuplicateCheck duplicateCheck(
            UUID currentCaseId,
            List<String> documentSha256,
            boolean fallbackDuplicate) {
        List<String> duplicates = new ArrayList<>();
        Map<String, Object> evidence = new LinkedHashMap<>();
        for (String sha256 : documentSha256) {
            JsonNode result =
                    call(
                            "check_duplicate_document",
                            Map.of(
                                    "sha256",
                                    sha256,
                                    "excludeCaseId",
                                    currentCaseId.toString()));
            boolean duplicate = result.path("duplicate").asBoolean(false);
            if (duplicate) {
                duplicates.add(sha256);
            }
            evidence.put(
                    sha256,
                    objectMapper.convertValue(
                            result, new TypeReference<Map<String, Object>>() {}));
        }
        return new DuplicateCheck(
                fallbackDuplicate || !duplicates.isEmpty(),
                duplicates,
                evidence,
                "MCP",
                false,
                "");
    }

    private JsonNode call(String toolName, Map<String, Object> arguments) {
        Map<String, Object> guardedArguments =
                inputGuard.inspectMap("mcp-read:" + toolName, arguments, GuardMode.REPORT_ONLY);
        ToolExecutionResult result =
                retryExecutor.execute(
                        toolName,
                        () ->
                                gateway.executeReadOnly(
                                        toolName, write(guardedArguments)));
        if (result.isError()) {
            throw new IllegalStateException(
                    "MCP Tool 调用失败：" + toolName + "，" + result.resultText());
        }
        try {
            return objectMapper.readTree(result.resultText());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "MCP Tool 返回的不是合法 JSON：" + toolName, exception);
        }
    }

    private String write(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("MCP Tool 参数序列化失败", exception);
        }
    }

    private static String text(
            JsonNode node, String field, String fallback) {
        String value = node.path(field).asText("");
        return value.isBlank() ? fallback : value;
    }

    private static List<String> readStrings(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        node.forEach(item -> values.add(item.asText()));
        return List.copyOf(values);
    }
}

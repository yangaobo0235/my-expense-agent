package com.yangaobo.expense.agents.mcp;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.service.tool.ToolProvider;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ExpenseMcpGateway {

    private final List<McpClient> clients;
    private final Map<String, McpClient> clientsByKey;
    private final ToolProvider readOnlyToolProvider;

    public ExpenseMcpGateway(List<McpClient> clients) {
        this.clients = List.copyOf(clients);
        this.clientsByKey =
                clients.stream()
                        .collect(
                                Collectors.toUnmodifiableMap(
                                        McpClient::key, Function.identity()));
        for (ExpenseMcpToolCatalog tool : ExpenseMcpToolCatalog.values()) {
            if (!clientsByKey.containsKey(tool.clientKey())) {
                throw new IllegalArgumentException(
                        "缺少 MCP Client：" + tool.clientKey());
            }
        }
        this.readOnlyToolProvider =
                McpToolProvider.builder()
                        .mcpClients(this.clients)
                        .filterToolNames(
                                ExpenseMcpToolCatalog.readToolNames().stream()
                                        .sorted()
                                        .toList())
                        .failIfOneServerFails(true)
                        .build();
    }

    public ToolProvider readOnlyToolProvider() {
        return readOnlyToolProvider;
    }

    public ToolExecutionResult executeReadOnly(
            String toolName, String argumentsJson) {
        ExpenseMcpToolCatalog tool = ExpenseMcpToolCatalog.require(toolName);
        if (tool.access() != ExpenseMcpToolCatalog.Access.READ) {
            throw new SecurityException(
                    "写 Tool 不能通过普通 Agent 入口调用：" + toolName);
        }
        return execute(tool, argumentsJson);
    }

    public ToolExecutionResult executeApprovedWrite(
            ApprovedMcpWriteRequest request) {
        ExpenseMcpToolCatalog tool =
                ExpenseMcpToolCatalog.require(request.toolName());
        if (tool.access() != ExpenseMcpToolCatalog.Access.WRITE) {
            throw new IllegalArgumentException(
                    "已批准写入口只允许调用写 Tool：" + request.toolName());
        }
        return execute(tool, request.argumentsJson());
    }

    private ToolExecutionResult execute(
            ExpenseMcpToolCatalog tool, String argumentsJson) {
        if (argumentsJson == null || argumentsJson.isBlank()) {
            throw new IllegalArgumentException("Tool argumentsJson 不能为空");
        }
        McpClient client = clientsByKey.get(tool.clientKey());
        return client.executeTool(
                ToolExecutionRequest.builder()
                        .name(tool.toolName())
                        .arguments(argumentsJson)
                        .build());
    }

    public void close() {
        RuntimeException failure = null;
        for (McpClient client : clients) {
            try {
                client.close();
            } catch (Exception exception) {
                if (failure == null) {
                    failure =
                            new IllegalStateException(
                                    "关闭 MCP Client 失败", exception);
                } else {
                    failure.addSuppressed(exception);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }
}

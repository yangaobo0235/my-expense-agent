package com.yangaobo.expense.agents.mcp;

import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import java.time.Duration;
import java.util.Map;
import java.util.function.Supplier;

public final class ExpenseMcpClientFactory {

    public static final String PROTOCOL_VERSION = "2025-06-18";

    private ExpenseMcpClientFactory() {}

    public static McpClient create(
            String key,
            String endpoint,
            Supplier<String> accessTokenSupplier,
            Duration connectionTimeout,
            Duration toolTimeout) {
        var transport =
                StreamableHttpMcpTransport.builder()
                        .url(endpoint)
                        .customHeaders(
                                () ->
                                        Map.of(
                                                "Authorization",
                                                "Bearer "
                                                        + accessTokenSupplier
                                                                .get()))
                        .timeout(connectionTimeout)
                        .build();
        return DefaultMcpClient.builder()
                .key(key)
                .clientName("campus-fund-backend")
                .clientVersion("1.0.0")
                .protocolVersion(PROTOCOL_VERSION)
                .transport(transport)
                .initializationTimeout(connectionTimeout)
                .toolExecutionTimeout(toolTimeout)
                .cacheToolList(true)
                .autoHealthCheck(true)
                .build();
    }
}

package com.yangaobo.expense.backend.infrastructure.mcp;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "expense.mcp.client")
public record ExpenseMcpClientProperties(
        boolean enabled,
        String tokenUri,
        String clientId,
        String clientSecret,
        String accountUrl,
        String expenseUrl,
        String auditHistoryUrl,
        Duration connectionTimeout,
        Duration toolTimeout,
        int maxAttempts,
        Duration retryDelay) {

    public ExpenseMcpClientProperties {
        connectionTimeout =
                connectionTimeout == null
                        ? Duration.ofSeconds(10)
                        : connectionTimeout;
        toolTimeout =
                toolTimeout == null ? Duration.ofSeconds(15) : toolTimeout;
        maxAttempts = maxAttempts == 0 ? 2 : maxAttempts;
        retryDelay =
                retryDelay == null
                        ? Duration.ofMillis(200)
                        : retryDelay;
        if (maxAttempts < 1 || maxAttempts > 5) {
            throw new IllegalArgumentException(
                    "expense.mcp.client.max-attempts 必须处于 1 到 5");
        }
        if (enabled) {
            tokenUri = required(tokenUri, "token-uri");
            clientId = required(clientId, "client-id");
            clientSecret = required(clientSecret, "client-secret");
            accountUrl = required(accountUrl, "account-url");
            expenseUrl = required(expenseUrl, "expense-url");
            auditHistoryUrl =
                    required(auditHistoryUrl, "audit-history-url");
        }
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "expense.mcp.client." + field + " 不能为空");
        }
        return value.trim();
    }
}

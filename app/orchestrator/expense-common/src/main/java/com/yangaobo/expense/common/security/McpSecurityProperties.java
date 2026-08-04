package com.yangaobo.expense.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "expense.mcp.security")
public record McpSecurityProperties(String serviceToken) {

    public McpSecurityProperties {
        if (serviceToken == null || serviceToken.isBlank()) {
            throw new IllegalArgumentException("expense.mcp.security.service-token 不能为空");
        }
        serviceToken = serviceToken.trim();
        if (serviceToken.length() < 32) {
            throw new IllegalArgumentException("expense.mcp.security.service-token 至少需要 32 个字符");
        }
    }
}

package com.yangaobo.expense.common.security;

import java.time.Duration;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "expense.mcp.security")
public record McpSecurityProperties(
        String issuerUri,
        String jwkSetUri,
        Duration jwtClockSkew,
        Set<String> audiences) {

    public McpSecurityProperties {
        if (issuerUri == null || issuerUri.isBlank()) {
            throw new IllegalArgumentException(
                    "expense.mcp.security.issuer-uri 不能为空");
        }
        if (jwkSetUri == null || jwkSetUri.isBlank()) {
            throw new IllegalArgumentException(
                    "expense.mcp.security.jwk-set-uri 不能为空");
        }
        if (audiences == null || audiences.isEmpty()) {
            throw new IllegalArgumentException(
                    "expense.mcp.security.audiences 不能为空");
        }
        jwtClockSkew = jwtClockSkew == null ? Duration.ofSeconds(60) : jwtClockSkew;
        audiences = Set.copyOf(audiences);
    }
}

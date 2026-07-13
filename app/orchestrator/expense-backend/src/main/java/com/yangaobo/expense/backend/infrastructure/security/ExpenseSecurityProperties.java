package com.yangaobo.expense.backend.infrastructure.security;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "expense.security")
public record ExpenseSecurityProperties(
        String issuerUri,
        String jwkSetUri,
        Set<String> audiences,
        Duration jwtClockSkew,
        List<String> allowedOrigins) {

    public ExpenseSecurityProperties {
        if (issuerUri == null || issuerUri.isBlank()) {
            throw new IllegalArgumentException("expense.security.issuer-uri 不能为空");
        }
        if (jwkSetUri == null || jwkSetUri.isBlank()) {
            throw new IllegalArgumentException("expense.security.jwk-set-uri 不能为空");
        }
        audiences =
                audiences == null || audiences.isEmpty()
                        ? Set.of("campus-fund-backend")
                        : Set.copyOf(audiences);
        jwtClockSkew = jwtClockSkew == null ? Duration.ofSeconds(60) : jwtClockSkew;
        allowedOrigins =
                allowedOrigins == null || allowedOrigins.isEmpty()
                        ? List.of("http://localhost:25105")
                        : List.copyOf(allowedOrigins);
    }
}

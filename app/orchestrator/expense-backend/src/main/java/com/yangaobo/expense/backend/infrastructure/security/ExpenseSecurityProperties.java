package com.yangaobo.expense.backend.infrastructure.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "expense.security")
public record ExpenseSecurityProperties(List<String> allowedOrigins) {

    public ExpenseSecurityProperties {
        allowedOrigins =
                allowedOrigins == null || allowedOrigins.isEmpty()
                        ? List.of("http://localhost:25105")
                        : List.copyOf(allowedOrigins);
    }
}

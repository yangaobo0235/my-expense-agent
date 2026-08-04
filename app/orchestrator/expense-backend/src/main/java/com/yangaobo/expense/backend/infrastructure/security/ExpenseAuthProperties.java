package com.yangaobo.expense.backend.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "expense.auth")
public record ExpenseAuthProperties(
        String bootstrapUsername,
        String bootstrapPassword,
        String bootstrapDisplayName,
        boolean secureCookie,
        boolean demoUsersEnabled) {

    public ExpenseAuthProperties {
        bootstrapUsername = normalize(bootstrapUsername, "admin");
        bootstrapDisplayName = normalize(bootstrapDisplayName, "系统管理员");
    }

    boolean bootstrapEnabled() {
        return bootstrapPassword != null && !bootstrapPassword.isBlank();
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}

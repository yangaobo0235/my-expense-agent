package com.yangaobo.expense.backend.infrastructure.mcp;

final class ServiceTokenProvider {

    private final String token;

    ServiceTokenProvider(ExpenseMcpClientProperties properties) {
        this.token = properties.serviceToken();
    }

    String accessToken() {
        return token;
    }
}

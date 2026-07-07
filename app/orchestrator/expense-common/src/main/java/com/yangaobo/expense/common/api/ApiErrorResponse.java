package com.yangaobo.expense.common.api;

import java.util.Map;

public record ApiErrorResponse(
        String code,
        String message,
        String requestId,
        Map<String, Object> details) {

    public ApiErrorResponse {
        details = details == null ? Map.of() : Map.copyOf(details);
    }
}

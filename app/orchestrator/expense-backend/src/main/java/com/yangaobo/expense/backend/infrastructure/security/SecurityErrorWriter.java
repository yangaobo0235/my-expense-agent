package com.yangaobo.expense.backend.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangaobo.expense.common.api.ApiErrorResponse;
import com.yangaobo.expense.common.error.CampusFundFlowErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

@Component
public class SecurityErrorWriter {

    private final ObjectMapper objectMapper;

    public SecurityErrorWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(
            HttpServletRequest request,
            HttpServletResponse response,
            int status,
            String message)
            throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(
                response.getOutputStream(),
                new ApiErrorResponse(
                        CampusFundFlowErrorCode.ACCESS_DENIED.name(),
                        message,
                        requestId(request),
                        Map.of()));
    }

    private static String requestId(HttpServletRequest request) {
        String requestId = request.getHeader("X-Request-ID");
        return requestId == null || requestId.isBlank()
                ? UUID.randomUUID().toString()
                : requestId;
    }
}

package com.yangaobo.expense.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class McpBearerTokenFilter extends HttpFilter {

    private static final Logger log = LoggerFactory.getLogger(McpBearerTokenFilter.class);

    private final JwtDecoder jwtDecoder;

    McpBearerTokenFilter(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    protected void doFilter(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain)
            throws IOException, ServletException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            unauthorized(response, "MCP endpoint requires a Bearer Token");
            return;
        }

        String token = authorization.substring(7).trim();
        if (token.isEmpty()) {
            unauthorized(response, "MCP endpoint requires a Bearer Token");
            return;
        }

        try {
            Jwt jwt = jwtDecoder.decode(token);
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(new JwtAuthenticationToken(jwt));
            SecurityContextHolder.setContext(context);
            chain.doFilter(request, response);
        } catch (JwtException exception) {
            log.warn("MCP Bearer Token is invalid: {}", exception.getMessage());
            unauthorized(response, "MCP Bearer Token is invalid");
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private static void unauthorized(HttpServletResponse response, String message)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter()
                .write(
                        "{\"code\":\"MCP_UNAUTHORIZED\",\"message\":\""
                                + message
                                + "\"}");
    }
}

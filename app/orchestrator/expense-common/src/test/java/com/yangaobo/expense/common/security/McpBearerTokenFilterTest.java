package com.yangaobo.expense.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;

class McpBearerTokenFilterTest {

    @Test
    void shouldRejectAnonymousRequest() throws Exception {
        McpBearerTokenFilter filter =
                new McpBearerTokenFilter(token -> jwt());
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(new MockHttpServletRequest(), response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader("WWW-Authenticate")).isEqualTo("Bearer");
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void shouldRejectInvalidToken() throws Exception {
        McpBearerTokenFilter filter =
                new McpBearerTokenFilter(
                        token -> {
                            throw new JwtException("invalid");
                        });
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer invalid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void shouldContinueWithValidToken() throws Exception {
        McpBearerTokenFilter filter =
                new McpBearerTokenFilter(token -> jwt());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isSameAs(request);
    }

    private static Jwt jwt() {
        Instant issuedAt = Instant.parse("2026-06-18T00:00:00Z");
        return Jwt.withTokenValue("valid-token")
                .header("alg", "RS256")
                .subject("expense-backend")
                .audience(List.of("account-mcp"))
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plusSeconds(300))
                .build();
    }
}

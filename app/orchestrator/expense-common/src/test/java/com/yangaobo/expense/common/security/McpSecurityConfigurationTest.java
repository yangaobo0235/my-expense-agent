package com.yangaobo.expense.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class McpSecurityConfigurationTest {

    @Test
    void shouldMatchMcpServletRequests() {
        assertThat(matches("", "/mcp")).isTrue();
        assertThat(matches("", "/mcp/session-id")).isTrue();
        assertThat(matches("/services", "/services/mcp")).isTrue();
    }

    @Test
    void shouldIgnoreOtherEndpoints() {
        assertThat(matches("", "/actuator/health")).isFalse();
        assertThat(matches("", "/mcp-tools")).isFalse();
    }

    private static boolean matches(String contextPath, String requestUri) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContextPath(contextPath);
        request.setRequestURI(requestUri);
        return McpSecurityConfiguration.MCP_ENDPOINT.matches(request);
    }
}

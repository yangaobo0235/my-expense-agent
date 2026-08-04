package com.yangaobo.expense.common.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.util.matcher.RequestMatcher;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(McpSecurityProperties.class)
public class McpSecurityConfiguration {

    static final RequestMatcher MCP_ENDPOINT = request -> {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return path.equals("/mcp") || path.startsWith("/mcp/");
    };

    @Bean
    @Order(1)
    SecurityFilterChain mcpSecurityFilterChain(
            HttpSecurity http, McpSecurityProperties properties) throws Exception {
        http.securityMatcher(MCP_ENDPOINT)
                .csrf(csrf -> csrf.disable())
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorization -> authorization.anyRequest().authenticated())
                .addFilterBefore(
                        new McpBearerTokenFilter(properties.serviceToken()),
                        AnonymousAuthenticationFilter.class);
        return http.build();
    }
}

package com.yangaobo.expense.common.security;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(McpSecurityProperties.class)
public class McpSecurityConfiguration {

    @Bean
    @Order(1)
    SecurityFilterChain mcpSecurityFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/mcp", "/mcp/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(
                        session ->
                                session.sessionCreationPolicy(
                                        SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(
                        authorization -> authorization.anyRequest().authenticated())
                .oauth2ResourceServer(resourceServer -> resourceServer.jwt(jwt -> {}));
        return http.build();
    }

    @Bean
    JwtDecoder mcpJwtDecoder(McpSecurityProperties properties) {
        NimbusJwtDecoder decoder =
                NimbusJwtDecoder.withJwkSetUri(properties.jwkSetUri()).build();
        OAuth2TokenValidator<Jwt> issuerValidator =
                new JwtIssuerValidator(properties.issuerUri());
        JwtTimestampValidator timestampValidator =
                new JwtTimestampValidator(properties.jwtClockSkew());
        decoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(
                        timestampValidator,
                        issuerValidator,
                        new McpAudienceValidator(properties.audiences())));
        return decoder;
    }

    @Bean
    FilterRegistrationBean<McpBearerTokenFilter> mcpBearerTokenFilterRegistration(
            JwtDecoder mcpJwtDecoder) {
        FilterRegistrationBean<McpBearerTokenFilter> registration =
                new FilterRegistrationBean<>();
        registration.setFilter(new McpBearerTokenFilter(mcpJwtDecoder));
        registration.setName("mcp-bearer-token-filter");
        registration.addUrlPatterns("/mcp", "/mcp/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}

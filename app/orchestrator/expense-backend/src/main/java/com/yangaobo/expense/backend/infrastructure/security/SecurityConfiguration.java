package com.yangaobo.expense.backend.infrastructure.security;

import jakarta.servlet.http.HttpServletResponse;
import java.util.Collection;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableConfigurationProperties(ExpenseSecurityProperties.class)
public class SecurityConfiguration {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            SecurityErrorWriter errorWriter,
            Converter<Jwt, AbstractAuthenticationToken> authenticationConverter)
            throws Exception {
        http.csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(
                        session ->
                                session.sessionCreationPolicy(
                                        SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(
                        authorization ->
                                authorization
                                        .requestMatchers(
                                                "/api/v1/system",
                                                "/v3/api-docs/**",
                                                "/swagger-ui/**",
                                                "/swagger-ui.html",
                                                "/actuator/health/**",
                                                "/actuator/info",
                                                "/actuator/prometheus")
                                        .permitAll()
                                        .requestMatchers(
                                                org.springframework.http.HttpMethod.POST,
                                                "/api/v1/policies")
                                        .hasRole("FINANCE_ADMIN")
                                        .requestMatchers(
                                                org.springframework.http.HttpMethod.GET,
                                                "/api/v1/policies",
                                                "/api/v1/policies/search")
                                        .hasAnyRole(
                                                "COLLEGE_REVIEWER",
                                                "FINANCE_ADMIN",
                                                "AUDITOR")
                                        .requestMatchers(
                                                org.springframework.http.HttpMethod.GET,
                                                "/api/v1/review-tasks/**")
                                        .hasAnyRole(
                                                "ADVISOR",
                                                "COLLEGE_REVIEWER",
                                                "FINANCE_ADMIN",
                                                "AUDITOR")
                                        .requestMatchers(
                                                org.springframework.http.HttpMethod.POST,
                                                "/api/v1/review-tasks/**")
                                        .hasAnyRole(
                                                "ADVISOR",
                                                "COLLEGE_REVIEWER",
                                                "FINANCE_ADMIN")
                                        .requestMatchers("/api/v1/evaluation/**")
                                        .hasAnyRole(
                                                "COLLEGE_REVIEWER",
                                                "FINANCE_ADMIN",
                                                "AUDITOR")
                                        .requestMatchers("/api/v1/observability/**")
                                        .hasAnyRole(
                                                "COLLEGE_REVIEWER",
                                                "FINANCE_ADMIN",
                                                "AUDITOR")
                                        .requestMatchers(
                                                org.springframework.http.HttpMethod.GET,
                                                "/api/v1/prompts/**")
                                        .hasAnyRole(
                                                "COLLEGE_REVIEWER",
                                                "FINANCE_ADMIN",
                                                "PROMPT_AUTHOR",
                                                "PROMPT_REVIEWER",
                                                "PROMPT_PUBLISHER",
                                                "AUDITOR")
                                        .requestMatchers(
                                                org.springframework.http.HttpMethod.POST,
                                                "/api/v1/prompts/changes/*/approve",
                                                "/api/v1/prompts/changes/*/reject")
                                        .hasAnyRole("PROMPT_REVIEWER", "FINANCE_ADMIN")
                                        .requestMatchers(
                                                org.springframework.http.HttpMethod.POST,
                                                "/api/v1/prompts/*/activate")
                                        .hasAnyRole("PROMPT_PUBLISHER", "FINANCE_ADMIN")
                                        .requestMatchers(
                                                org.springframework.http.HttpMethod.POST,
                                                "/api/v1/prompts",
                                                "/api/v1/prompts/*/submit")
                                        .hasAnyRole("PROMPT_AUTHOR", "FINANCE_ADMIN")
                                        .requestMatchers(
                                                org.springframework.http.HttpMethod.PUT,
                                                "/api/v1/prompts/**")
                                        .hasAnyRole("PROMPT_AUTHOR", "FINANCE_ADMIN")
                                        .requestMatchers("/api/v1/prompts/**")
                                        .hasAnyRole(
                                                "PROMPT_AUTHOR",
                                                "PROMPT_REVIEWER",
                                                "PROMPT_PUBLISHER",
                                                "FINANCE_ADMIN")
                                        .requestMatchers(
                                                org.springframework.http.HttpMethod.POST,
                                                "/api/v1/fund-applications/*/posting")
                                        .hasRole("FINANCE_ADMIN")
                                        .requestMatchers(
                                                org.springframework.http.HttpMethod.GET,
                                                "/api/v1/fund-applications/**")
                                        .hasAnyRole(
                                                "STUDENT",
                                                "ADVISOR",
                                                "COLLEGE_REVIEWER",
                                                "FINANCE_ADMIN",
                                                "AUDITOR")
                                        .requestMatchers("/api/v1/fund-applications/**")
                                        .hasAnyRole(
                                                "STUDENT",
                                                "ADVISOR",
                                                "COLLEGE_REVIEWER",
                                                "FINANCE_ADMIN")
                                        .anyRequest()
                                        .authenticated())
                .exceptionHandling(
                        exceptions ->
                                exceptions
                                        .authenticationEntryPoint(
                                                (request, response, exception) ->
                                                        errorWriter.write(
                                                                request,
                                                                response,
                                                                HttpServletResponse
                                                                        .SC_UNAUTHORIZED,
                                                                "需要有效的 Bearer Token"))
                                        .accessDeniedHandler(
                                                (request, response, exception) ->
                                                        errorWriter.write(
                                                                request,
                                                                response,
                                                                HttpServletResponse.SC_FORBIDDEN,
                                                                "当前用户没有访问权限")))
                .oauth2ResourceServer(
                        resourceServer ->
                                resourceServer
                                        .jwt(
                                                jwt ->
                                                        jwt.jwtAuthenticationConverter(
                                                                authenticationConverter))
                                        .authenticationEntryPoint(
                                                (request, response, exception) ->
                                                        errorWriter.write(
                                                                request,
                                                                response,
                                                                HttpServletResponse
                                                                        .SC_UNAUTHORIZED,
                                                                "Bearer Token 无效或已过期")));
        return http.build();
    }

    @Bean
    JwtDecoder jwtDecoder(ExpenseSecurityProperties properties) {
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
                        new AudienceValidator(properties.audiences())));
        return decoder;
    }

    @Bean
    Converter<Jwt, AbstractAuthenticationToken> authenticationConverter() {
        KeycloakRealmRoleConverter authoritiesConverter =
                new KeycloakRealmRoleConverter();
        return jwt -> {
            Collection<GrantedAuthority> authorities =
                    authoritiesConverter.convert(jwt);
            String principal =
                    firstNonBlank(
                            jwt.getClaimAsString("preferred_username"),
                            jwt.getClaimAsString("sub"));
            return new JwtAuthenticationToken(jwt, authorities, principal);
        };
    }

    private static String firstNonBlank(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred;
        }
        return fallback;
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(
            ExpenseSecurityProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.allowedOrigins());
        configuration.setAllowedMethods(
                java.util.List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(
                java.util.List.of(
                        "Authorization",
                        "Content-Type",
                        "X-Request-ID",
                        "Last-Event-ID"));
        configuration.setExposedHeaders(
                java.util.List.of("Location", "X-Request-ID"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}

package com.yangaobo.expense.backend.infrastructure.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableConfigurationProperties({ExpenseSecurityProperties.class, ExpenseAuthProperties.class})
public class SecurityConfiguration {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            SecurityErrorWriter errorWriter,
            SecurityContextRepository securityContextRepository,
            CookieCsrfTokenRepository csrfRepository)
            throws Exception {
        CsrfTokenRequestAttributeHandler csrfRequestHandler =
                new CsrfTokenRequestAttributeHandler();
        csrfRequestHandler.setCsrfRequestAttributeName(null);

        http.cors(Customizer.withDefaults())
                .csrf(
                        csrf ->
                                csrf.csrfTokenRepository(csrfRepository)
                                        .csrfTokenRequestHandler(csrfRequestHandler))
                .securityContext(
                        context ->
                                context.securityContextRepository(securityContextRepository)
                                        .requireExplicitSave(true))
                .logout(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(
                        authorization ->
                                authorization
                                        .requestMatchers(
                                                "/v3/api-docs/**",
                                                "/swagger-ui/**",
                                                "/swagger-ui.html",
                                                "/actuator/health/**",
                                                "/actuator/info",
                                                "/api/v1/auth/login",
                                                "/api/v1/auth/session")
                                        .permitAll()
                                        .requestMatchers(
                                                HttpMethod.POST,
                                                "/api/v1/expense-cases/*/more-info-requests")
                                        .hasAnyRole("COLLEGE_REVIEWER", "FINANCE_ADMIN")
                                        .requestMatchers(
                                                HttpMethod.POST,
                                                "/api/v1/expense-cases/*/more-info-submissions",
                                                "/api/v1/expense-cases/*/review-runs")
                                        .hasAnyRole("STUDENT", "ADVISOR")
                                        .requestMatchers(
                                                HttpMethod.GET,
                                                "/api/v1/expense-cases/*/more-info-request",
                                                "/api/v1/expense-cases/*/review-runs",
                                                "/api/v1/expense-cases/*/document-versions")
                                        .hasAnyRole(
                                                "STUDENT",
                                                "ADVISOR",
                                                "COLLEGE_REVIEWER",
                                                "FINANCE_ADMIN",
                                                "AUDITOR")
                                        .requestMatchers(HttpMethod.POST, "/api/v1/policies")
                                        .hasRole("FINANCE_ADMIN")
                                        .requestMatchers(
                                                HttpMethod.GET,
                                                "/api/v1/policies",
                                                "/api/v1/policies/search")
                                        .hasAnyRole(
                                                "COLLEGE_REVIEWER",
                                                "FINANCE_ADMIN",
                                                "AUDITOR")
                                        .requestMatchers(HttpMethod.GET, "/api/v1/review-tasks/**")
                                        .hasAnyRole(
                                                "ADVISOR",
                                                "COLLEGE_REVIEWER",
                                                "FINANCE_ADMIN",
                                                "AUDITOR")
                                        .requestMatchers(HttpMethod.POST, "/api/v1/review-tasks/**")
                                        .hasAnyRole(
                                                "ADVISOR", "COLLEGE_REVIEWER", "FINANCE_ADMIN")
                                        .requestMatchers("/api/v1/evaluations/**")
                                        .hasAnyRole(
                                                "COLLEGE_REVIEWER",
                                                "FINANCE_ADMIN",
                                                "AUDITOR")
                                        .requestMatchers(
                                                HttpMethod.POST,
                                                "/api/v1/fund-applications/*/posting")
                                        .hasRole("FINANCE_ADMIN")
                                        .requestMatchers(HttpMethod.GET, "/api/v1/fund-applications/**")
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
                                                                HttpServletResponse.SC_UNAUTHORIZED,
                                                                "请先登录"))
                                        .accessDeniedHandler(
                                                (request, response, exception) ->
                                                        errorWriter.write(
                                                                request,
                                                                response,
                                                                HttpServletResponse.SC_FORBIDDEN,
                                                                "当前用户没有访问权限")));
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    AuthenticationManager authenticationManager(
            UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    SessionAuthenticationStrategy sessionAuthenticationStrategy() {
        return new ChangeSessionIdAuthenticationStrategy();
    }

    @Bean
    CookieCsrfTokenRepository csrfTokenRepository(ExpenseAuthProperties properties) {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieCustomizer(
                cookie ->
                        cookie.path("/")
                                .sameSite("Lax")
                                .secure(properties.secureCookie()));
        return repository;
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(ExpenseSecurityProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.allowedOrigins());
        configuration.setAllowedMethods(
                java.util.List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(
                java.util.List.of(
                        "Content-Type", "X-Request-ID", "Last-Event-ID", "X-XSRF-TOKEN"));
        configuration.setExposedHeaders(java.util.List.of("Location", "X-Request-ID"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}

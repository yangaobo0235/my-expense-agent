package com.yangaobo.expense.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class McpAudienceValidatorTest {

    private final McpAudienceValidator validator =
            new McpAudienceValidator(Set.of("account-mcp"));

    @Test
    void shouldAcceptDedicatedAudience() {
        assertThat(validator.validate(jwt(List.of("account-mcp"))).hasErrors())
                .isFalse();
    }

    @Test
    void shouldAcceptWhenDedicatedAudienceIsOneOfSeveralValues() {
        assertThat(
                        validator.validate(
                                        jwt(
                                                List.of(
                                                        "expense-backend",
                                                        "account-mcp")))
                                .hasErrors())
                .isFalse();
    }

    @Test
    void shouldRejectAnotherMcpAudience() {
        assertThat(validator.validate(jwt(List.of("expense-mcp"))).hasErrors())
                .isTrue();
    }

    @Test
    void shouldRejectMissingAudience() {
        assertThat(validator.validate(jwt(List.of())).hasErrors()).isTrue();
    }

    private static Jwt jwt(List<String> audiences) {
        Instant issuedAt = Instant.parse("2026-06-18T00:00:00Z");
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("expense-backend")
                .audience(audiences)
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plusSeconds(300))
                .build();
    }
}

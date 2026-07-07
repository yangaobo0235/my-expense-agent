package com.yangaobo.expense.backend.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class AudienceValidatorTest {

    private final AudienceValidator validator =
            new AudienceValidator(Set.of("expense-backend"));

    @Test
    void shouldAcceptExpectedAudience() {
        assertThat(validator.validate(jwt(List.of("expense-backend"))).hasErrors())
                .isFalse();
    }

    @Test
    void shouldRejectUnexpectedAudience() {
        assertThat(validator.validate(jwt(List.of("account"))).hasErrors()).isTrue();
    }

    private static Jwt jwt(List<String> audience) {
        return new Jwt(
                "token",
                Instant.parse("2026-06-18T00:00:00Z"),
                Instant.parse("2026-06-18T00:05:00Z"),
                Map.of("alg", "RS256"),
                Map.of("sub", "subject", "aud", audience));
    }
}

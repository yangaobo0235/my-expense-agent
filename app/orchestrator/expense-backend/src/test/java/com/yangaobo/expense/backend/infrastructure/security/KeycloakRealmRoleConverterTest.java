package com.yangaobo.expense.backend.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class KeycloakRealmRoleConverterTest {

    private final KeycloakRealmRoleConverter converter =
            new KeycloakRealmRoleConverter();

    @Test
    void shouldConvertRealmRolesAndScopes() {
        Jwt jwt =
                new Jwt(
                        "token",
                        Instant.parse("2026-06-18T00:00:00Z"),
                        Instant.parse("2026-06-18T00:05:00Z"),
                        Map.of("alg", "RS256"),
                        Map.of(
                                "sub", "employee-subject",
                                "scope", "profile email",
                                "realm_access",
                                        Map.of(
                                                "roles",
                                                List.of("EMPLOYEE", "offline_access"))));

        assertThat(converter.convert(jwt))
                .extracting(authority -> authority.getAuthority())
                .contains(
                        "SCOPE_profile",
                        "SCOPE_email",
                        "ROLE_EMPLOYEE",
                        "ROLE_offline_access");
    }
}

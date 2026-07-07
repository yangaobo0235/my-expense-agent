package com.yangaobo.expense.backend.infrastructure.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ClientCredentialsTokenProviderTest {

    @Test
    void shouldRequestAndCacheClientCredentialsToken() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server =
                MockRestServiceServer.bindTo(builder).build();
        server.expect(
                        requestTo(
                                "http://keycloak/realms/expense-flow/token"))
                .andExpect(
                        content()
                                .formDataContains(
                                        java.util.Map.of(
                                                "grant_type",
                                                "client_credentials",
                                                "client_id",
                                                "expense-backend",
                                                "client_secret",
                                                "secret")))
                .andRespond(
                        withSuccess(
                                """
                                {"access_token":"token-1","expires_in":300}
                                """,
                                MediaType.APPLICATION_JSON));
        ExpenseMcpClientProperties properties =
                new ExpenseMcpClientProperties(
                        true,
                        "http://keycloak/realms/expense-flow/token",
                        "expense-backend",
                        "secret",
                        "http://localhost:25102/mcp",
                        "http://localhost:25103/mcp",
                        "http://localhost:25104/mcp",
                        Duration.ofSeconds(10),
                        Duration.ofSeconds(15),
                        2,
                        Duration.ZERO);
        ClientCredentialsTokenProvider provider =
                new ClientCredentialsTokenProvider(
                        builder.build(),
                        properties,
                        Clock.fixed(
                                Instant.parse("2026-06-19T00:00:00Z"),
                                ZoneOffset.UTC));

        assertThat(provider.accessToken()).isEqualTo("token-1");
        assertThat(provider.accessToken()).isEqualTo("token-1");
        server.verify();
    }
}

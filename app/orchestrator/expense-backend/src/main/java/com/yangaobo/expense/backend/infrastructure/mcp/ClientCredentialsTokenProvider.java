package com.yangaobo.expense.backend.infrastructure.mcp;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

final class ClientCredentialsTokenProvider {

    private static final Duration REFRESH_SKEW = Duration.ofSeconds(30);

    private final RestClient restClient;
    private final ExpenseMcpClientProperties properties;
    private final Clock clock;
    private volatile CachedToken cachedToken;

    ClientCredentialsTokenProvider(
            RestClient restClient,
            ExpenseMcpClientProperties properties,
            Clock clock) {
        this.restClient = restClient;
        this.properties = properties;
        this.clock = clock;
    }

    String accessToken() {
        CachedToken current = cachedToken;
        Instant now = clock.instant();
        if (current != null
                && now.isBefore(current.expiresAt().minus(REFRESH_SKEW))) {
            return current.value();
        }
        synchronized (this) {
            current = cachedToken;
            now = clock.instant();
            if (current != null
                    && now.isBefore(
                            current.expiresAt().minus(REFRESH_SKEW))) {
                return current.value();
            }
            TokenResponse response = requestToken();
            long expiresIn = Math.max(response.expiresIn(), 1);
            cachedToken =
                    new CachedToken(
                            response.accessToken(),
                            now.plusSeconds(expiresIn));
            return cachedToken.value();
        }
    }

    private TokenResponse requestToken() {
        var form = new LinkedMultiValueMap<String, String>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());
        TokenResponse response =
                restClient
                        .post()
                        .uri(properties.tokenUri())
                        .contentType(
                                MediaType.APPLICATION_FORM_URLENCODED)
                        .body(form)
                        .retrieve()
                        .body(TokenResponse.class);
        if (response == null
                || response.accessToken() == null
                || response.accessToken().isBlank()) {
            throw new IllegalStateException("Keycloak 未返回 access_token");
        }
        return response;
    }

    private record CachedToken(String value, Instant expiresAt) {}

    private record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("expires_in") long expiresIn) {}
}

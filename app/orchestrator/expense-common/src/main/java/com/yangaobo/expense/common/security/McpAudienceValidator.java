package com.yangaobo.expense.common.security;

import java.util.Set;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

public final class McpAudienceValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error INVALID_AUDIENCE =
            new OAuth2Error(
                    "invalid_token",
                    "JWT audience 不允许访问当前 MCP 服务",
                    null);

    private final Set<String> allowedAudiences;

    public McpAudienceValidator(Set<String> allowedAudiences) {
        if (allowedAudiences == null || allowedAudiences.isEmpty()) {
            throw new IllegalArgumentException("MCP JWT audience 不能为空");
        }
        this.allowedAudiences = Set.copyOf(allowedAudiences);
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        boolean accepted =
                jwt.getAudience().stream().anyMatch(allowedAudiences::contains);
        return accepted
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(INVALID_AUDIENCE);
    }
}

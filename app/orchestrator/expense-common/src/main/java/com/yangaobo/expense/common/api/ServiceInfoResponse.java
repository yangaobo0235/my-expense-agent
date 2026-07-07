package com.yangaobo.expense.common.api;

import java.time.Instant;

public record ServiceInfoResponse(String service, String version, Instant time) {

    public static ServiceInfoResponse of(String serviceName, Class<?> sourceClass) {
        String version = sourceClass.getPackage().getImplementationVersion();
        return new ServiceInfoResponse(
                serviceName,
                version == null ? "development" : version,
                Instant.now());
    }
}

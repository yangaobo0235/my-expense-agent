package com.yangaobo.expense.backend.infrastructure.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "expense.minio")
public record MinioProperties(
        String endpoint, String accessKey, String secretKey, String bucket) {}

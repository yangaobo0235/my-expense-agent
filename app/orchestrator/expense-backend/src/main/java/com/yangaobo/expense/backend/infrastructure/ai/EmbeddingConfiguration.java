package com.yangaobo.expense.backend.infrastructure.ai;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(DashScopeEmbeddingProperties.class)
public class EmbeddingConfiguration {}

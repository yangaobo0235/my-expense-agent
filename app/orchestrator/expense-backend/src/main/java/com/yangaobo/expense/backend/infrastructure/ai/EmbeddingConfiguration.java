package com.yangaobo.expense.backend.infrastructure.ai;

import com.yangaobo.expense.backend.application.ai.ChatModelProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({DashScopeEmbeddingProperties.class, ChatModelProperties.class})
public class EmbeddingConfiguration {}

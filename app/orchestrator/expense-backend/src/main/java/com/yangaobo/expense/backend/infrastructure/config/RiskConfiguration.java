package com.yangaobo.expense.backend.infrastructure.config;

import com.yangaobo.expense.backend.domain.risk.DeterministicRiskEngine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RiskConfiguration {

    @Bean
    DeterministicRiskEngine deterministicRiskEngine() {
        return new DeterministicRiskEngine();
    }
}

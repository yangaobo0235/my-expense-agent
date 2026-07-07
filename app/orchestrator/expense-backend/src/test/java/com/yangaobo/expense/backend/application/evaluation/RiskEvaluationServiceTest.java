package com.yangaobo.expense.backend.application.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yangaobo.expense.backend.domain.risk.DeterministicRiskEngine;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class RiskEvaluationServiceTest {

    @Test
    void shouldProduceReproducibleRiskBaseline() {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        RiskEvaluationService service =
                new RiskEvaluationService(
                        objectMapper,
                        new DeterministicRiskEngine(),
                        Clock.fixed(
                                Instant.parse("2026-06-22T00:00:00Z"),
                                ZoneOffset.UTC),
                        "classpath:evaluation/cases/risk-golden-v1.json");

        RiskEvaluationReport report = service.evaluate();

        assertThat(report.datasetVersion()).isEqualTo("risk-golden-v1");
        assertThat(report.caseCount()).isEqualTo(100);
        assertThat(report.categoryCounts())
                .containsEntry("正常案例", 30)
                .containsEntry("超标案例", 20)
                .containsEntry("缺票案例", 10)
                .containsEntry("重复票据", 10)
                .containsEntry("金额不一致", 10)
                .containsEntry("制度边界", 10)
                .containsEntry("提示注入", 5)
                .containsEntry("低质量图片", 5);
        assertThat(report.metrics().precision()).isEqualTo(1.0);
        assertThat(report.metrics().recall()).isEqualTo(1.0);
        assertThat(report.metrics().f1()).isEqualTo(1.0);
        assertThat(report.metrics().highRiskMissRate()).isZero();
        assertThat(report.metrics().humanReviewTriggerRate()).isEqualTo(0.6);
        assertThat(report.agentGovernance().planVersion())
                .isEqualTo("expenseflow-multi-agent-v1");
        assertThat(report.agentGovernance().writeToolIsolationPassed()).isTrue();
        assertThat(report.agentGovernance().settlementWriteRetryProtected()).isTrue();
        assertThat(report.agentGovernance().humanHandoffCoverage()).isEqualTo(1.0);
        assertThat(report.agentGovernance().retryableAgentRate()).isEqualTo(0.5);
        assertThat(report.failures()).isEmpty();
        assertThat(report.datasetSha256()).hasSize(64);
    }
}

package com.yangaobo.expense.backend.application.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import com.yangaobo.expense.backend.domain.model.RiskLevel;
import com.yangaobo.expense.backend.domain.risk.RiskAssessment;
import com.yangaobo.expense.backend.domain.risk.RiskSignal;
import com.yangaobo.expense.backend.domain.risk.RiskSignalCode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RiskRoutingDecisionTest {

    @Test
    void shouldRouteCampusReviewLevelsToDistinctRoles() {
        assertThat(route(RiskSignalCode.LOW_EXTRACTION_CONFIDENCE, 50).assigneeRole())
                .isEqualTo("ADVISOR");
        assertThat(route(RiskSignalCode.MISSING_REQUIRED_DOCUMENT, 50).assigneeRole())
                .isEqualTo("ADVISOR");
        assertThat(route(RiskSignalCode.POLICY_LIMIT_EXCEEDED, 50).assigneeRole())
                .isEqualTo("COLLEGE_REVIEWER");
        assertThat(route(RiskSignalCode.DUPLICATE_DOCUMENT, 80).assigneeRole())
                .isEqualTo("FINANCE_ADMIN");
        assertThat(route(RiskSignalCode.DEPENDENCY_UNAVAILABLE, 50).assigneeRole())
                .isEqualTo("COLLEGE_REVIEWER");
    }

    @Test
    void shouldGiveFraudAndHighRiskSignalsPriorityOverLowerReviewQueues() {
        assertThat(
                        route(
                                        100,
                                        RiskSignalCode.DUPLICATE_DOCUMENT,
                                        RiskSignalCode.MISSING_REQUIRED_DOCUMENT,
                                        RiskSignalCode.POLICY_EVIDENCE_MISSING)
                                .assigneeRole())
                .isEqualTo("FINANCE_ADMIN");
        assertThat(
                        route(
                                        65,
                                        RiskSignalCode.MISSING_REQUIRED_DOCUMENT,
                                        RiskSignalCode.POLICY_LIMIT_EXCEEDED)
                                .assigneeRole())
                .isEqualTo("FINANCE_ADMIN");
    }

    @Test
    void shouldGiveCollegeConflictsPriorityOverMissingInformation() {
        assertThat(
                        route(
                                        50,
                                        RiskSignalCode.DEPENDENCY_UNAVAILABLE,
                                        RiskSignalCode.MISSING_REQUIRED_DOCUMENT)
                                .assigneeRole())
                .isEqualTo("COLLEGE_REVIEWER");
        assertThat(
                        route(
                                        50,
                                        RiskSignalCode.POLICY_EVIDENCE_MISSING,
                                        RiskSignalCode.MISSING_REQUIRED_DOCUMENT)
                                .assigneeRole())
                .isEqualTo("COLLEGE_REVIEWER");
    }

    @Test
    void shouldNeverAutoApproveLowConfidenceOrDateAnomalySignals() {
        assertThat(route(25, RiskSignalCode.LOW_EXTRACTION_CONFIDENCE).assigneeRole())
                .isEqualTo("ADVISOR");
        assertThat(route(20, RiskSignalCode.DATE_ANOMALY).assigneeRole())
                .isEqualTo("ADVISOR");
    }

    private static RiskRoutingDecision route(RiskSignalCode code, int score) {
        return route(score, code);
    }

    private static RiskRoutingDecision route(int score, RiskSignalCode... codes) {
        RiskAssessment risk =
                new RiskAssessment(
                        score,
                        RiskLevel.fromScore(score),
                        score >= 30,
                        java.util.Arrays.stream(codes)
                                .map(code -> new RiskSignal(code, 10, "测试风险信号", Map.of()))
                                .toList());
        return RiskRoutingDecision.from(risk);
    }
}

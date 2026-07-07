package com.yangaobo.expense.backend.domain.risk;

import static org.assertj.core.api.Assertions.assertThat;

import com.yangaobo.expense.backend.domain.model.RiskLevel;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class DeterministicRiskEngineTest {

    private final DeterministicRiskEngine engine = new DeterministicRiskEngine();

    @Test
    void shouldKeepCleanExpenseAtLowRisk() {
        RiskAssessment result =
                engine.assess(
                        input(
                                "600.00",
                                "600.00",
                                0.95,
                                false,
                                false,
                                false,
                                false,
                                false,
                                false));

        assertThat(result.score()).isZero();
        assertThat(result.level()).isEqualTo(RiskLevel.LOW);
        assertThat(result.requiresHumanReview()).isFalse();
        assertThat(result.signals()).isEmpty();
    }

    @Test
    void shouldTreatOneCentAsAllowedRoundingTolerance() {
        RiskAssessment result =
                engine.assess(
                        input(
                                "100.00",
                                "99.99",
                                0.90,
                                false,
                                false,
                                false,
                                false,
                                false,
                                false));

        assertThat(result.signals())
                .extracting(RiskSignal::code)
                .doesNotContain(RiskSignalCode.AMOUNT_MISMATCH);
    }

    @Test
    void shouldExplainAmountMismatchAndLowConfidence() {
        RiskAssessment result =
                engine.assess(
                        input(
                                "800.00",
                                "600.00",
                                0.60,
                                false,
                                false,
                                false,
                                false,
                                false,
                                false));

        assertThat(result.score()).isEqualTo(55);
        assertThat(result.level()).isEqualTo(RiskLevel.MEDIUM);
        assertThat(result.requiresHumanReview()).isTrue();
        assertThat(result.signals())
                .extracting(RiskSignal::code)
                .containsExactly(
                        RiskSignalCode.AMOUNT_MISMATCH,
                        RiskSignalCode.LOW_EXTRACTION_CONFIDENCE);
        assertThat(result.signals().getFirst().evidence())
                .containsEntry("difference", "200.00");
    }

    @Test
    void shouldCapCombinedRiskAtOneHundred() {
        RiskAssessment result =
                engine.assess(
                        input(
                                "1000.00",
                                "100.00",
                                0.20,
                                true,
                                true,
                                true,
                                true,
                                true,
                                true));

        assertThat(result.score()).isEqualTo(100);
        assertThat(result.level()).isEqualTo(RiskLevel.HIGH);
        assertThat(result.signals()).hasSize(8);
    }

    private static RiskAssessmentInput input(
            String claimed,
            String extracted,
            double confidence,
            boolean duplicate,
            boolean dateAnomaly,
            boolean sellerAnomaly,
            boolean policyLimitExceeded,
            boolean missingDocument,
            boolean forbiddenItem) {
        return new RiskAssessmentInput(
                new BigDecimal(claimed),
                new BigDecimal(extracted),
                confidence,
                duplicate,
                dateAnomaly,
                sellerAnomaly,
                policyLimitExceeded,
                missingDocument,
                forbiddenItem);
    }
}

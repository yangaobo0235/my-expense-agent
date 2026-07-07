package com.yangaobo.expense.backend.domain.risk;

import com.yangaobo.expense.backend.domain.model.RiskLevel;
import java.util.List;
import java.util.Objects;

public record RiskAssessment(
        int score,
        RiskLevel level,
        boolean requiresHumanReview,
        List<RiskSignal> signals) {

    public RiskAssessment {
        if (score < 0 || score > 100) {
            throw new IllegalArgumentException("风险分必须处于 0 到 100");
        }
        Objects.requireNonNull(level, "level");
        if (RiskLevel.fromScore(score) != level) {
            throw new IllegalArgumentException("风险等级与风险分不一致");
        }
        signals = signals == null ? List.of() : List.copyOf(signals);
    }
}

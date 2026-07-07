package com.yangaobo.expense.backend.domain.risk;

import java.util.Map;
import java.util.Objects;

public record RiskSignal(
        RiskSignalCode code,
        int score,
        String message,
        Map<String, String> evidence) {

    public RiskSignal {
        Objects.requireNonNull(code, "code");
        if (score <= 0 || score > 100) {
            throw new IllegalArgumentException("风险信号分值必须处于 1 到 100");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("风险信号说明不能为空");
        }
        evidence = evidence == null ? Map.of() : Map.copyOf(evidence);
    }
}

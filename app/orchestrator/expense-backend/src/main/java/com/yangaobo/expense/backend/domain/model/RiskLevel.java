package com.yangaobo.expense.backend.domain.model;

public enum RiskLevel {
    LOW,
    MEDIUM,
    HIGH;

    public static RiskLevel fromScore(int score) {
        if (score < 0 || score > 100) {
            throw new IllegalArgumentException("Risk score must be between 0 and 100");
        }
        if (score < 30) {
            return LOW;
        }
        if (score < 60) {
            return MEDIUM;
        }
        return HIGH;
    }
}

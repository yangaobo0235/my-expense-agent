package com.yangaobo.expense.common.domain;

public enum ExpenseCaseStatus {
    DRAFT,
    UPLOADED,
    EXTRACTING,
    EXTRACTED,
    POLICY_CHECKING,
    RISK_CHECKING,
    WAITING_MORE_INFO,
    WAITING_HUMAN,
    APPROVED,
    REJECTED,
    FAILED;

    public boolean isTerminal() {
        return this == APPROVED || this == REJECTED;
    }
}

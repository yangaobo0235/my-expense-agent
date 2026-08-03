package com.yangaobo.expense.backend.application.settlement;

public enum SettlementWriteStatus {
    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED_RETRYABLE,
    FAILED_MANUAL,
    COMPENSATED
}

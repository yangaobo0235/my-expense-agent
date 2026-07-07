package com.yangaobo.expense.backend.interfaces.rest;

import com.yangaobo.expense.backend.domain.model.ExpenseCase;
import com.yangaobo.expense.common.domain.ExpenseCaseStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ExpenseCaseResponse(
        UUID id,
        String caseNumber,
        String applicantName,
        String departmentCode,
        String title,
        BigDecimal claimedAmount,
        String currency,
        ExpenseCaseStatus status,
        String riskLevel,
        Integer riskScore,
        String failureStage,
        String failureReason,
        String settlementStatus,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    static ExpenseCaseResponse from(ExpenseCase expenseCase) {
        return from(expenseCase, "NOT_SUBMITTED");
    }

    static ExpenseCaseResponse from(ExpenseCase expenseCase, String settlementStatus) {
        return new ExpenseCaseResponse(
                expenseCase.id(),
                expenseCase.caseNumber(),
                expenseCase.applicantName(),
                expenseCase.departmentCode(),
                expenseCase.title(),
                expenseCase.claimedAmount().amount(),
                expenseCase.claimedAmount().currency(),
                expenseCase.status(),
                expenseCase.riskLevel() == null ? null : expenseCase.riskLevel().name(),
                expenseCase.riskScore(),
                expenseCase.failureStage(),
                expenseCase.failureReason(),
                settlementStatus,
                expenseCase.version(),
                expenseCase.createdAt(),
                expenseCase.updatedAt());
    }
}

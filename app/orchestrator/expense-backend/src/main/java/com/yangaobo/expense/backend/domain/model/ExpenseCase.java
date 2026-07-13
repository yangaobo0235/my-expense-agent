package com.yangaobo.expense.backend.domain.model;

import com.yangaobo.expense.common.domain.ExpenseCaseStateMachine;
import com.yangaobo.expense.common.domain.ExpenseCaseStatus;
import com.yangaobo.expense.common.error.CampusFundFlowErrorCode;
import com.yangaobo.expense.common.error.CampusFundFlowException;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ExpenseCase(
        UUID id,
        String caseNumber,
        String ownerSubject,
        String applicantName,
        String projectCode,
        String title,
        Money claimedAmount,
        ExpenseCaseStatus status,
        RiskLevel riskLevel,
        Integer riskScore,
        String failureStage,
        String failureReason,
        long version,
        Instant createdAt,
        Instant updatedAt)
        implements Serializable {

    public ExpenseCase {
        Objects.requireNonNull(id, "id");
        caseNumber = required(caseNumber, "caseNumber", 32);
        ownerSubject = required(ownerSubject, "ownerSubject", 128);
        applicantName = required(applicantName, "applicantName", 128);
        projectCode = required(projectCode, "projectCode", 64);
        title = required(title, "title", 256);
        Objects.requireNonNull(claimedAmount, "claimedAmount");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (version < 0) {
            throw validation("Version must not be negative");
        }
        if ((riskScore == null) != (riskLevel == null)) {
            throw validation("Risk level and score must be set together");
        }
        if (riskScore != null
                && (riskScore < 0
                        || riskScore > 100
                        || RiskLevel.fromScore(riskScore) != riskLevel)) {
            throw validation("Risk level does not match risk score");
        }
    }

    public static ExpenseCase create(
            UUID id,
            String caseNumber,
            String ownerSubject,
            String applicantName,
            String projectCode,
            String title,
            Money claimedAmount,
            Instant now) {
        return new ExpenseCase(
                id,
                caseNumber,
                ownerSubject,
                applicantName,
                projectCode,
                title,
                claimedAmount,
                ExpenseCaseStatus.DRAFT,
                null,
                null,
                null,
                null,
                0,
                now,
                now);
    }

    public ExpenseCase transitionTo(ExpenseCaseStatus target, Instant now) {
        if (target == ExpenseCaseStatus.FAILED) {
            throw validation("Use fail(stage, reason, now) to enter FAILED");
        }
        ExpenseCaseStatus next = ExpenseCaseStateMachine.transition(status, target);
        return new ExpenseCase(
                id,
                caseNumber,
                ownerSubject,
                applicantName,
                projectCode,
                title,
                claimedAmount,
                next,
                riskLevel,
                riskScore,
                null,
                null,
                version + 1,
                createdAt,
                now);
    }

    public ExpenseCase reviseDraft(
            String applicantName,
            String projectCode,
            String title,
            Money claimedAmount,
            Instant now) {
        if (status != ExpenseCaseStatus.DRAFT) {
            throw validation("Only draft expense cases can be revised");
        }
        return new ExpenseCase(
                id,
                caseNumber,
                ownerSubject,
                required(applicantName, "applicantName", 128),
                required(projectCode, "projectCode", 64),
                required(title, "title", 256),
                Objects.requireNonNull(claimedAmount, "claimedAmount"),
                status,
                riskLevel,
                riskScore,
                failureStage,
                failureReason,
                version + 1,
                createdAt,
                now);
    }

    public ExpenseCase withRiskScore(int score, Instant now) {
        if (status != ExpenseCaseStatus.RISK_CHECKING) {
            throw validation("Risk score can only be recorded during RISK_CHECKING");
        }
        return new ExpenseCase(
                id,
                caseNumber,
                ownerSubject,
                applicantName,
                projectCode,
                title,
                claimedAmount,
                status,
                RiskLevel.fromScore(score),
                score,
                failureStage,
                failureReason,
                version + 1,
                createdAt,
                now);
    }

    public ExpenseCase fail(String stage, String reason, Instant now) {
        ExpenseCaseStatus next =
                ExpenseCaseStateMachine.transition(status, ExpenseCaseStatus.FAILED);
        return new ExpenseCase(
                id,
                caseNumber,
                ownerSubject,
                applicantName,
                projectCode,
                title,
                claimedAmount,
                next,
                riskLevel,
                riskScore,
                required(stage, "failureStage", 64),
                required(reason, "failureReason", 1000),
                version + 1,
                createdAt,
                now);
    }

    private static String required(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw validation(field + " is required");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw validation(field + " exceeds " + maxLength + " characters");
        }
        return normalized;
    }

    private static CampusFundFlowException validation(String message) {
        return new CampusFundFlowException(CampusFundFlowErrorCode.VALIDATION_FAILED, message);
    }
}

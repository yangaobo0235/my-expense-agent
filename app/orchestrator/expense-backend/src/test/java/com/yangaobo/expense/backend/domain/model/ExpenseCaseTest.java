package com.yangaobo.expense.backend.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yangaobo.expense.common.domain.ExpenseCaseStatus;
import com.yangaobo.expense.common.error.ExpenseFlowException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ExpenseCaseTest {

    private static final Instant CREATED_AT = Instant.parse("2026-06-18T00:00:00Z");

    @Test
    void createsDraftWithNormalizedMoney() {
        ExpenseCase expenseCase =
                ExpenseCase.create(
                        UUID.randomUUID(),
                        "EF-20260618-1234567890",
                        "user-1",
                        "Alice",
                        "CN-SH-RD",
                        "Client visit",
                        new Money(new BigDecimal("128.50"), "cny"),
                        CREATED_AT);

        assertThat(expenseCase.status()).isEqualTo(ExpenseCaseStatus.DRAFT);
        assertThat(expenseCase.claimedAmount().currency()).isEqualTo("CNY");
        assertThat(expenseCase.version()).isZero();
    }

    @Test
    void transitionIncrementsVersion() {
        ExpenseCase draft = sample();
        ExpenseCase uploaded =
                draft.transitionTo(ExpenseCaseStatus.UPLOADED, CREATED_AT.plusSeconds(10));

        assertThat(uploaded.status()).isEqualTo(ExpenseCaseStatus.UPLOADED);
        assertThat(uploaded.version()).isEqualTo(1);
        assertThat(uploaded.updatedAt()).isAfter(draft.updatedAt());
    }

    @Test
    void reviseDraftUpdatesEditableFieldsAndIncrementsVersion() {
        ExpenseCase draft = sample();
        ExpenseCase revised =
                draft.reviseDraft(
                        " Bob ",
                        " FIN ",
                        " Corrected title ",
                        new Money(new BigDecimal("256.80"), "usd"),
                        CREATED_AT.plusSeconds(10));

        assertThat(revised.status()).isEqualTo(ExpenseCaseStatus.DRAFT);
        assertThat(revised.applicantName()).isEqualTo("Bob");
        assertThat(revised.departmentCode()).isEqualTo("FIN");
        assertThat(revised.title()).isEqualTo("Corrected title");
        assertThat(revised.claimedAmount().amount()).isEqualByComparingTo("256.80");
        assertThat(revised.claimedAmount().currency()).isEqualTo("USD");
        assertThat(revised.version()).isEqualTo(draft.version() + 1);
        assertThat(revised.createdAt()).isEqualTo(draft.createdAt());
        assertThat(revised.updatedAt()).isAfter(draft.updatedAt());
    }

    @Test
    void reviseDraftRejectsNonDraftCase() {
        ExpenseCase uploaded =
                sample().transitionTo(ExpenseCaseStatus.UPLOADED, CREATED_AT.plusSeconds(1));

        assertThatThrownBy(
                        () ->
                                uploaded.reviseDraft(
                                        "Bob",
                                        "FIN",
                                        "Corrected title",
                                        new Money(new BigDecimal("256.80"), "CNY"),
                                        CREATED_AT.plusSeconds(10)))
                .isInstanceOf(ExpenseFlowException.class);
    }

    @Test
    void derivesRiskLevelFromDeterministicScore() {
        ExpenseCase riskChecking =
                sample()
                        .transitionTo(ExpenseCaseStatus.UPLOADED, CREATED_AT.plusSeconds(1))
                        .transitionTo(ExpenseCaseStatus.EXTRACTING, CREATED_AT.plusSeconds(2))
                        .transitionTo(ExpenseCaseStatus.EXTRACTED, CREATED_AT.plusSeconds(3))
                        .transitionTo(ExpenseCaseStatus.POLICY_CHECKING, CREATED_AT.plusSeconds(4))
                        .transitionTo(ExpenseCaseStatus.RISK_CHECKING, CREATED_AT.plusSeconds(5));
        ExpenseCase scored = riskChecking.withRiskScore(60, CREATED_AT.plusSeconds(10));

        assertThat(scored.riskLevel()).isEqualTo(RiskLevel.HIGH);
        assertThat(scored.riskScore()).isEqualTo(60);
    }

    @Test
    void rejectsFractionalPrecisionBeyondCents() {
        assertThatThrownBy(() -> new Money(new BigDecimal("1.001"), "CNY"))
                .isInstanceOf(ExpenseFlowException.class);
    }

    @Test
    void rejectsIllegalWorkflowJump() {
        assertThatThrownBy(
                        () ->
                                sample()
                                        .transitionTo(
                                                ExpenseCaseStatus.APPROVED,
                                                CREATED_AT.plusSeconds(10)))
                .isInstanceOf(ExpenseFlowException.class);
    }

    @Test
    void failureRequiresStageAndReason() {
        ExpenseCase uploaded =
                sample().transitionTo(ExpenseCaseStatus.UPLOADED, CREATED_AT.plusSeconds(1));
        ExpenseCase failed =
                uploaded.fail("DOCUMENT_UPLOAD", "Object storage timeout", CREATED_AT.plusSeconds(2));

        assertThat(failed.status()).isEqualTo(ExpenseCaseStatus.FAILED);
        assertThat(failed.failureStage()).isEqualTo("DOCUMENT_UPLOAD");
        assertThat(failed.failureReason()).isEqualTo("Object storage timeout");
    }

    private static ExpenseCase sample() {
        return ExpenseCase.create(
                UUID.randomUUID(),
                "EF-20260618-1234567890",
                "user-1",
                "Alice",
                "CN-SH-RD",
                "Client visit",
                new Money(new BigDecimal("128.50"), "CNY"),
                CREATED_AT);
    }
}

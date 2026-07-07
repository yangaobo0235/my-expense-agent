package com.yangaobo.expense.common.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yangaobo.expense.common.error.ExpenseFlowErrorCode;
import com.yangaobo.expense.common.error.ExpenseFlowException;
import org.junit.jupiter.api.Test;

class ExpenseCaseStateMachineTest {

    @Test
    void allowsTheHappyPath() {
        assertThat(
                        ExpenseCaseStateMachine.canTransition(
                                ExpenseCaseStatus.UPLOADED, ExpenseCaseStatus.EXTRACTING))
                .isTrue();
        assertThat(
                        ExpenseCaseStateMachine.canTransition(
                                ExpenseCaseStatus.RISK_CHECKING, ExpenseCaseStatus.APPROVED))
                .isTrue();
        assertThat(
                        ExpenseCaseStateMachine.canTransition(
                                ExpenseCaseStatus.WAITING_HUMAN, ExpenseCaseStatus.REJECTED))
                .isTrue();
    }

    @Test
    void rejectsSkippingDeterministicSteps() {
        assertThatThrownBy(
                        () ->
                                ExpenseCaseStateMachine.transition(
                                        ExpenseCaseStatus.UPLOADED,
                                        ExpenseCaseStatus.APPROVED))
                .isInstanceOf(ExpenseFlowException.class)
                .satisfies(
                        error ->
                                assertThat(((ExpenseFlowException) error).code())
                                        .isEqualTo(
                                                ExpenseFlowErrorCode.INVALID_STATE_TRANSITION));
    }

    @Test
    void terminalStatesCannotChange() {
        assertThat(
                        ExpenseCaseStateMachine.canTransition(
                                ExpenseCaseStatus.APPROVED, ExpenseCaseStatus.REJECTED))
                .isFalse();
        assertThat(ExpenseCaseStatus.REJECTED.isTerminal()).isTrue();
    }

    @Test
    void failedCasesCanResumeOnlyAtRecoverableSteps() {
        assertThat(
                        ExpenseCaseStateMachine.canTransition(
                                ExpenseCaseStatus.FAILED, ExpenseCaseStatus.POLICY_CHECKING))
                .isTrue();
        assertThat(
                        ExpenseCaseStateMachine.canTransition(
                                ExpenseCaseStatus.FAILED, ExpenseCaseStatus.APPROVED))
                .isFalse();
    }
}

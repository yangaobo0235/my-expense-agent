package com.yangaobo.expense.common.domain;

import com.yangaobo.expense.common.error.MyExpenseAgentException;
import com.yangaobo.expense.common.error.MyExpenseAgentErrorCode;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public final class ExpenseCaseStateMachine {

    private static final Map<ExpenseCaseStatus, Set<ExpenseCaseStatus>> ALLOWED =
            buildAllowedTransitions();

    private ExpenseCaseStateMachine() {}

    public static boolean canTransition(ExpenseCaseStatus current, ExpenseCaseStatus target) {
        if (current == null || target == null || current == target) {
            return false;
        }
        return ALLOWED.getOrDefault(current, Set.of()).contains(target);
    }

    public static ExpenseCaseStatus transition(
            ExpenseCaseStatus current, ExpenseCaseStatus target) {
        if (!canTransition(current, target)) {
            throw new MyExpenseAgentException(
                    MyExpenseAgentErrorCode.INVALID_STATE_TRANSITION,
                    "Cannot transition expense case from %s to %s".formatted(current, target));
        }
        return target;
    }

    private static Map<ExpenseCaseStatus, Set<ExpenseCaseStatus>> buildAllowedTransitions() {
        Map<ExpenseCaseStatus, Set<ExpenseCaseStatus>> transitions =
                new EnumMap<>(ExpenseCaseStatus.class);
        transitions.put(ExpenseCaseStatus.DRAFT, EnumSet.of(ExpenseCaseStatus.UPLOADED));
        transitions.put(
                ExpenseCaseStatus.UPLOADED,
                EnumSet.of(ExpenseCaseStatus.EXTRACTING, ExpenseCaseStatus.FAILED));
        transitions.put(
                ExpenseCaseStatus.EXTRACTING,
                EnumSet.of(ExpenseCaseStatus.EXTRACTED, ExpenseCaseStatus.FAILED));
        transitions.put(
                ExpenseCaseStatus.EXTRACTED,
                EnumSet.of(ExpenseCaseStatus.POLICY_CHECKING, ExpenseCaseStatus.FAILED));
        transitions.put(
                ExpenseCaseStatus.POLICY_CHECKING,
                EnumSet.of(ExpenseCaseStatus.RISK_CHECKING, ExpenseCaseStatus.FAILED));
        transitions.put(
                ExpenseCaseStatus.RISK_CHECKING,
                EnumSet.of(
                        ExpenseCaseStatus.WAITING_HUMAN,
                        ExpenseCaseStatus.APPROVED,
                        ExpenseCaseStatus.REJECTED,
                        ExpenseCaseStatus.FAILED));
        transitions.put(
                ExpenseCaseStatus.WAITING_HUMAN,
                EnumSet.of(
                        ExpenseCaseStatus.APPROVED,
                        ExpenseCaseStatus.REJECTED,
                        ExpenseCaseStatus.FAILED));
        transitions.put(
                ExpenseCaseStatus.FAILED,
                EnumSet.of(
                        ExpenseCaseStatus.EXTRACTING,
                        ExpenseCaseStatus.POLICY_CHECKING,
                        ExpenseCaseStatus.RISK_CHECKING,
                        ExpenseCaseStatus.WAITING_HUMAN));
        return Map.copyOf(transitions);
    }
}

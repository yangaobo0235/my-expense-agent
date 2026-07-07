package com.yangaobo.expense.backend.domain.repository;

import com.yangaobo.expense.backend.domain.model.ExpenseCase;
import com.yangaobo.expense.common.domain.ExpenseCaseStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExpenseCaseRepository {

    ExpenseCase insert(ExpenseCase expenseCase);

    Optional<ExpenseCase> findById(UUID id);

    ExpenseCasePage search(ExpenseCaseSearchCriteria criteria);

    ExpenseCase update(ExpenseCase expenseCase, long expectedVersion);

    void deleteById(UUID id, long expectedVersion);

    record ExpenseCaseSearchCriteria(
            String ownerSubject,
            ExpenseCaseStatus status,
            String riskLevel,
            String applicant,
            Instant createdFrom,
            Instant createdTo,
            int page,
            int size) {}

    record ExpenseCasePage(List<ExpenseCase> items, long total) {}
}

package com.yangaobo.expense.backend.application.policy;

import java.time.LocalDate;

public record PolicySearchQuery(
        String query,
        String category,
        String region,
        String applicantType,
        LocalDate expenseDate,
        int limit,
        double minimumScore) {}

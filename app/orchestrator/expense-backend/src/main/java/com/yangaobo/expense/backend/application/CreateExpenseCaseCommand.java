package com.yangaobo.expense.backend.application;

import java.math.BigDecimal;

public record CreateExpenseCaseCommand(
        String ownerSubject,
        String applicantName,
        String departmentCode,
        String title,
        BigDecimal claimedAmount,
        String currency) {}

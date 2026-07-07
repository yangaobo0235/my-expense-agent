package com.yangaobo.expense.backend.application;

import java.math.BigDecimal;

public record UpdateExpenseCaseCommand(
        String ownerSubject,
        String applicantName,
        String departmentCode,
        String title,
        BigDecimal claimedAmount,
        String currency) {}

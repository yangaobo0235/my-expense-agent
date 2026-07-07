package com.yangaobo.expense.backend.interfaces.rest;

import java.util.List;

public record ExpenseCasePageResponse(
        List<ExpenseCaseResponse> items, int page, int size, long total) {}

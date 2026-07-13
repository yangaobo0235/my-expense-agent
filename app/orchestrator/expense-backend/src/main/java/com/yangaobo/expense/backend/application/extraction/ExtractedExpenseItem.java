package com.yangaobo.expense.backend.application.extraction;

import java.io.Serializable;
import java.math.BigDecimal;

public record ExtractedExpenseItem(
        String description, BigDecimal quantity, BigDecimal unitPrice, BigDecimal amount)
        implements Serializable {}

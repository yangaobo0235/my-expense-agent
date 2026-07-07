package com.yangaobo.expense.backend.interfaces.rest;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record ReviewDecisionRequest(
        @NotBlank @Size(max = 128) String requestId,
        @PositiveOrZero long version,
        @DecimalMin("0.00") @Digits(integer = 17, fraction = 2) BigDecimal approvedAmount,
        @Size(max = 2000) String comment) {}

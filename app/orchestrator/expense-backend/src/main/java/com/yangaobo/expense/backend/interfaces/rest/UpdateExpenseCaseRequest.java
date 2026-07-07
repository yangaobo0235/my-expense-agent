package com.yangaobo.expense.backend.interfaces.rest;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record UpdateExpenseCaseRequest(
        @NotBlank @Size(max = 128) String applicantName,
        @NotBlank @Size(max = 64) String departmentCode,
        @NotBlank @Size(max = 256) String title,
        @NotNull @DecimalMin("0.00") @Digits(integer = 17, fraction = 2)
                BigDecimal claimedAmount,
        @NotBlank @Size(min = 3, max = 3) String currency) {}

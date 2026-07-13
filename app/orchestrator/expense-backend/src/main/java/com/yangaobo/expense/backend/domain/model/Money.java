package com.yangaobo.expense.backend.domain.model;

import com.yangaobo.expense.common.error.CampusFundFlowErrorCode;
import com.yangaobo.expense.common.error.CampusFundFlowException;
import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Locale;
import java.util.Objects;

public record Money(BigDecimal amount, String currency) implements Serializable {

    public Money {
        Objects.requireNonNull(amount, "amount");
        if (amount.signum() < 0) {
            throw validation("Amount must not be negative");
        }
        try {
            amount = amount.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw validation("Amount supports at most two decimal places");
        }
        try {
            currency = Currency.getInstance(requiredCurrency(currency))
                    .getCurrencyCode();
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw validation("Currency must be a valid ISO 4217 code");
        }
    }

    private static CampusFundFlowException validation(String message) {
        return new CampusFundFlowException(CampusFundFlowErrorCode.VALIDATION_FAILED, message);
    }

    private static String requiredCurrency(String currency) {
        return Objects.requireNonNull(currency, "currency").trim().toUpperCase(Locale.ROOT);
    }
}

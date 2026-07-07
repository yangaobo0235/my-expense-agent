package com.yangaobo.expense.backend.application.extraction;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class ExpenseExtractionValidator {

    private static final BigDecimal AMOUNT_TOLERANCE = new BigDecimal("0.01");
    private static final Pattern INVOICE_NUMBER = Pattern.compile("[A-Za-z0-9\\-]{6,32}");

    private final Clock clock;

    public ExpenseExtractionValidator(Clock clock) {
        this.clock = clock;
    }

    public ExtractionValidationResult validate(ExtractedExpenseDocument document) {
        List<String> errors = new ArrayList<>();
        if (blank(document.documentType())) {
            errors.add("documentType is required");
        }
        if (blank(document.sellerName())) {
            errors.add("sellerName is required");
        }
        if (document.issueDate() == null) {
            errors.add("issueDate is required");
        } else {
            LocalDate latestAllowed =
                    LocalDate.now(clock.withZone(ZoneOffset.UTC)).plusDays(1);
            if (document.issueDate().isAfter(latestAllowed)) {
                errors.add("issueDate is in the future");
            }
        }
        validateAmount(document.totalAmount(), "totalAmount", errors);
        validateCurrency(document.currency(), errors);
        if (!blank(document.invoiceNumber())
                && !INVOICE_NUMBER.matcher(document.invoiceNumber()).matches()) {
            errors.add("invoiceNumber format is invalid");
        }
        if (document.confidence() < 0 || document.confidence() > 1) {
            errors.add("confidence must be between 0 and 1");
        }

        BigDecimal itemTotal = BigDecimal.ZERO;
        for (int index = 0; index < document.items().size(); index++) {
            ExtractedExpenseItem item = document.items().get(index);
            String prefix = "items[" + index + "]";
            if (blank(item.description())) {
                errors.add(prefix + ".description is required");
            }
            validatePositive(item.quantity(), prefix + ".quantity", errors);
            validateAmount(item.unitPrice(), prefix + ".unitPrice", errors);
            validateAmount(item.amount(), prefix + ".amount", errors);
            if (item.amount() != null) {
                itemTotal = itemTotal.add(item.amount());
            }
        }
        if (!document.items().isEmpty()
                && document.totalAmount() != null
                && itemTotal.subtract(document.totalAmount()).abs().compareTo(AMOUNT_TOLERANCE)
                        > 0) {
            errors.add("item amount sum does not match totalAmount");
        }
        return new ExtractionValidationResult(document, errors);
    }

    private static void validateCurrency(String currency, List<String> errors) {
        try {
            Currency.getInstance(currency == null ? "" : currency.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            errors.add("currency must be a valid ISO 4217 code");
        }
    }

    private static void validateAmount(
            BigDecimal amount, String field, List<String> errors) {
        if (amount == null) {
            errors.add(field + " is required");
            return;
        }
        if (amount.signum() < 0) {
            errors.add(field + " must not be negative");
        }
        try {
            amount.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            errors.add(field + " supports at most two decimal places");
        }
    }

    private static void validatePositive(
            BigDecimal value, String field, List<String> errors) {
        if (value == null || value.signum() <= 0) {
            errors.add(field + " must be positive");
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}

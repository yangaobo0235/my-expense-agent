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
        List<ExtractionValidationError> errors = new ArrayList<>();
        if (blank(document.documentType())) {
            errors.add(terminal(ExtractionErrorCode.REQUIRED_FACT_MISSING, "documentType", "documentType is required"));
        }
        if (blank(document.sellerName())) {
            errors.add(terminal(ExtractionErrorCode.REQUIRED_FACT_MISSING, "sellerName", "sellerName is required"));
        }
        if (document.issueDate() == null) {
            errors.add(terminal(ExtractionErrorCode.REQUIRED_FACT_MISSING, "issueDate", "issueDate is required"));
        } else {
            LocalDate latestAllowed =
                    LocalDate.now(clock.withZone(ZoneOffset.UTC)).plusDays(1);
            if (document.issueDate().isAfter(latestAllowed)) {
                errors.add(terminal(ExtractionErrorCode.INVALID_DATE, "issueDate", "issueDate is in the future"));
            }
        }
        validateAmount(document.totalAmount(), "totalAmount", errors);
        validateCurrency(document.currency(), errors);
        if (!blank(document.invoiceNumber())
                && !INVOICE_NUMBER.matcher(document.invoiceNumber()).matches()) {
            errors.add(repairable(ExtractionErrorCode.INVALID_INVOICE_NUMBER, "invoiceNumber", "invoiceNumber format is invalid"));
        }
        if (document.confidence() < 0 || document.confidence() > 1) {
            errors.add(repairable(ExtractionErrorCode.INVALID_CONFIDENCE, "confidence", "confidence must be between 0 and 1"));
        } else if (document.confidence() < 0.6) {
            errors.add(terminal(ExtractionErrorCode.LOW_CONFIDENCE, "confidence", "confidence is below the manual-review threshold"));
        }

        BigDecimal itemTotal = BigDecimal.ZERO;
        for (int index = 0; index < document.items().size(); index++) {
            ExtractedExpenseItem item = document.items().get(index);
            String prefix = "items[" + index + "]";
            if (blank(item.description())) {
                errors.add(terminal(ExtractionErrorCode.REQUIRED_FACT_MISSING, prefix + ".description", prefix + ".description is required"));
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
            errors.add(terminal(ExtractionErrorCode.ITEM_TOTAL_MISMATCH, "items", "item amount sum does not match totalAmount"));
        }
        if (document.warnings().stream()
                .map(String::toLowerCase)
                .anyMatch(value -> value.contains("prompt injection") || value.contains("提示注入"))) {
            errors.add(terminal(ExtractionErrorCode.PROMPT_INJECTION_DETECTED, "warnings", "prompt injection content was detected"));
        }
        return new ExtractionValidationResult(document, errors);
    }

    private static void validateCurrency(String currency, List<ExtractionValidationError> errors) {
        try {
            Currency.getInstance(currency == null ? "" : currency.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            errors.add(repairable(ExtractionErrorCode.INVALID_CURRENCY, "currency", "currency must be a valid ISO 4217 code"));
        }
    }

    private static void validateAmount(
            BigDecimal amount, String field, List<ExtractionValidationError> errors) {
        if (amount == null) {
            errors.add(terminal(ExtractionErrorCode.REQUIRED_FACT_MISSING, field, field + " is required"));
            return;
        }
        if (amount.signum() < 0) {
            errors.add(terminal(ExtractionErrorCode.INVALID_AMOUNT, field, field + " must not be negative"));
        }
        try {
            amount.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            errors.add(repairable(ExtractionErrorCode.INVALID_AMOUNT, field, field + " supports at most two decimal places"));
        }
    }

    private static void validatePositive(
            BigDecimal value, String field, List<ExtractionValidationError> errors) {
        if (value == null || value.signum() <= 0) {
            errors.add(terminal(ExtractionErrorCode.INVALID_AMOUNT, field, field + " must be positive"));
        }
    }

    private static ExtractionValidationError repairable(
            ExtractionErrorCode code, String field, String message) {
        return ExtractionValidationError.repairable(code, field, message);
    }

    private static ExtractionValidationError terminal(
            ExtractionErrorCode code, String field, String message) {
        return ExtractionValidationError.terminal(code, field, message);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}

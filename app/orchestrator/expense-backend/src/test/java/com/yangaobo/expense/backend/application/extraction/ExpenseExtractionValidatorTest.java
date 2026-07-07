package com.yangaobo.expense.backend.application.extraction;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExpenseExtractionValidatorTest {

    private final ExpenseExtractionValidator validator =
            new ExpenseExtractionValidator(
                    Clock.fixed(Instant.parse("2026-06-18T00:00:00Z"), ZoneOffset.UTC));

    @Test
    void acceptsConsistentExpenseDocument() {
        var document =
                new ExtractedExpenseDocument(
                        "INVOICE",
                        "3100",
                        "ABC123456",
                        "ExpenseFlow Hotel",
                        "Example Company",
                        LocalDate.of(2026, 6, 17),
                        new BigDecimal("300.00"),
                        "CNY",
                        List.of(
                                new ExtractedExpenseItem(
                                        "Room",
                                        BigDecimal.ONE,
                                        new BigDecimal("300.00"),
                                        new BigDecimal("300.00"))),
                        0.95,
                        List.of());

        assertThat(validator.validate(document).errors()).isEmpty();
    }

    @Test
    void reportsAmountDateCurrencyAndConfidenceProblemsTogether() {
        var document =
                new ExtractedExpenseDocument(
                        "INVOICE",
                        null,
                        "bad",
                        "",
                        null,
                        LocalDate.of(2027, 1, 1),
                        new BigDecimal("100.00"),
                        "NOPE",
                        List.of(
                                new ExtractedExpenseItem(
                                        "Room",
                                        BigDecimal.ONE,
                                        new BigDecimal("99.00"),
                                        new BigDecimal("99.00"))),
                        1.2,
                        List.of());

        assertThat(validator.validate(document).errors())
                .contains(
                        "sellerName is required",
                        "issueDate is in the future",
                        "currency must be a valid ISO 4217 code",
                        "confidence must be between 0 and 1",
                        "item amount sum does not match totalAmount");
    }
}

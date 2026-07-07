package com.yangaobo.expense.backend.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.yangaobo.expense.backend.domain.model.ExpenseCase;
import com.yangaobo.expense.backend.domain.model.Money;
import com.yangaobo.expense.common.domain.ExpenseCaseStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ExpenseCaseResponseTest {

    @Test
    void includesFailureDetailsForRecoverableCases() {
        Instant now = Instant.parse("2026-06-30T01:00:00Z");
        ExpenseCase failed =
                ExpenseCase.create(
                                UUID.randomUUID(),
                                "EF-20260630-000001",
                                "employee-1",
                                "Alice",
                                "CN-SH-FIN",
                                "Client dinner",
                                new Money(new BigDecimal("128.00"), "CNY"),
                                now)
                        .transitionTo(ExpenseCaseStatus.UPLOADED, now.plusSeconds(1))
                        .fail(
                                "DOCUMENT_EXTRACTION",
                                "Vision model timeout",
                                now.plusSeconds(2));

        ExpenseCaseResponse response = ExpenseCaseResponse.from(failed);

        assertThat(response.status()).isEqualTo(ExpenseCaseStatus.FAILED);
        assertThat(response.failureStage()).isEqualTo("DOCUMENT_EXTRACTION");
        assertThat(response.failureReason()).isEqualTo("Vision model timeout");
    }
}

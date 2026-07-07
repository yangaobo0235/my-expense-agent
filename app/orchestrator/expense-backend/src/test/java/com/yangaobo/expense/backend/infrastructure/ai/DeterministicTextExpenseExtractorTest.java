package com.yangaobo.expense.backend.infrastructure.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yangaobo.expense.backend.application.document.DocumentContentInspector;
import com.yangaobo.expense.backend.application.extraction.DocumentInputKind;
import com.yangaobo.expense.backend.application.extraction.PreparedDocument;
import com.yangaobo.expense.common.error.ExpenseFlowException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class DeterministicTextExpenseExtractorTest {

    private final DeterministicTextExpenseExtractor extractor =
            new DeterministicTextExpenseExtractor(new DocumentContentInspector());

    @Test
    void extractsExplicitLabelledFields() {
        String text =
                """
                documentType: INVOICE
                invoiceCode: 3100
                invoiceNumber: ABC123456
                sellerName: ExpenseFlow Hotel
                buyerName: Example Company
                issueDate: 2026-06-17
                totalAmount: CNY 288.00
                currency: CNY
                """;

        var candidate =
                extractor.extract(
                        new PreparedDocument(
                                DocumentInputKind.TEXT,
                                text,
                                new byte[0],
                                "application/pdf",
                                1));

        assertThat(candidate.document().sellerName()).isEqualTo("ExpenseFlow Hotel");
        assertThat(candidate.document().totalAmount())
                .isEqualByComparingTo(new BigDecimal("288.00"));
        assertThat(candidate.promptVersion())
                .isEqualTo(DeterministicTextExpenseExtractor.PROMPT_VERSION);
        assertThat(candidate.rawResponseHash()).hasSize(64);
    }

    @Test
    void refusesImageInputUntilVisionAdapterIsConfigured() {
        assertThatThrownBy(
                        () ->
                                extractor.extract(
                                        new PreparedDocument(
                                                DocumentInputKind.IMAGE,
                                                "",
                                                new byte[] {1, 2, 3},
                                                "image/png",
                                                1)))
                .isInstanceOf(ExpenseFlowException.class)
                .hasMessageContaining("Vision extraction is not configured");
    }
}

package com.yangaobo.expense.agents.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ExpenseMcpToolCatalogTest {

    @Test
    void shouldExposeOnlySevenReadToolsToAgents() {
        assertThat(ExpenseMcpToolCatalog.readToolNames())
                .containsExactlyInAnyOrder(
                        "get_employee_profile",
                        "get_payment_methods",
                        "get_account_balance",
                        "validate_invoice_number",
                        "calculate_allowed_amount",
                        "check_duplicate_document",
                        "get_expense_history");
    }

    @Test
    void shouldKeepAllWriteToolsOutOfReadOnlySet() {
        assertThat(ExpenseMcpToolCatalog.writeToolNames())
                .containsExactlyInAnyOrder(
                        "submit_reimbursement",
                        "submit_payment",
                        "save_review_evidence");
        assertThat(ExpenseMcpToolCatalog.readToolNames())
                .doesNotContainAnyElementsOf(
                        ExpenseMcpToolCatalog.writeToolNames());
    }

    @Test
    void shouldRejectToolOutsideWhitelist() {
        assertThatThrownBy(
                        () ->
                                ExpenseMcpToolCatalog.require(
                                        "delete_everything"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不在白名单");
    }
}

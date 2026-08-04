package com.yangaobo.expense.agents.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ExpenseMcpToolCatalogTest {

    @Test
    void shouldExposeOnlySevenReadToolsToAgents() {
        assertThat(ExpenseMcpToolCatalog.readToolNames())
                .containsExactlyInAnyOrder(
                        "get_applicant_profile",
                        "get_reimbursement_accounts",
                        "get_project_budget_balance",
                        "validate_invoice_number",
                        "calculate_allowed_amount",
                        "check_duplicate_document",
                        "get_fund_reimbursement_history");
    }

    @Test
    void shouldKeepAllWriteToolsOutOfReadOnlySet() {
        assertThat(ExpenseMcpToolCatalog.writeToolNames())
                .containsExactlyInAnyOrder(
                        "debit_project_budget",
                        "submit_fund_reimbursement",
                        "submit_fund_posting",
                        "save_review_evidence",
                        "record_fund_reimbursement_history");
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

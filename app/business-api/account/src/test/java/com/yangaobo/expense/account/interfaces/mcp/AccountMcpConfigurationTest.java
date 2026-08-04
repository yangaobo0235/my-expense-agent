package com.yangaobo.expense.account.interfaces.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import io.modelcontextprotocol.spec.McpSchema;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AccountMcpConfigurationTest {

    @Test
    void parsesNumericAmountFromMcpArguments() {
        var request =
                new McpSchema.CallToolRequest(
                        "debit_project_budget", Map.of("amount", new BigDecimal("650.00")));

        assertThat(AccountMcpConfiguration.decimal(request, "amount"))
                .isEqualByComparingTo("650.00");
    }

    @Test
    void keepsCompatibilityWithTextualAmount() {
        var request =
                new McpSchema.CallToolRequest(
                        "debit_project_budget", Map.of("amount", "650.00"));

        assertThat(AccountMcpConfiguration.decimal(request, "amount"))
                .isEqualByComparingTo("650.00");
    }

    @Test
    void rejectsMissingOrInvalidAmount() {
        var missing = new McpSchema.CallToolRequest("debit_project_budget", Map.of());
        var invalid =
                new McpSchema.CallToolRequest(
                        "debit_project_budget", Map.of("amount", "not-a-number"));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> AccountMcpConfiguration.decimal(missing, "amount"))
                .withMessage("amount不能为空");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> AccountMcpConfiguration.decimal(invalid, "amount"))
                .withMessage("amount必须是合法金额");
    }
}

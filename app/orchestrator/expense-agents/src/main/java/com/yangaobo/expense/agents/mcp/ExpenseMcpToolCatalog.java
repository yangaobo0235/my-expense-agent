package com.yangaobo.expense.agents.mcp;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum ExpenseMcpToolCatalog {
    GET_EMPLOYEE_PROFILE("account", "get_employee_profile", Access.READ),
    GET_PAYMENT_METHODS("account", "get_payment_methods", Access.READ),
    GET_ACCOUNT_BALANCE("account", "get_account_balance", Access.READ),
    VALIDATE_INVOICE_NUMBER("expense", "validate_invoice_number", Access.READ),
    CALCULATE_ALLOWED_AMOUNT("expense", "calculate_allowed_amount", Access.READ),
    SUBMIT_REIMBURSEMENT("expense", "submit_reimbursement", Access.WRITE),
    SUBMIT_PAYMENT("expense", "submit_payment", Access.WRITE),
    CHECK_DUPLICATE_DOCUMENT(
            "audit-history", "check_duplicate_document", Access.READ),
    GET_EXPENSE_HISTORY("audit-history", "get_expense_history", Access.READ),
    SAVE_REVIEW_EVIDENCE(
            "audit-history", "save_review_evidence", Access.WRITE);

    private static final Map<String, ExpenseMcpToolCatalog> BY_NAME =
            Arrays.stream(values())
                    .collect(
                            Collectors.toUnmodifiableMap(
                                    ExpenseMcpToolCatalog::toolName,
                                    Function.identity()));

    private final String clientKey;
    private final String toolName;
    private final Access access;

    ExpenseMcpToolCatalog(String clientKey, String toolName, Access access) {
        this.clientKey = clientKey;
        this.toolName = toolName;
        this.access = access;
    }

    public String clientKey() {
        return clientKey;
    }

    public String toolName() {
        return toolName;
    }

    public Access access() {
        return access;
    }

    public static ExpenseMcpToolCatalog require(String toolName) {
        ExpenseMcpToolCatalog tool = BY_NAME.get(toolName);
        if (tool == null) {
            throw new IllegalArgumentException("MCP Tool 不在白名单中：" + toolName);
        }
        return tool;
    }

    public static Set<String> readToolNames() {
        return names(Access.READ);
    }

    public static Set<String> writeToolNames() {
        return names(Access.WRITE);
    }

    private static Set<String> names(Access access) {
        return Arrays.stream(values())
                .filter(tool -> tool.access == access)
                .map(ExpenseMcpToolCatalog::toolName)
                .collect(Collectors.toUnmodifiableSet());
    }

    public enum Access {
        READ,
        WRITE
    }
}

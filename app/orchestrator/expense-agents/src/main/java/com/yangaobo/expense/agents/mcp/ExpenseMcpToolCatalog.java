package com.yangaobo.expense.agents.mcp;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum ExpenseMcpToolCatalog {
    GET_APPLICANT_PROFILE("account", "get_applicant_profile", Access.READ),
    GET_REIMBURSEMENT_ACCOUNTS("account", "get_reimbursement_accounts", Access.READ),
    GET_PROJECT_BUDGET_BALANCE("account", "get_project_budget_balance", Access.READ),
    DEBIT_PROJECT_BUDGET("account", "debit_project_budget", Access.WRITE),
    VALIDATE_INVOICE_NUMBER("expense", "validate_invoice_number", Access.READ),
    CALCULATE_ALLOWED_AMOUNT("expense", "calculate_allowed_amount", Access.READ),
    SUBMIT_FUND_REIMBURSEMENT("expense", "submit_fund_reimbursement", Access.WRITE),
    SUBMIT_FUND_POSTING("expense", "submit_fund_posting", Access.WRITE),
    CHECK_DUPLICATE_DOCUMENT(
            "audit-history", "check_duplicate_document", Access.READ),
    GET_FUND_REIMBURSEMENT_HISTORY("audit-history", "get_fund_reimbursement_history", Access.READ),
    SAVE_REVIEW_EVIDENCE(
            "audit-history", "save_review_evidence", Access.WRITE),
    RECORD_FUND_REIMBURSEMENT_HISTORY(
            "audit-history", "record_fund_reimbursement_history", Access.WRITE);

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

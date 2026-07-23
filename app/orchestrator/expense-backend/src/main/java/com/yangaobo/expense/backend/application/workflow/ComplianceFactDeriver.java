package com.yangaobo.expense.backend.application.workflow;

import com.yangaobo.expense.backend.application.extraction.ExtractedExpenseDocument;
import com.yangaobo.expense.backend.application.extraction.ExtractedExpenseItem;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class ComplianceFactDeriver {

    private static final Set<String> SUSPICIOUS_SELLERS =
            Set.of("未知", "未知商户", "unknown", "n/a", "na", "个人收款", "私人账户");
    private static final List<String> FORBIDDEN_ITEM_TERMS =
            List.of(
                    "香烟",
                    "烟草",
                    "酒水",
                    "白酒",
                    "红酒",
                    "礼品卡",
                    "购物卡",
                    "充值卡",
                    "娱乐消费",
                    "奢侈品",
                    "私人用品",
                    "个人消费");
    private static final List<String> PROMPT_INJECTION_TERMS =
            List.of(
                    "忽略之前",
                    "ignore previous",
                    "越过规则",
                    "绕过审批",
                    "直接批准",
                    "approve all",
                    "跳过人工",
                    "skip human",
                    "submit_fund_posting",
                    "直接入账");

    private ComplianceFactDeriver() {}

    static ComplianceFacts derive(
            List<ExtractedExpenseDocument> documents,
            LocalDate claimedExpenseDate,
            BigDecimal claimedAmount,
            String claimedCurrency,
            ExpenseContextGateway.ProjectBudget budget,
            List<Map<String, Object>> policyFindings,
            LocalDate today) {
        List<String> dateReasons = new ArrayList<>();
        List<String> sellerReasons = new ArrayList<>();
        List<String> missingFields = new ArrayList<>();
        List<String> forbiddenItems = new ArrayList<>();
        List<String> promptInjectionEvidence = new ArrayList<>();

        for (int documentIndex = 0; documentIndex < documents.size(); documentIndex++) {
            ExtractedExpenseDocument document = documents.get(documentIndex);
            String prefix = "document[" + documentIndex + "]";
            LocalDate issueDate = document.issueDate();
            if (issueDate == null) {
                missingFields.add(prefix + ".issueDate");
            } else {
                if (issueDate.isAfter(today.plusDays(1))) {
                    dateReasons.add(prefix + ":FUTURE_DATE:" + issueDate);
                }
                if (claimedExpenseDate != null
                        && Math.abs(ChronoUnit.DAYS.between(claimedExpenseDate, issueDate)) > 31) {
                    dateReasons.add(prefix + ":CLAIM_DATE_MISMATCH:" + issueDate);
                }
            }

            String seller = normalized(document.sellerName());
            if (seller.isBlank()) {
                missingFields.add(prefix + ".sellerName");
                sellerReasons.add(prefix + ":MISSING_SELLER");
            } else if (SUSPICIOUS_SELLERS.contains(seller.toLowerCase(Locale.ROOT))) {
                sellerReasons.add(prefix + ":PLACEHOLDER_SELLER:" + seller);
            }
            if (document.totalAmount() == null || document.totalAmount().signum() <= 0) {
                missingFields.add(prefix + ".totalAmount");
            }
            if (normalized(document.currency()).isBlank()) {
                missingFields.add(prefix + ".currency");
            }
            if (document.items().isEmpty()) {
                missingFields.add(prefix + ".items");
            }
            if ("INVOICE".equalsIgnoreCase(normalized(document.documentType()))
                    && normalized(document.invoiceNumber()).isBlank()) {
                missingFields.add(prefix + ".invoiceNumber");
            }
            for (ExtractedExpenseItem item : document.items()) {
                String description = normalized(item.description());
                String matchedTerm = forbiddenTerm(description);
                if (matchedTerm != null) {
                    forbiddenItems.add(description + " [" + matchedTerm + "]");
                }
                collectPromptInjectionEvidence(
                        prefix + ".items.description", description, promptInjectionEvidence);
            }
            collectPromptInjectionEvidence(prefix + ".sellerName", seller, promptInjectionEvidence);
            collectPromptInjectionEvidence(
                    prefix + ".buyerName", normalized(document.buyerName()), promptInjectionEvidence);
            for (String warning : document.warnings()) {
                collectPromptInjectionEvidence(
                        prefix + ".warnings", normalized(warning), promptInjectionEvidence);
            }
        }

        boolean budgetExceeded =
                budget != null
                        && !budget.dependencyFailure()
                        && (claimedAmount.compareTo(budget.available()) > 0
                                || !claimedCurrency.equalsIgnoreCase(budget.currency()));
        boolean policyEvidenceMissing = policyFindings == null || policyFindings.isEmpty();
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("claimedExpenseDate", claimedExpenseDate);
        evidence.put("dateReasons", List.copyOf(dateReasons));
        evidence.put("sellerReasons", List.copyOf(sellerReasons));
        evidence.put("missingFields", List.copyOf(missingFields));
        evidence.put("forbiddenItems", List.copyOf(forbiddenItems));
        evidence.put("promptInjectionEvidence", List.copyOf(promptInjectionEvidence));
        evidence.put("policyMatchCount", policyFindings == null ? 0 : policyFindings.size());
        if (budget != null) {
            evidence.put(
                    "projectBudget",
                    Map.of(
                            "projectCode",
                            budget.projectCode(),
                            "available",
                            budget.available(),
                            "currency",
                            budget.currency(),
                            "dependencyFailure",
                            budget.dependencyFailure()));
        }
        return new ComplianceFacts(
                !dateReasons.isEmpty(),
                !sellerReasons.isEmpty(),
                budgetExceeded,
                !missingFields.isEmpty(),
                !forbiddenItems.isEmpty(),
                policyEvidenceMissing,
                !promptInjectionEvidence.isEmpty(),
                evidence);
    }

    private static String forbiddenTerm(String description) {
        String normalized = description.toLowerCase(Locale.ROOT);
        return FORBIDDEN_ITEM_TERMS.stream().filter(normalized::contains).findFirst().orElse(null);
    }

    private static void collectPromptInjectionEvidence(
            String field, String value, List<String> evidence) {
        String normalized = value.toLowerCase(Locale.ROOT);
        PROMPT_INJECTION_TERMS.stream()
                .filter(normalized::contains)
                .findFirst()
                .ifPresent(term -> evidence.add(field + " [" + term + "]"));
    }

    private static String normalized(String value) {
        return value == null ? "" : value.trim();
    }

    record ComplianceFacts(
            boolean dateAnomaly,
            boolean sellerAnomaly,
            boolean projectBudgetExceeded,
            boolean missingRequiredDocument,
            boolean forbiddenExpenseItem,
            boolean policyEvidenceMissing,
            boolean promptInjectionDetected,
            Map<String, Object> evidence)
            implements Serializable {

        ComplianceFacts {
            evidence = evidence == null ? Map.of() : Map.copyOf(evidence);
        }
    }
}

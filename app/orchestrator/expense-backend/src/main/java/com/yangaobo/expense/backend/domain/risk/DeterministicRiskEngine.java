package com.yangaobo.expense.backend.domain.risk;

import com.yangaobo.expense.backend.domain.model.RiskLevel;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DeterministicRiskEngine {

    private static final BigDecimal AMOUNT_TOLERANCE = new BigDecimal("0.01");

    public RiskAssessment assess(RiskAssessmentInput input) {
        List<RiskSignal> signals = new ArrayList<>();

        if (input.duplicateDocument()) {
            signals.add(
                    signal(
                            RiskSignalCode.DUPLICATE_DOCUMENT,
                            50,
                            "票据与历史报销记录重复"));
        }
        BigDecimal difference =
                input.claimedAmount().subtract(input.extractedAmount()).abs();
        if (difference.compareTo(AMOUNT_TOLERANCE) > 0) {
            signals.add(
                    new RiskSignal(
                            RiskSignalCode.AMOUNT_MISMATCH,
                            30,
                            "申报金额与票据提取金额不一致",
                            Map.of(
                                    "claimedAmount",
                                    input.claimedAmount().toPlainString(),
                                    "extractedAmount",
                                    input.extractedAmount().toPlainString(),
                                    "difference",
                                    difference.toPlainString())));
        }
        if (input.dateAnomaly()) {
            signals.add(signal(RiskSignalCode.DATE_ANOMALY, 20, "票据日期存在异常"));
        }
        if (input.sellerAnomaly()) {
            signals.add(signal(RiskSignalCode.SELLER_ANOMALY, 15, "销售方信息存在异常"));
        }
        if (input.policyLimitExceeded()) {
            signals.add(
                    signal(
                            RiskSignalCode.POLICY_LIMIT_EXCEEDED,
                            35,
                            "费用超过适用制度限额"));
        }
        if (input.missingRequiredDocument()) {
            signals.add(
                    signal(
                            RiskSignalCode.MISSING_REQUIRED_DOCUMENT,
                            30,
                            "缺少制度要求的必要凭证"));
        }
        if (input.extractionConfidence() < 0.70) {
            signals.add(
                    new RiskSignal(
                            RiskSignalCode.LOW_EXTRACTION_CONFIDENCE,
                            25,
                            "票据字段提取置信度过低",
                            Map.of(
                                    "confidence",
                                    "%.4f".formatted(input.extractionConfidence()),
                                    "threshold",
                                    "0.7000")));
        }
        if (input.forbiddenExpenseItem()) {
            signals.add(
                    signal(
                            RiskSignalCode.FORBIDDEN_EXPENSE_ITEM,
                            40,
                            "票据包含制度明确禁止报销的项目"));
        }
        if (input.projectBudgetExceeded()) {
            signals.add(
                    signal(
                            RiskSignalCode.PROJECT_BUDGET_EXCEEDED,
                            45,
                            "申请金额超过共享项目可用预算或预算币种不匹配"));
        }
        if (input.policyEvidenceMissing()) {
            signals.add(
                    signal(
                            RiskSignalCode.POLICY_EVIDENCE_MISSING,
                            30,
                            "未检索到可追溯的适用校园经费制度证据"));
        }
        if (input.promptInjectionDetected()) {
            signals.add(
                    signal(
                            RiskSignalCode.PROMPT_INJECTION_DETECTED,
                            60,
                            "票据文本包含试图绕过审核或操纵模型的提示注入指令"));
        }

        int score =
                Math.min(
                        100,
                        signals.stream().mapToInt(RiskSignal::score).sum());
        RiskLevel level = RiskLevel.fromScore(score);
        return new RiskAssessment(score, level, score >= 30, signals);
    }

    private static RiskSignal signal(
            RiskSignalCode code, int score, String message) {
        return new RiskSignal(code, score, message, Map.of());
    }
}

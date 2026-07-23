package com.yangaobo.expense.backend.application.workflow;

import com.yangaobo.expense.backend.domain.model.RiskLevel;
import com.yangaobo.expense.backend.domain.risk.RiskAssessment;
import com.yangaobo.expense.backend.domain.risk.RiskSignal;
import com.yangaobo.expense.backend.domain.risk.RiskSignalCode;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record RiskRoutingDecision(
        RiskRoutingAction action,
        boolean requiresHumanReview,
        boolean debateAssistEnabled,
        String queue,
        String assigneeRole,
        int slaHours,
        List<String> requiredEvidence,
        String userFacingMessage,
        String fallbackStrategy,
        List<String> reasons)
        implements Serializable {

    public RiskRoutingDecision {
        if (action == null) {
            throw new IllegalArgumentException("路由动作不能为空");
        }
        queue = queue == null ? "" : queue.trim();
        assigneeRole = assigneeRole == null ? "" : assigneeRole.trim();
        slaHours = Math.max(1, slaHours);
        requiredEvidence = requiredEvidence == null ? List.of() : List.copyOf(requiredEvidence);
        userFacingMessage = userFacingMessage == null ? "" : userFacingMessage.trim();
        fallbackStrategy = fallbackStrategy == null ? "" : fallbackStrategy.trim();
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
    }

    public Map<String, Object> toEvidence() {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("action", action.name());
        evidence.put("requiresHumanReview", requiresHumanReview);
        evidence.put("debateAssistEnabled", debateAssistEnabled);
        evidence.put("queue", queue);
        evidence.put("assigneeRole", assigneeRole);
        evidence.put("slaHours", slaHours);
        evidence.put("requiredEvidence", requiredEvidence);
        evidence.put("userFacingMessage", userFacingMessage);
        evidence.put("fallbackStrategy", fallbackStrategy);
        evidence.put("reasons", reasons);
        return evidence;
    }

    static RiskRoutingDecision from(RiskAssessment risk) {
        List<RiskSignalCode> codes = risk.signals().stream().map(RiskSignal::code).toList();
        List<String> reasons = codes.stream().map(Enum::name).toList();
        if (codes.contains(RiskSignalCode.DUPLICATE_DOCUMENT)
                || codes.contains(RiskSignalCode.SELLER_ANOMALY)
                || codes.contains(RiskSignalCode.FORBIDDEN_EXPENSE_ITEM)
                || codes.contains(RiskSignalCode.PROMPT_INJECTION_DETECTED)) {
            return new RiskRoutingDecision(
                    RiskRoutingAction.POSSIBLE_FRAUD_ESCALATE,
                    true,
                    true,
                    "FRAUD_REVIEW",
                    "FINANCE_ADMIN",
                    8,
                    List.of("重复票据检查", "商户异常证据", "提示注入证据", "历史报销", "原始票据", "正反证据摘要"),
                    "该案例存在疑似舞弊或提示注入信号，已升级财务管理员复核。",
                    "ESCALATE_FRAUD_REVIEW",
                    reasons);
        }
        if (risk.level() == RiskLevel.HIGH) {
            return new RiskRoutingDecision(
                    RiskRoutingAction.HIGH_RISK_ESCALATE_WITH_DEBATE_ASSIST,
                    true,
                    true,
                    "HIGH_RISK_REVIEW",
                    "FINANCE_ADMIN",
                    12,
                    List.of("风险信号", "制度引用", "历史报销", "重复票据检查", "正反证据摘要"),
                    "该案例风险较高，已升级财务管理员复核。",
                    "ESCALATE_WITH_DEBATE_ASSIST",
                    reasons);
        }
        if (codes.contains(RiskSignalCode.DEPENDENCY_UNAVAILABLE)) {
            return new RiskRoutingDecision(
                    RiskRoutingAction.DEPENDENCY_FAILURE_HUMAN_REVIEW,
                    true,
                    false,
                    "DEPENDENCY_RECOVERY",
                    "COLLEGE_REVIEWER",
                    24,
                    List.of("依赖失败详情", "缓存证据", "原始票据"),
                    "外部依赖暂不可用，已转人工核验证据。",
                    "RETRY_AFTER_COOLDOWN_OR_HUMAN_REVIEW",
                    reasons);
        }
        if (codes.contains(RiskSignalCode.POLICY_LIMIT_EXCEEDED)
                || codes.contains(RiskSignalCode.PROJECT_BUDGET_EXCEEDED)
                || codes.contains(RiskSignalCode.POLICY_EVIDENCE_MISSING)
                || codes.contains(RiskSignalCode.AMOUNT_MISMATCH)) {
            return new RiskRoutingDecision(
                    RiskRoutingAction.POLICY_CONFLICT_MANUAL_REVIEW,
                    true,
                    false,
                    "POLICY_CONFLICT_REVIEW",
                    "COLLEGE_REVIEWER",
                    24,
                    List.of("制度条款引用", "项目预算余额", "申报金额", "票据金额", "审批上限计算"),
                    "该申请存在制度证据、项目预算或金额冲突，需要人工确认可报销金额。",
                    "MANUAL_POLICY_RECONCILIATION",
                    reasons);
        }
        if (codes.contains(RiskSignalCode.MISSING_REQUIRED_DOCUMENT)
                || codes.contains(RiskSignalCode.LOW_EXTRACTION_CONFIDENCE)
                || codes.contains(RiskSignalCode.DATE_ANOMALY)) {
            return new RiskRoutingDecision(
                    RiskRoutingAction.MISSING_INFO_REQUEST_MORE,
                    true,
                    false,
                    "MISSING_INFO",
                    "ADVISOR",
                    48,
                    List.of("缺失材料清单", "票据原件", "学生补充说明"),
                    "当前材料、票据识别结果或日期信息需要补充核验。",
                    "REQUEST_MORE_INFO",
                    reasons);
        }
        if (risk.requiresHumanReview()) {
            return new RiskRoutingDecision(
                    RiskRoutingAction.MEDIUM_RISK_HUMAN_REVIEW,
                    true,
                    false,
                    "STANDARD_REVIEW",
                    "ADVISOR",
                    48,
                    List.of("风险信号", "制度引用", "MCP 证据"),
                    "该经费申请需要指导老师复核项目相关性和材料完整性。",
                    "HUMAN_REVIEW",
                    reasons);
        }
        return new RiskRoutingDecision(
                RiskRoutingAction.LOW_RISK_AUTO_APPROVE,
                false,
                false,
                "AUTO_APPROVAL",
                "SYSTEM",
                1,
                List.of("票据字段", "制度引用", "风险评分"),
                "风险较低，系统按确定性规则自动通过。",
                "AUTO_APPROVE",
                reasons);
    }
}

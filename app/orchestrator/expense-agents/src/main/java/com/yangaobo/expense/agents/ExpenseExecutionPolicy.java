package com.yangaobo.expense.agents;

import java.util.List;

public final class ExpenseExecutionPolicy {

    public static final String PLAN_VERSION = "my-expense-governed-policy-v1";

    public GovernedExecutionPlan plan(String caseId, String requestId) {
        return new GovernedExecutionPlan(
                PLAN_VERSION,
                caseId,
                requestId,
                List.of(
                        step(1, AgentRole.RECEIPT_EXTRACTION_AGENT, "票据结构化抽取", "INGESTION",
                                List.of(), false, AgentFailurePolicy.REQUIRE_HUMAN_REVIEW, 1,
                                "生成候选事实并执行结构与业务校验；一次修正失败后转人工票据核验。"),
                        step(2, AgentRole.MCP_CONTEXT_AGENT, "业务证据收集", "EVIDENCE_COLLECTION",
                                List.of("get_applicant_profile", "get_reimbursement_accounts",
                                        "get_project_budget_balance", "check_duplicate_document",
                                        "get_fund_reimbursement_history"),
                                false, AgentFailurePolicy.RETRY_THEN_HUMAN_REVIEW, 2,
                                "通过只读 MCP Tool 收集申请人、预算、历史和重复票据证据。"),
                        step(3, AgentRole.POLICY_RAG_AGENT, "适用制度检索", "EVIDENCE_COLLECTION",
                                List.of("calculate_allowed_amount", "validate_invoice_number"),
                                false, AgentFailurePolicy.RETRY_THEN_HUMAN_REVIEW, 2,
                                "按类别、地区、申请人类型和费用日期检索适用制度并保留引用。"),
                        step(4, AgentRole.RISK_RULE_AGENT, "确定性风险计算与路由", "RISK_DECISION",
                                List.of(), false, AgentFailurePolicy.STOP_AND_ESCALATE, 1,
                                "Java 规则生成风险信号并路由补材料、学院复核、财务复核或低风险路径。"),
                        step(5, AgentRole.REVIEW_SUMMARY_AGENT, "受限复核摘要", "HUMAN_REVIEW",
                                List.of(), false, AgentFailurePolicy.REQUIRE_HUMAN_REVIEW, 1,
                                "仅根据已有风险信号和制度引用生成摘要，不改变风险、路由或审批状态。"),
                        step(6, AgentRole.APPROVED_SETTLEMENT_AGENT, "审批后受控写入", "SETTLEMENT",
                                List.of("debit_project_budget", "submit_fund_reimbursement",
                                        "submit_fund_posting", "record_fund_reimbursement_history",
                                        "save_review_evidence"),
                                true, AgentFailurePolicy.IDEMPOTENT_WRITE_RETRY, 3,
                                "仅在服务端重新验证审批、角色、金额、币种和 requestId 后执行白名单写 Tool。")));
    }

    private static AgentStepDefinition step(
            int sequence,
            AgentRole role,
            String name,
            String phase,
            List<String> tools,
            boolean write,
            AgentFailurePolicy failurePolicy,
            int maxAttempts,
            String responsibility) {
        return new AgentStepDefinition(
                sequence, role, name, phase, sequence == 4 ? "ROUTER" : "SEQUENTIAL",
                phase, List.of(), "", responsibility,
                List.of("caseContext"), List.of("governedEvidence"), tools, write,
                failurePolicy, maxAttempts, "人工接管");
    }
}

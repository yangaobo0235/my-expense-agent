package com.yangaobo.expense.backend.application.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangaobo.expense.backend.application.ai.ChatModelClient;
import com.yangaobo.expense.backend.application.ai.ChatModelProperties;
import com.yangaobo.expense.backend.application.workflow.CaseEvidenceService;
import com.yangaobo.expense.backend.application.prompt.PromptRenderService;
import com.yangaobo.expense.backend.application.prompt.RenderedPrompt;
import com.yangaobo.expense.backend.domain.model.ExpenseCase;
import com.yangaobo.expense.backend.domain.risk.RiskSignal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ReviewReportAgent {

    private final PromptRenderService promptRenderService;
    private final ChatModelClient chatModelClient;
    private final ObjectMapper objectMapper;
    private final ChatModelProperties chatModelProperties;

    public ReviewReportAgent(
            PromptRenderService promptRenderService,
            ChatModelClient chatModelClient,
            ObjectMapper objectMapper,
            ChatModelProperties chatModelProperties) {
        this.promptRenderService = promptRenderService;
        this.chatModelClient = chatModelClient;
        this.objectMapper = objectMapper;
        this.chatModelProperties = chatModelProperties;
    }

    public DraftResult draft(
            ExpenseCase expenseCase,
            CaseEvidenceService.CaseEvidence evidence,
            java.time.Instant createdAt) {
        String evidenceJson = evidenceJson(expenseCase, evidence);
        RenderedPrompt prompt =
                promptRenderService.render(
                        "review-report",
                        Map.of("evidence", evidenceJson));
        try {
            ChatModelClient.ChatCompletion completion =
                    chatModelClient.complete(
                            new ChatModelClient.ChatRequest(
                                    "review-report",
                                    prompt.modelName(),
                                    prompt.temperature(),
                                    prompt.maxTokens(),
                                    """
                                    You are a campus fund compliance review assistant.
                                    Generate auditable evidence explanations for student and project reimbursements only.
                                    Never approve, reject, modify amounts, change risk scores, or initiate fund posting.
                                    Return JSON with summary, riskExplanation, humanReviewHints, limitations.
                                    """,
                                    prompt.content()));
            ReviewReport report =
                    reportFromModel(expenseCase, evidence, prompt, completion.content(), createdAt);
            return new DraftResult(report, prompt, completion, false, null);
        } catch (RuntimeException exception) {
            if (!chatModelProperties.isFallbackEnabled()) {
                throw exception;
            }
            ReviewReport report = fallback(expenseCase, evidence, prompt, createdAt);
            return new DraftResult(report, prompt, null, true, exception.getMessage());
        }
    }

    private ReviewReport fallback(
            ExpenseCase expenseCase,
            CaseEvidenceService.CaseEvidence evidence,
            RenderedPrompt prompt,
            java.time.Instant createdAt) {
        String summary =
                "该报销申请为%s，申报金额 %s %s，当前状态为 %s。"
                        .formatted(
                                expenseCase.title(),
                                expenseCase.claimedAmount().amount(),
                                expenseCase.claimedAmount().currency(),
                                expenseCase.status());
        List<String> riskExplanation =
                evidence.risk() == null
                        ? List.of("尚未形成风险评分，报告仅能展示已有证据。")
                        : evidence.risk().signals().stream()
                                .map(RiskSignal::message)
                                .distinct()
                                .toList();
        List<ReviewReport.PolicyCitation> citations =
                evidence.policyFindings().stream()
                        .map(ReviewReportAgent::citation)
                        .filter(java.util.Objects::nonNull)
                        .collect(
                                java.util.stream.Collectors.collectingAndThen(
                                        java.util.stream.Collectors.toCollection(LinkedHashSet::new),
                                        List::copyOf));
        List<String> hints =
                evidence.risk() != null && evidence.risk().requiresHumanReview()
                        ? List.of("请核验风险信号对应的票据、制度引用和 MCP 证据。", "审批结论必须由人工复核或既有规则产生，报告本身不改变案例状态。")
                        : List.of("请确认制度引用和票据字段无异常后再继续处理。");
        return new ReviewReport(
                UUID.randomUUID(),
                expenseCase.id(),
                summary,
                riskExplanation,
                citations,
                hints,
                List.of("该报告只能解释已有证据，不能替代人工审核决定。", "该报告不会修改风险分、批准金额或案例状态。"),
                prompt.modelName(),
                prompt.version(),
                createdAt);
    }

    private ReviewReport reportFromModel(
            ExpenseCase expenseCase,
            CaseEvidenceService.CaseEvidence evidence,
            RenderedPrompt prompt,
            String content,
            java.time.Instant createdAt) {
        try {
            JsonNode root = objectMapper.readTree(stripJsonFence(content));
            List<ReviewReport.PolicyCitation> citations =
                    evidence.policyFindings().stream()
                            .map(ReviewReportAgent::citation)
                            .filter(java.util.Objects::nonNull)
                            .collect(
                                    java.util.stream.Collectors.collectingAndThen(
                                            java.util.stream.Collectors.toCollection(LinkedHashSet::new),
                                            List::copyOf));
            return new ReviewReport(
                    UUID.randomUUID(),
                    expenseCase.id(),
                    text(root, "summary", "模型未返回审核摘要。"),
                    textList(root, "riskExplanation"),
                    citations,
                    textList(root, "humanReviewHints"),
                    textList(root, "limitations"),
                    prompt.modelName(),
                    prompt.version(),
                    createdAt);
        } catch (Exception exception) {
            throw new IllegalStateException("审核报告模型输出不是有效 JSON：" + exception.getMessage(), exception);
        }
    }

    public String modelName() {
        return promptRenderService.activeOrSeed("review-report").modelName();
    }

    public String promptVersion() {
        return promptRenderService.activeOrSeed("review-report").version();
    }

    private String evidenceJson(
            ExpenseCase expenseCase,
            CaseEvidenceService.CaseEvidence evidence) {
        try {
            Map<String, Object> caseContext = new java.util.LinkedHashMap<>();
            caseContext.put("id", expenseCase.id());
            caseContext.put("title", expenseCase.title());
            caseContext.put("claimedAmount", expenseCase.claimedAmount().amount());
            caseContext.put("currency", expenseCase.claimedAmount().currency());
            caseContext.put("status", expenseCase.status());
            caseContext.put("riskLevel", expenseCase.riskLevel());
            caseContext.put("riskScore", expenseCase.riskScore());
            Map<String, Object> root = new java.util.LinkedHashMap<>();
            root.put("case", caseContext);
            root.put("risk", evidence.risk());
            root.put("policyFindings", evidence.policyFindings());
            root.put("steps", evidence.steps());
            root.put("toolCalls", evidence.toolCalls());
            return objectMapper.writeValueAsString(
                    root);
        } catch (Exception exception) {
            throw new IllegalStateException("审核报告证据序列化失败", exception);
        }
    }

    private static List<String> textList(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            if (item.isTextual() && !item.asText().isBlank()) {
                values.add(item.asText());
            }
        }
        return List.copyOf(values);
    }

    private static String text(JsonNode root, String field, String fallback) {
        JsonNode node = root.get(field);
        return node != null && node.isTextual() && !node.asText().isBlank()
                ? node.asText()
                : fallback;
    }

    private static String stripJsonFence(String content) {
        String normalized = content == null ? "" : content.trim();
        if (normalized.startsWith("```")) {
            normalized = normalized.replaceFirst("(?s)^```(?:json)?\\s*", "");
            normalized = normalized.replaceFirst("(?s)\\s*```$", "");
        }
        return normalized.trim();
    }

    private static ReviewReport.PolicyCitation citation(Map<String, Object> finding) {
        Object policyCode = finding.get("policyCode");
        Object section = finding.get("section");
        Object chunkId = finding.get("chunkId");
        if (policyCode == null || section == null || chunkId == null) {
            return null;
        }
        return new ReviewReport.PolicyCitation(
                policyCode.toString(), section.toString(), chunkId.toString());
    }

    public record DraftResult(
            ReviewReport report,
            RenderedPrompt prompt,
            ChatModelClient.ChatCompletion completion,
            boolean fallbackUsed,
            String fallbackReason) {}
}

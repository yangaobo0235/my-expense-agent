package com.yangaobo.expense.backend.application.chat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangaobo.expense.backend.application.ai.ChatModelClient;
import com.yangaobo.expense.backend.application.ai.ChatModelProperties;
import com.yangaobo.expense.backend.application.governance.AgentInputGuard;
import com.yangaobo.expense.backend.application.governance.AgentInputGuard.GuardMode;
import com.yangaobo.expense.backend.application.observability.ModelCallRecorder;
import com.yangaobo.expense.backend.application.prompt.PromptRenderService;
import com.yangaobo.expense.backend.application.prompt.RenderedPrompt;
import com.yangaobo.expense.backend.application.workflow.CaseEvidenceService;
import com.yangaobo.expense.backend.application.settlement.ToolCallRepository;
import com.yangaobo.expense.backend.domain.risk.RiskSignal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EvidenceChatService {

    private final CaseEvidenceService evidenceService;
    private final ModelCallRecorder modelCallRecorder;
    private final AgentInputGuard inputGuard;
    private final PromptRenderService promptRenderService;
    private final ChatModelClient chatModelClient;
    private final ObjectMapper objectMapper;
    private final ChatModelProperties chatModelProperties;

    public EvidenceChatService(
            CaseEvidenceService evidenceService,
            ModelCallRecorder modelCallRecorder,
            AgentInputGuard inputGuard,
            PromptRenderService promptRenderService,
            ChatModelClient chatModelClient,
            ObjectMapper objectMapper,
            ChatModelProperties chatModelProperties) {
        this.evidenceService = evidenceService;
        this.modelCallRecorder = modelCallRecorder;
        this.inputGuard = inputGuard;
        this.promptRenderService = promptRenderService;
        this.chatModelClient = chatModelClient;
        this.objectMapper = objectMapper;
        this.chatModelProperties = chatModelProperties;
    }

    @Transactional(readOnly = true)
    public EvidenceChatResponse answer(
            UUID caseId, String subject, boolean privileged, String question) {
        CaseEvidenceService.CaseEvidence evidence =
                evidenceService.get(caseId, subject, privileged);
        long startedNanos = System.nanoTime();
        AgentInputGuard.GuardResult guard =
                inputGuard.inspect("evidence-chat-question", question, GuardMode.BLOCK);
        RenderedPrompt prompt =
                promptRenderService.render(
                        "evidence-chat",
                        java.util.Map.of(
                                "question",
                                guard.sanitizedInput(),
                                "evidence",
                                evidenceJson(evidence)));
        EvidenceChatResponse response;
        ChatModelClient.ChatCompletion completion = null;
        String fallbackReason = null;
        try {
            completion =
                    chatModelClient.complete(
                            new ChatModelClient.ChatRequest(
                                    "evidence-chat",
                                    prompt.modelName(),
                                    prompt.temperature(),
                                    prompt.maxTokens(),
                                    """
                                    You answer questions for a campus fund reimbursement reviewer.
                                    Use only the supplied case evidence. Do not approve, reject, pay, change state,
                                    change risk scores, or reveal secrets. Return JSON with answer and citations.
                                    citations must be an array of objects with type and id.
                                    """,
                                    prompt.content()));
            response = responseFromModel(completion.content());
        } catch (RuntimeException exception) {
            if (!chatModelProperties.isFallbackEnabled()) {
                throw exception;
            }
            fallbackReason = exception.getMessage();
            response = answerFromEvidence(guard.sanitizedInput(), evidence);
        }
        if (completion == null) {
            modelCallRecorder.failed(
                    caseId,
                    evidence.run() == null ? null : evidence.run().id(),
                    "EVIDENCE_CHAT",
                    prompt.modelName(),
                    prompt.version(),
                    prompt.content(),
                    guard.sanitizedInput(),
                    java.time.Duration.ofNanos(System.nanoTime() - startedNanos).toMillis(),
                    0,
                    fallbackReason == null ? "MODEL_FALLBACK" : fallbackReason);
        } else {
            modelCallRecorder.succeeded(
                    caseId,
                    evidence.run() == null ? null : evidence.run().id(),
                    "EVIDENCE_CHAT",
                    prompt.modelName(),
                    prompt.version(),
                    prompt.content(),
                    guard.sanitizedInput(),
                    json(response),
                    completion.promptTokens(),
                    completion.completionTokens(),
                    completion.latencyMs(),
                    completion.retryCount());
        }
        return response;
    }

    private EvidenceChatResponse responseFromModel(String content) {
        try {
            JsonNode root = objectMapper.readTree(stripJsonFence(content));
            String answer = root.path("answer").asText("");
            if (answer.isBlank()) {
                throw new IllegalStateException("answer is blank");
            }
            List<Citation> citations = new ArrayList<>();
            JsonNode citationNodes = root.get("citations");
            if (citationNodes != null && citationNodes.isArray()) {
                for (JsonNode node : citationNodes) {
                    String type = node.path("type").asText("");
                    String id = node.path("id").asText("");
                    if (!type.isBlank() && !id.isBlank()) {
                        citations.add(new Citation(type, id));
                    }
                }
            }
            return new EvidenceChatResponse(answer, citations);
        } catch (Exception exception) {
            throw new IllegalStateException("证据问答模型输出不是有效 JSON：" + exception.getMessage(), exception);
        }
    }

    private EvidenceChatResponse answerFromEvidence(
            String question, CaseEvidenceService.CaseEvidence evidence) {
        String normalized = question == null ? "" : question.trim();
        if (normalized.isBlank()) {
            return refused("请先输入要追问的当前经费申请问题。");
        }
        if (containsAny(normalized, "修改", "批准", "入账", "转账", "跳过", "token", "密钥")) {
            return new EvidenceChatResponse(
                    "该助手只能解释当前经费申请证据，不能修改状态、批准金额、风险分，也不能发起入账或泄露凭据。",
                    List.of(new Citation("BOUNDARY", "READ_ONLY_EVIDENCE_CHAT")));
        }
        if (containsAny(normalized, "为什么", "人工", "风险", "不能自动")) {
            if (evidence.risk() == null || evidence.risk().signals().isEmpty()) {
                return refused("当前经费申请还没有风险评估证据，无法判断为什么进入人工审核。");
            }
            List<Citation> citations = new ArrayList<>();
            String details =
                    evidence.risk().signals().stream()
                            .peek(signal -> citations.add(new Citation("RISK_SIGNAL", signal.code().name())))
                            .map(RiskSignal::message)
                            .distinct()
                            .collect(java.util.stream.Collectors.joining("；"));
            return new EvidenceChatResponse(
                    "当前经费申请的主要风险依据是：" + details + "。", citations);
        }
        if (containsAny(normalized, "制度", "引用", "政策")) {
            if (evidence.policyFindings().isEmpty()) {
                return refused("当前经费申请没有可引用的制度检索结果。");
            }
            List<Citation> citations =
                    evidence.policyFindings().stream()
                            .map(item -> new Citation("POLICY_CHUNK", String.valueOf(item.get("chunkId"))))
                            .toList();
            String answer =
                    evidence.policyFindings().stream()
                            .map(
                                    item ->
                                            "%s / %s"
                                                    .formatted(
                                                            item.get("policyCode"),
                                                            item.get("section")))
                            .distinct()
                            .collect(java.util.stream.Collectors.joining("；"));
            return new EvidenceChatResponse("当前经费申请引用的制度证据包括：" + answer + "。", citations);
        }
        if (containsAny(normalized, "tool", "mcp", "工具")) {
            if (evidence.toolCalls().isEmpty()) {
                return refused("当前经费申请没有 MCP Tool 调用记录。");
            }
            List<Citation> citations =
                    evidence.toolCalls().stream()
                            .map(call -> new Citation("TOOL_CALL", call.id().toString()))
                            .toList();
            String answer =
                    evidence.toolCalls().stream()
                            .map(ToolCallRepository.ToolCallDetail::toolName)
                            .distinct()
                            .collect(java.util.stream.Collectors.joining("、"));
            return new EvidenceChatResponse("当前经费申请记录过这些 Tool 调用：" + answer + "。", citations);
        }
        if (containsAny(normalized, "入账", "提交")) {
            boolean submitted =
                    evidence.toolCalls().stream()
                            .anyMatch(
                                    call ->
                                            call.writeOperation()
                                                    && "SUCCEEDED".equals(call.status())
                                                    && List.of("submit_fund_reimbursement", "submit_fund_posting")
                                                            .contains(call.toolName()));
            List<Citation> citations =
                    evidence.toolCalls().stream()
                            .filter(call -> call.writeOperation())
                            .map(call -> new Citation("TOOL_CALL", call.id().toString()))
                            .toList();
            if (citations.isEmpty()) {
                return refused("当前经费申请没有审批后写 Tool 调用证据。");
            }
            return new EvidenceChatResponse(
                    submitted ? "入账已经通过审批后的写 Tool 提交。" : "未看到成功的入账提交证据。",
                    citations);
        }
        return refused("当前证据链不足以回答这个问题。");
    }

    private EvidenceChatResponse refused(String message) {
        return new EvidenceChatResponse(message, List.of());
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("证据问答响应序列化失败", exception);
        }
    }

    private String evidenceJson(CaseEvidenceService.CaseEvidence evidence) {
        try {
            java.util.Map<String, Object> context = new java.util.LinkedHashMap<>();
            context.put("run", evidence.run());
            context.put("steps", evidence.steps());
            context.put("policyFindings", evidence.policyFindings());
            context.put("risk", evidence.risk());
            context.put("toolCalls", evidence.toolCalls());
            return objectMapper.writeValueAsString(context);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("证据问答上下文序列化失败", exception);
        }
    }

    private static String stripJsonFence(String content) {
        String normalized = content == null ? "" : content.trim();
        if (normalized.startsWith("```")) {
            normalized = normalized.replaceFirst("(?s)^```(?:json)?\\s*", "");
            normalized = normalized.replaceFirst("(?s)\\s*```$", "");
        }
        return normalized.trim();
    }

    private static boolean containsAny(String value, String... keywords) {
        for (String keyword : keywords) {
            if (value.toLowerCase(java.util.Locale.ROOT)
                    .contains(keyword.toLowerCase(java.util.Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static int estimateTokens(String value) {
        return value == null ? 0 : Math.max(1, value.length() / 4);
    }

    public record EvidenceChatResponse(String answer, List<Citation> citations) {
        public EvidenceChatResponse {
            citations = citations == null ? List.of() : List.copyOf(citations);
        }
    }

    public record Citation(String type, String id) {}
}

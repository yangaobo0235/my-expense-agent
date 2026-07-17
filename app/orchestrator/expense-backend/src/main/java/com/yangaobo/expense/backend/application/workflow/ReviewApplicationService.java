package com.yangaobo.expense.backend.application.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangaobo.expense.backend.application.ExpenseCaseApplicationService;
import com.yangaobo.expense.backend.application.ai.ChatModelClient;
import com.yangaobo.expense.backend.application.prompt.PromptRenderService;
import com.yangaobo.expense.backend.application.prompt.RenderedPrompt;
import com.yangaobo.expense.backend.domain.model.ExpenseCase;
import com.yangaobo.expense.backend.domain.risk.RiskAssessment;
import com.yangaobo.expense.common.domain.ExpenseCaseStatus;
import com.yangaobo.expense.common.error.MyExpenseAgentErrorCode;
import com.yangaobo.expense.common.error.MyExpenseAgentException;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReviewApplicationService {

    private final ReviewRepository reviewRepository;
    private final ExpenseCaseApplicationService caseService;
    private final PromptRenderService promptRenderService;
    private final ChatModelClient chatModelClient;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public ReviewApplicationService(
            ReviewRepository reviewRepository,
            ExpenseCaseApplicationService caseService,
            PromptRenderService promptRenderService,
            ChatModelClient chatModelClient,
            ObjectMapper objectMapper,
            Clock clock) {
        this.reviewRepository = reviewRepository;
        this.caseService = caseService;
        this.promptRenderService = promptRenderService;
        this.chatModelClient = chatModelClient;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public List<ReviewRepository.ReviewTask> openTasks() {
        return reviewRepository.findOpenTasks();
    }

    public List<ReviewRepository.ReviewTask> openTasks(Set<String> reviewerRoles) {
        return reviewRepository.findOpenTasks().stream()
                .filter(task -> canView(task, reviewerRoles))
                .toList();
    }

    public ReviewRepository.ReviewTask get(UUID taskId) {
        return reviewRepository.findById(taskId).orElseThrow(() -> notFound(taskId));
    }

    public ReviewRepository.ReviewTask get(UUID taskId, Set<String> reviewerRoles) {
        ReviewRepository.ReviewTask task = get(taskId);
        ensureReviewerCanView(task, reviewerRoles);
        return task;
    }

    public MoreInfoSuggestion suggestMoreInfo(UUID taskId) {
        ReviewRepository.ReviewTask task = get(taskId);
        return suggestMoreInfo(task);
    }

    public MoreInfoSuggestion suggestMoreInfo(UUID taskId, Set<String> reviewerRoles) {
        ReviewRepository.ReviewTask task = get(taskId);
        ensureReviewerCanHandle(task, reviewerRoles);
        return suggestMoreInfo(task);
    }

    private MoreInfoSuggestion suggestMoreInfo(ReviewRepository.ReviewTask task) {
        try {
            String taskContext =
                    objectMapper.writeValueAsString(
                            Map.of(
                                    "taskId",
                                    task.id(),
                                    "caseId",
                                    task.caseId(),
                                    "reasonCodes",
                                    task.reasonCodes(),
                                    "requiredEvidence",
                                    task.requiredEvidence(),
                                    "assigneeRole",
                                    task.assigneeRole(),
                                    "slaHours",
                                    task.slaHours()));
            RenderedPrompt prompt =
                    promptRenderService.render(
                            "more-info-suggestion",
                            Map.of("reviewTask", taskContext));
            ChatModelClient.ChatCompletion completion =
                    chatModelClient.complete(
                            new ChatModelClient.ChatRequest(
                                    "more-info-suggestion",
                                    prompt.modelName(),
                                    prompt.temperature(),
                                    prompt.maxTokens(),
                                    """
                                    You generate user-facing missing-information requests for expense review.
                                    Do not approve, reject, change status, or request secrets.
                                    Return JSON with userFacingMessage, requestedEvidence, reviewerQuestions.
                                    """,
                                    prompt.content()));
            return suggestionFromModel(completion.content());
        } catch (RuntimeException exception) {
            return fallbackMoreInfo(task);
        } catch (Exception exception) {
            return fallbackMoreInfo(task);
        }
    }

    private MoreInfoSuggestion fallbackMoreInfo(ReviewRepository.ReviewTask task) {
        List<String> evidence =
                task.requiredEvidence().isEmpty()
                        ? List.of("请补充能证明费用真实性、必要性和合规性的材料")
                        : task.requiredEvidence();
        List<String> questions =
                evidence.stream()
                        .map(ReviewApplicationService::questionForEvidence)
                        .distinct()
                        .toList();
        String message =
                """
                请补充以下材料或说明：%s。补充后财务会基于制度依据、票据一致性和风险信号继续审核。
                """
                        .formatted(String.join("、", evidence))
                        .trim();
        if (task.reasonCodes().stream().anyMatch(reason -> reason.contains("DUPLICATE"))) {
            message += " 当前案例存在疑似重复票据，请同时说明该票据与历史报销的关系。";
        }
        if (task.reasonCodes().stream().anyMatch(reason -> reason.contains("POLICY"))) {
            message += " 若存在特殊审批或例外场景，请上传对应审批记录。";
        }
        return new MoreInfoSuggestion(message, evidence, questions);
    }

    private MoreInfoSuggestion suggestionFromModel(String content) {
        try {
            JsonNode root = objectMapper.readTree(stripJsonFence(content));
            String message = root.path("userFacingMessage").asText("");
            if (message.isBlank()) {
                throw new IllegalStateException("userFacingMessage is blank");
            }
            return new MoreInfoSuggestion(
                    message,
                    textList(root, "requestedEvidence"),
                    textList(root, "reviewerQuestions"));
        } catch (Exception exception) {
            throw new IllegalStateException("补充材料建议模型输出不是有效 JSON：" + exception.getMessage(), exception);
        }
    }

    @Transactional
    public ExpenseCase approve(
            UUID taskId,
            long version,
            BigDecimal approvedAmount,
            String comment,
            String reviewerSubject,
            Set<String> reviewerRoles,
            String requestId) {
        ExpenseCase replay = replayedDecision(requestId);
        if (replay != null) {
            return replay;
        }
        ReviewRepository.ReviewTask task = get(taskId);
        ensureReviewerCanHandle(task, reviewerRoles);
        ExpenseCase expenseCase = waitingCase(task.caseId());
        BigDecimal amount =
                approvedAmount == null
                        ? expenseCase.claimedAmount().amount()
                        : approvedAmount;
        if (amount.signum() < 0
                || amount.compareTo(expenseCase.claimedAmount().amount()) > 0) {
            throw new MyExpenseAgentException(
                    MyExpenseAgentErrorCode.VALIDATION_FAILED,
                    "批准金额必须处于 0 到申报金额之间");
        }
        if (amount.compareTo(expenseCase.claimedAmount().amount()) != 0
                && (comment == null || comment.isBlank())) {
            throw new MyExpenseAgentException(
                    MyExpenseAgentErrorCode.VALIDATION_FAILED,
                    "修改批准金额时必须填写审核说明");
        }
        reviewRepository.completeTask(
                taskId, version, "APPROVED", reviewerSubject, comment, clock.instant());
        ExpenseCase approved =
                caseService.transition(expenseCase.id(), ExpenseCaseStatus.APPROVED);
        reviewRepository.saveDecision(
                approved,
                "APPROVED",
                amount,
                currentRisk(approved),
                List.of(),
                reviewerSubject,
                requestId,
                clock.instant());
        audit(task, reviewerSubject, requestId, "REVIEW_APPROVED", comment);
        return approved;
    }

    @Transactional
    public ExpenseCase reject(
            UUID taskId,
            long version,
            String comment,
            String reviewerSubject,
            Set<String> reviewerRoles,
            String requestId) {
        ExpenseCase replay = replayedDecision(requestId);
        if (replay != null) {
            return replay;
        }
        if (comment == null || comment.isBlank()) {
            throw new MyExpenseAgentException(
                    MyExpenseAgentErrorCode.VALIDATION_FAILED, "拒绝时必须填写审核说明");
        }
        ReviewRepository.ReviewTask task = get(taskId);
        ensureReviewerCanHandle(task, reviewerRoles);
        ExpenseCase expenseCase = waitingCase(task.caseId());
        reviewRepository.completeTask(
                taskId, version, "REJECTED", reviewerSubject, comment, clock.instant());
        ExpenseCase rejected =
                caseService.transition(expenseCase.id(), ExpenseCaseStatus.REJECTED);
        reviewRepository.saveDecision(
                rejected,
                "REJECTED",
                BigDecimal.ZERO,
                currentRisk(rejected),
                List.of(),
                reviewerSubject,
                requestId,
                clock.instant());
        audit(task, reviewerSubject, requestId, "REVIEW_REJECTED", comment);
        return rejected;
    }

    @Transactional
    public ExpenseCase requestMoreInfo(
            UUID taskId,
            long version,
            String comment,
            String reviewerSubject,
            Set<String> reviewerRoles,
            String requestId) {
        validateRequestId(requestId);
        ExpenseCase replay =
                reviewRepository
                        .findMoreInfoCaseId(requestId)
                        .map(caseService::getById)
                        .orElse(null);
        if (replay != null) {
            return replay;
        }
        if (comment == null || comment.isBlank()) {
            throw new MyExpenseAgentException(
                    MyExpenseAgentErrorCode.VALIDATION_FAILED, "要求补充材料时必须填写说明");
        }
        ReviewRepository.ReviewTask task = get(taskId);
        ensureReviewerCanHandle(task, reviewerRoles);
        ExpenseCase expenseCase = waitingCase(task.caseId());
        reviewRepository.requestMoreInfo(
                taskId, version, reviewerSubject, comment, clock.instant());
        audit(
                task,
                reviewerSubject,
                requestId,
                "REVIEW_MORE_INFO_REQUESTED",
                comment);
        return expenseCase;
    }

    public ExpenseCase approve(
            UUID taskId,
            long version,
            BigDecimal approvedAmount,
            String comment,
            String reviewerSubject,
            String requestId) {
        return approve(
                taskId,
                version,
                approvedAmount,
                comment,
                reviewerSubject,
                Set.of("FINANCE_ADMIN"),
                requestId);
    }

    public ExpenseCase reject(
            UUID taskId,
            long version,
            String comment,
            String reviewerSubject,
            String requestId) {
        return reject(taskId, version, comment, reviewerSubject, Set.of("FINANCE_ADMIN"), requestId);
    }

    public ExpenseCase requestMoreInfo(
            UUID taskId,
            long version,
            String comment,
            String reviewerSubject,
            String requestId) {
        return requestMoreInfo(taskId, version, comment, reviewerSubject, Set.of("FINANCE_ADMIN"), requestId);
    }

    private ExpenseCase waitingCase(UUID caseId) {
        ExpenseCase expenseCase = caseService.getById(caseId);
        if (expenseCase.status() != ExpenseCaseStatus.WAITING_HUMAN) {
            throw new MyExpenseAgentException(
                    MyExpenseAgentErrorCode.INVALID_STATE_TRANSITION,
                    "只有等待人工审核的案例可以处理");
        }
        return expenseCase;
    }

    private static void ensureReviewerCanHandle(
            ReviewRepository.ReviewTask task, Set<String> reviewerRoles) {
        if (!canHandle(task, reviewerRoles)) {
            throw new MyExpenseAgentException(
                    MyExpenseAgentErrorCode.ACCESS_DENIED,
                    "当前角色不能处理该审核任务，任务要求角色：" + task.assigneeRole());
        }
    }

    private static void ensureReviewerCanView(
            ReviewRepository.ReviewTask task, Set<String> reviewerRoles) {
        if (!canView(task, reviewerRoles)) {
            throw new MyExpenseAgentException(
                    MyExpenseAgentErrorCode.ACCESS_DENIED,
                    "当前角色不能查看该审核任务，任务要求角色：" + task.assigneeRole());
        }
    }

    private static boolean canView(
            ReviewRepository.ReviewTask task, Set<String> reviewerRoles) {
        Set<String> roles = reviewerRoles == null ? Set.of() : reviewerRoles;
        return roles.contains("AUDITOR") || canHandle(task, roles);
    }

    private static boolean canHandle(
            ReviewRepository.ReviewTask task, Set<String> reviewerRoles) {
        Set<String> roles = reviewerRoles == null ? Set.of() : reviewerRoles;
        return roles.contains("FINANCE_ADMIN") || roles.contains(task.assigneeRole());
    }

    private ExpenseCase replayedDecision(String requestId) {
        validateRequestId(requestId);
        return reviewRepository
                .findDecisionCaseId(requestId)
                .map(caseService::getById)
                .orElse(null);
    }

    private static void validateRequestId(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            throw new MyExpenseAgentException(
                    MyExpenseAgentErrorCode.VALIDATION_FAILED, "requestId不能为空");
        }
    }

    private static RiskAssessment currentRisk(ExpenseCase expenseCase) {
        int score = expenseCase.riskScore() == null ? 0 : expenseCase.riskScore();
        return new RiskAssessment(
                score,
                com.yangaobo.expense.backend.domain.model.RiskLevel.fromScore(score),
                score >= 30,
                List.of());
    }

    private void audit(
            ReviewRepository.ReviewTask task,
            String reviewer,
            String requestId,
            String action,
            String comment) {
        reviewRepository.appendAudit(
                task.caseId(),
                reviewer,
                action,
                "REVIEW_TASK",
                task.id().toString(),
                requestId,
                Map.of("comment", comment == null ? "" : comment),
                clock.instant());
    }

    private static MyExpenseAgentException notFound(UUID taskId) {
        return new MyExpenseAgentException(
                MyExpenseAgentErrorCode.EXPENSE_CASE_NOT_FOUND,
                "审核任务 %s 不存在".formatted(taskId));
    }

    private static String questionForEvidence(String evidence) {
        String normalized = evidence == null ? "" : evidence;
        if (normalized.contains("制度") || normalized.contains("审批")) {
            return "是否存在主管或财务预审批记录？";
        }
        if (normalized.contains("票据") || normalized.contains("发票")) {
            return "票据抬头、金额、日期是否与本次报销一致？";
        }
        if (normalized.contains("重复")) {
            return "该票据是否曾在其他报销单中提交过？";
        }
        if (normalized.contains("行程") || normalized.contains("差旅")) {
            return "费用发生日期是否落在已批准的出差周期内？";
        }
        return "该材料能否证明本次费用的真实性和业务必要性？";
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

    private static String stripJsonFence(String content) {
        String normalized = content == null ? "" : content.trim();
        if (normalized.startsWith("```")) {
            normalized = normalized.replaceFirst("(?s)^```(?:json)?\\s*", "");
            normalized = normalized.replaceFirst("(?s)\\s*```$", "");
        }
        return normalized.trim();
    }

    public record MoreInfoSuggestion(
            String userFacingMessage, List<String> requestedEvidence, List<String> reviewerQuestions) {}
}

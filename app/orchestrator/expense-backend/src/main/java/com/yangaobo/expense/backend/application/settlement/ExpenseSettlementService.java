package com.yangaobo.expense.backend.application.settlement;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangaobo.expense.backend.application.ExpenseCaseApplicationService;
import com.yangaobo.expense.backend.application.extraction.ExtractedExpenseDocument;
import com.yangaobo.expense.backend.application.workflow.ReviewRepository;
import com.yangaobo.expense.backend.domain.model.ExpenseCase;
import com.yangaobo.expense.backend.domain.model.ExpenseDocument;
import com.yangaobo.expense.backend.domain.repository.ExpenseDocumentRepository;
import com.yangaobo.expense.common.domain.ExpenseCaseStatus;
import com.yangaobo.expense.common.error.CampusFundFlowErrorCode;
import com.yangaobo.expense.common.error.CampusFundFlowException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ExpenseSettlementService {

    private static final String BUDGET_TOOL = "debit_project_budget";
    private static final String REIMBURSEMENT_TOOL = "submit_fund_reimbursement";
    private static final String POSTING_TOOL = "submit_fund_posting";
    private static final String HISTORY_TOOL = "record_fund_reimbursement_history";

    private final ExpenseCaseApplicationService caseService;
    private final ReviewRepository reviewRepository;
    private final ToolCallRepository toolCallRepository;
    private final ExpenseDocumentRepository documentRepository;
    private final ApprovedExpenseWriter writer;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public ExpenseSettlementService(
            ExpenseCaseApplicationService caseService,
            ReviewRepository reviewRepository,
            ToolCallRepository toolCallRepository,
            ExpenseDocumentRepository documentRepository,
            ApprovedExpenseWriter writer,
            ObjectMapper objectMapper,
            Clock clock) {
        this.caseService = caseService;
        this.reviewRepository = reviewRepository;
        this.toolCallRepository = toolCallRepository;
        this.documentRepository = documentRepository;
        this.writer = writer;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public SettlementResult settle(
            UUID caseId, String requestId, String actorSubject) {
        ExpenseCase expenseCase = caseService.getById(caseId);
        if (expenseCase.status() != ExpenseCaseStatus.APPROVED) {
            throw new CampusFundFlowException(
                    CampusFundFlowErrorCode.INVALID_STATE_TRANSITION,
                    "只有已批准的经费申请才能发起入账");
        }
        ReviewRepository.ExpenseDecision decision =
                reviewRepository
                        .findDecisionByCaseId(caseId)
                        .filter(item -> "APPROVED".equals(item.decision()))
                        .orElseThrow(
                                () ->
                                        new CampusFundFlowException(
                                                CampusFundFlowErrorCode
                                                        .INVALID_STATE_TRANSITION,
                                                "经费申请缺少有效的批准决定"));
        String normalizedRequestId = required(requestId, "requestId");
        String approvalReference =
                decision.requestId() == null
                        ? "decision:" + caseId
                        : "decision:" + decision.requestId();
        List<HistoryCandidate> historyCandidates = historyCandidates(expenseCase, decision);
        String budgetRequestId = childRequestId(normalizedRequestId, "budget");
        String reimbursementRequestId = childRequestId(normalizedRequestId, "reimbursement");
        String postingRequestId = childRequestId(normalizedRequestId, "posting");
        boolean completedBefore =
                succeeded(BUDGET_TOOL, budgetRequestId)
                        && succeeded(REIMBURSEMENT_TOOL, reimbursementRequestId)
                        && succeeded(POSTING_TOOL, postingRequestId)
                        && historyCandidates.stream()
                                .allMatch(
                                        candidate ->
                                                succeeded(
                                                        HISTORY_TOOL,
                                                        historyRequestId(
                                                                normalizedRequestId,
                                                                candidate.documentSha256())));

        Map<String, Object> budgetDebit =
                execute(
                        caseId,
                        BUDGET_TOOL,
                        budgetRequestId,
                        actorSubject,
                        approvalReference,
                        Map.of(
                                "caseId",
                                caseId.toString(),
                                "projectCode",
                                expenseCase.projectCode(),
                                "applicantId",
                                expenseCase.ownerSubject(),
                                "amount",
                                decision.approvedAmount(),
                                "currency",
                                decision.currency()),
                        () ->
                                writer.debitProjectBudget(
                                        caseId,
                                        decision.approvedAmount(),
                                        decision.currency(),
                                        budgetRequestId,
                                        actorSubject,
                                        approvalReference));
        UUID budgetDebitId =
                UUID.fromString(
                        required(
                                String.valueOf(budgetDebit.get("debitId")),
                                "debitId"));

        Map<String, Object> reimbursement =
                execute(
                        caseId,
                        REIMBURSEMENT_TOOL,
                        reimbursementRequestId,
                        actorSubject,
                        approvalReference,
                        Map.of(
                                "caseId", caseId.toString(),
                                "amount", decision.approvedAmount(),
                                "currency", decision.currency()),
                        () ->
                                writer.submitReimbursement(
                                        caseId,
                                        decision.approvedAmount(),
                                        decision.currency(),
                                        reimbursementRequestId,
                                        actorSubject,
                                        approvalReference));
        UUID reimbursementId =
                UUID.fromString(
                        required(
                                String.valueOf(
                                        reimbursement.get(
                                                "reimbursementId")),
                                "reimbursementId"));

        Map<String, Object> posting =
                execute(
                        caseId,
                        POSTING_TOOL,
                        postingRequestId,
                        actorSubject,
                        approvalReference,
                        Map.of(
                                "reimbursementId",
                                reimbursementId.toString(),
                                "amount",
                                decision.approvedAmount(),
                                "currency",
                                decision.currency()),
                        () ->
                                writer.submitPayment(
                                        caseId,
                                        reimbursementId,
                                        decision.approvedAmount(),
                                        decision.currency(),
                                        postingRequestId,
                                        actorSubject,
                                        approvalReference));
        UUID postingId =
                UUID.fromString(
                        required(
                                String.valueOf(posting.get("postingId")),
                                "postingId"));
        List<UUID> historyRecordIds = new ArrayList<>();
        for (HistoryCandidate candidate : historyCandidates) {
            String historyRequestId =
                    historyRequestId(normalizedRequestId, candidate.documentSha256());
            Map<String, Object> history =
                    execute(
                            caseId,
                            HISTORY_TOOL,
                            historyRequestId,
                            actorSubject,
                            approvalReference,
                            Map.of(
                                    "caseId",
                                    caseId.toString(),
                                    "sellerName",
                                    candidate.sellerName(),
                                    "amount",
                                    candidate.amount(),
                                    "currency",
                                    candidate.currency(),
                                    "expenseDate",
                                    candidate.expenseDate().toString(),
                                    "documentSha256",
                                    candidate.documentSha256()),
                            () ->
                                    writer.recordReimbursementHistory(
                                            caseId,
                                            candidate.sellerName(),
                                            candidate.amount(),
                                            candidate.currency(),
                                            candidate.expenseDate(),
                                            candidate.documentSha256(),
                                            historyRequestId,
                                            actorSubject,
                                            approvalReference));
            historyRecordIds.add(
                    UUID.fromString(
                            required(
                                    String.valueOf(history.get("historyId")),
                                    "historyId")));
        }
        SettlementResult settlement =
                new SettlementResult(
                caseId,
                budgetDebitId,
                reimbursementId,
                postingId,
                historyRecordIds,
                decision.approvedAmount(),
                decision.currency(),
                String.valueOf(posting.get("status")));
        if (!completedBefore) {
            reviewRepository.appendAudit(
                    caseId,
                    actorSubject,
                    "FUND_POSTED",
                    "EXPENSE_CASE",
                    caseId.toString(),
                    normalizedRequestId,
                    Map.of(
                            "reimbursementId",
                            reimbursementId.toString(),
                            "budgetDebitId",
                            budgetDebitId.toString(),
                            "postingId",
                            settlement.postingId().toString(),
                            "historyRecordCount",
                            historyRecordIds.size(),
                            "amount",
                            decision.approvedAmount().toPlainString(),
                            "currency",
                            decision.currency()),
                    clock.instant());
        }
        return settlement;
    }

    private boolean succeeded(String toolName, String requestId) {
        return toolCallRepository
                .find(toolName, requestId)
                .map(call -> "SUCCEEDED".equals(call.status()))
                .orElse(false);
    }

    private List<HistoryCandidate> historyCandidates(
            ExpenseCase expenseCase, ReviewRepository.ExpenseDecision decision) {
        List<HistoryCandidate> candidates = new ArrayList<>();
        for (ExpenseDocument document : documentRepository.findByCaseId(expenseCase.id())) {
            var extraction = documentRepository.findExtractionByDocumentId(document.id());
            if (extraction.isEmpty()) {
                continue;
            }
            try {
                ExtractedExpenseDocument extracted =
                        objectMapper.readValue(
                                extraction.get().resultJson(), ExtractedExpenseDocument.class);
                String sellerName =
                        extracted.sellerName() == null || extracted.sellerName().isBlank()
                                ? "未识别商户"
                                : extracted.sellerName().trim();
                java.math.BigDecimal amount =
                        extracted.totalAmount() == null
                                ? decision.approvedAmount()
                                : extracted.totalAmount();
                String currency =
                        extracted.currency() == null || extracted.currency().isBlank()
                                ? decision.currency()
                                : extracted.currency().trim().toUpperCase();
                LocalDate expenseDate =
                        extracted.issueDate() == null
                                ? LocalDate.ofInstant(expenseCase.createdAt(), ZoneOffset.UTC)
                                : extracted.issueDate();
                candidates.add(
                        new HistoryCandidate(
                                sellerName,
                                amount,
                                currency,
                                expenseDate,
                                document.sha256()));
            } catch (JsonProcessingException exception) {
                throw new CampusFundFlowException(
                        CampusFundFlowErrorCode.VALIDATION_FAILED,
                        "票据提取结果无法写入报销历史");
            }
        }
        if (candidates.isEmpty()) {
            throw new CampusFundFlowException(
                    CampusFundFlowErrorCode.VALIDATION_FAILED,
                    "没有可用于入账历史回写的票据提取结果");
        }
        return List.copyOf(candidates);
    }

    private static String historyRequestId(String requestId, String sha256) {
        String hashPart = sha256.length() <= 16 ? sha256 : sha256.substring(0, 16);
        return childRequestId(requestId, "history:" + hashPart);
    }

    private static String childRequestId(String requestId, String suffix) {
        String candidate = requestId + ":" + suffix;
        return candidate.length() <= 128 ? candidate : hash(requestId) + ":" + suffix;
    }

    private Map<String, Object> execute(
            UUID caseId,
            String toolName,
            String requestId,
            String actorSubject,
            String approvalReference,
            Map<String, Object> input,
            java.util.function.Supplier<
                            ApprovedExpenseWriter.WriteResult>
                    operation) {
        var existing = toolCallRepository.find(toolName, requestId);
        if (existing.isPresent()
                && "SUCCEEDED".equals(existing.get().status())) {
            return existing.get().output();
        }
        Instant startedAt = clock.instant();
        ToolCallRepository.ToolCall call =
                toolCallRepository.start(
                        caseId,
                        toolName,
                        requestId,
                        hash(input),
                        actorSubject,
                        approvalReference,
                        startedAt);
        if ("SUCCEEDED".equals(call.status())) {
            return call.output();
        }
        try {
            ApprovedExpenseWriter.WriteResult result = operation.get();
            if (!result.success()) {
                throw new CampusFundFlowException(
                        CampusFundFlowErrorCode.DEPENDENCY_UNAVAILABLE,
                        toolName + " 返回失败");
            }
            toolCallRepository.succeed(
                    call.id(),
                    hash(result.output()),
                    result.output(),
                    elapsed(startedAt),
                    clock.instant());
            return result.output();
        } catch (RuntimeException exception) {
            toolCallRepository.fail(
                    call.id(),
                    errorCode(exception),
                    elapsed(startedAt),
                    clock.instant());
            throw exception;
        }
    }

    private long elapsed(Instant startedAt) {
        return Math.max(
                0,
                Duration.between(startedAt, clock.instant()).toMillis());
    }

    private static String hash(Object value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(
                                            String.valueOf(value)
                                                    .getBytes(
                                                            StandardCharsets
                                                                    .UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String errorCode(RuntimeException exception) {
        return exception instanceof CampusFundFlowException flow
                ? flow.code().name()
                : CampusFundFlowErrorCode.INTERNAL_ERROR.name();
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank() || "null".equals(value)) {
            throw new CampusFundFlowException(
                    CampusFundFlowErrorCode.VALIDATION_FAILED,
                    field + "不能为空");
        }
        return value.trim();
    }

    public record SettlementResult(
            UUID caseId,
            UUID budgetDebitId,
            UUID reimbursementId,
            UUID postingId,
            List<UUID> historyRecordIds,
            java.math.BigDecimal amount,
            String currency,
            String status) {

        public SettlementResult {
            historyRecordIds =
                    historyRecordIds == null ? List.of() : List.copyOf(historyRecordIds);
        }
    }

    private record HistoryCandidate(
            String sellerName,
            java.math.BigDecimal amount,
            String currency,
            LocalDate expenseDate,
            String documentSha256) {}
}

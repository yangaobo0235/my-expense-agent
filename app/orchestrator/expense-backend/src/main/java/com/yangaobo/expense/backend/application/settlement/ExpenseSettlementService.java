package com.yangaobo.expense.backend.application.settlement;

import com.yangaobo.expense.backend.application.ExpenseCaseApplicationService;
import com.yangaobo.expense.backend.application.workflow.ReviewRepository;
import com.yangaobo.expense.backend.domain.model.ExpenseCase;
import com.yangaobo.expense.common.domain.ExpenseCaseStatus;
import com.yangaobo.expense.common.error.ExpenseFlowErrorCode;
import com.yangaobo.expense.common.error.ExpenseFlowException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ExpenseSettlementService {

    private static final String REIMBURSEMENT_TOOL = "submit_reimbursement";
    private static final String PAYMENT_TOOL = "submit_payment";

    private final ExpenseCaseApplicationService caseService;
    private final ReviewRepository reviewRepository;
    private final ToolCallRepository toolCallRepository;
    private final ApprovedExpenseWriter writer;
    private final Clock clock;

    public ExpenseSettlementService(
            ExpenseCaseApplicationService caseService,
            ReviewRepository reviewRepository,
            ToolCallRepository toolCallRepository,
            ApprovedExpenseWriter writer,
            Clock clock) {
        this.caseService = caseService;
        this.reviewRepository = reviewRepository;
        this.toolCallRepository = toolCallRepository;
        this.writer = writer;
        this.clock = clock;
    }

    public SettlementResult settle(
            UUID caseId, String requestId, String actorSubject) {
        ExpenseCase expenseCase = caseService.getById(caseId);
        if (expenseCase.status() != ExpenseCaseStatus.APPROVED) {
            throw new ExpenseFlowException(
                    ExpenseFlowErrorCode.INVALID_STATE_TRANSITION,
                    "只有已批准的费用案例才能结算");
        }
        ReviewRepository.ExpenseDecision decision =
                reviewRepository
                        .findDecisionByCaseId(caseId)
                        .filter(item -> "APPROVED".equals(item.decision()))
                        .orElseThrow(
                                () ->
                                        new ExpenseFlowException(
                                                ExpenseFlowErrorCode
                                                        .INVALID_STATE_TRANSITION,
                                                "案例缺少有效的批准决定"));
        String normalizedRequestId = required(requestId, "requestId");
        String approvalReference =
                decision.requestId() == null
                        ? "decision:" + caseId
                        : "decision:" + decision.requestId();
        boolean completedBefore =
                succeeded(
                                REIMBURSEMENT_TOOL,
                                normalizedRequestId + ":reimbursement")
                        && succeeded(
                                PAYMENT_TOOL,
                                normalizedRequestId + ":payment");

        Map<String, Object> reimbursement =
                execute(
                        caseId,
                        REIMBURSEMENT_TOOL,
                        normalizedRequestId + ":reimbursement",
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
                                        normalizedRequestId + ":reimbursement",
                                        actorSubject,
                                        approvalReference));
        UUID reimbursementId =
                UUID.fromString(
                        required(
                                String.valueOf(
                                        reimbursement.get(
                                                "reimbursementId")),
                                "reimbursementId"));

        Map<String, Object> payment =
                execute(
                        caseId,
                        PAYMENT_TOOL,
                        normalizedRequestId + ":payment",
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
                                        normalizedRequestId + ":payment",
                                        actorSubject,
                                        approvalReference));
        SettlementResult settlement =
                new SettlementResult(
                caseId,
                reimbursementId,
                UUID.fromString(
                        required(
                                String.valueOf(payment.get("paymentId")),
                                "paymentId")),
                decision.approvedAmount(),
                decision.currency(),
                String.valueOf(payment.get("status")));
        if (!completedBefore) {
            reviewRepository.appendAudit(
                    caseId,
                    actorSubject,
                    "EXPENSE_SETTLED",
                    "EXPENSE_CASE",
                    caseId.toString(),
                    normalizedRequestId,
                    Map.of(
                            "reimbursementId",
                            reimbursementId.toString(),
                            "paymentId",
                            settlement.paymentId().toString(),
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
                throw new ExpenseFlowException(
                        ExpenseFlowErrorCode.DEPENDENCY_UNAVAILABLE,
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
        return exception instanceof ExpenseFlowException flow
                ? flow.code().name()
                : ExpenseFlowErrorCode.INTERNAL_ERROR.name();
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank() || "null".equals(value)) {
            throw new ExpenseFlowException(
                    ExpenseFlowErrorCode.VALIDATION_FAILED,
                    field + "不能为空");
        }
        return value.trim();
    }

    public record SettlementResult(
            UUID caseId,
            UUID reimbursementId,
            UUID paymentId,
            java.math.BigDecimal amount,
            String currency,
            String status) {}
}

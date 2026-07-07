package com.yangaobo.expense.backend.application.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yangaobo.expense.backend.application.ExpenseCaseApplicationService;
import com.yangaobo.expense.backend.application.workflow.ReviewRepository;
import com.yangaobo.expense.backend.domain.model.ExpenseCase;
import com.yangaobo.expense.backend.domain.model.Money;
import com.yangaobo.expense.common.domain.ExpenseCaseStatus;
import com.yangaobo.expense.common.error.ExpenseFlowException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ExpenseSettlementServiceTest {

    private final ExpenseCaseApplicationService caseService =
            mock(ExpenseCaseApplicationService.class);
    private final ReviewRepository reviewRepository =
            mock(ReviewRepository.class);
    private final ApprovedExpenseWriter writer =
            mock(ApprovedExpenseWriter.class);
    private final InMemoryToolCallRepository toolCalls =
            new InMemoryToolCallRepository();
    private final ExpenseSettlementService service =
            new ExpenseSettlementService(
                    caseService,
                    reviewRepository,
                    toolCalls,
                    writer,
                    Clock.fixed(
                            Instant.parse("2026-06-21T00:00:00Z"),
                            ZoneOffset.UTC));

    @Test
    void shouldUseHumanApprovedAmountForBothWrites() {
        UUID caseId = UUID.randomUUID();
        UUID reimbursementId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        approved(caseId, new BigDecimal("500.00"));
        when(writer.submitReimbursement(
                        eq(caseId),
                        eq(new BigDecimal("500.00")),
                        eq("CNY"),
                        any(),
                        eq("finance01"),
                        any()))
                .thenReturn(
                        result(
                                "reimbursementId",
                                reimbursementId,
                                "status",
                                "SUBMITTED"));
        when(writer.submitPayment(
                        eq(caseId),
                        eq(reimbursementId),
                        eq(new BigDecimal("500.00")),
                        eq("CNY"),
                        any(),
                        eq("finance01"),
                        any()))
                .thenReturn(
                        result(
                                "paymentId",
                                paymentId,
                                "status",
                                "SIMULATED_PAID"));

        var settlement =
                service.settle(caseId, "settle-1", "finance01");

        assertThat(settlement.amount())
                .isEqualByComparingTo("500.00");
        assertThat(settlement.reimbursementId())
                .isEqualTo(reimbursementId);
        assertThat(settlement.paymentId()).isEqualTo(paymentId);
    }

    @Test
    void shouldResumeOnlyPaymentAfterPaymentFailure() {
        UUID caseId = UUID.randomUUID();
        UUID reimbursementId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        approved(caseId, new BigDecimal("500.00"));
        when(writer.submitReimbursement(any(), any(), any(), any(), any(), any()))
                .thenReturn(
                        result(
                                "reimbursementId",
                                reimbursementId,
                                "status",
                                "SUBMITTED"));
        when(writer.submitPayment(
                        any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new ExpenseFlowException(
                        com.yangaobo.expense.common.error.ExpenseFlowErrorCode
                                .DEPENDENCY_UNAVAILABLE,
                        "payment unavailable"))
                .thenReturn(
                        result(
                                "paymentId",
                                paymentId,
                                "status",
                                "SIMULATED_PAID"));

        assertThatThrownBy(
                        () ->
                                service.settle(
                                        caseId, "settle-2", "finance01"))
                .isInstanceOf(ExpenseFlowException.class);

        var resumed =
                service.settle(caseId, "settle-2", "finance01");

        assertThat(resumed.paymentId()).isEqualTo(paymentId);
        verify(writer, times(1))
                .submitReimbursement(any(), any(), any(), any(), any(), any());
        verify(writer, times(2))
                .submitPayment(
                        any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void shouldRejectNonApprovedCaseBeforeCallingTools() {
        UUID caseId = UUID.randomUUID();
        when(caseService.getById(caseId))
                .thenReturn(expenseCase(caseId, ExpenseCaseStatus.REJECTED));

        assertThatThrownBy(
                        () ->
                                service.settle(
                                        caseId, "settle-3", "finance01"))
                .isInstanceOf(ExpenseFlowException.class)
                .hasMessageContaining("只有已批准");
        verify(writer, never())
                .submitReimbursement(any(), any(), any(), any(), any(), any());
    }

    private void approved(UUID caseId, BigDecimal amount) {
        when(caseService.getById(caseId))
                .thenReturn(expenseCase(caseId, ExpenseCaseStatus.APPROVED));
        when(reviewRepository.findDecisionByCaseId(caseId))
                .thenReturn(
                        Optional.of(
                                new ReviewRepository.ExpenseDecision(
                                        caseId,
                                        "APPROVED",
                                        amount,
                                        "CNY",
                                        "reviewer01",
                                        "review-1",
                                        Instant.parse(
                                                "2026-06-21T00:00:00Z"))));
    }

    private static ApprovedExpenseWriter.WriteResult result(
            String idKey, UUID id, String statusKey, String status) {
        return new ApprovedExpenseWriter.WriteResult(
                true, Map.of(idKey, id.toString(), statusKey, status));
    }

    private static ExpenseCase expenseCase(
            UUID id, ExpenseCaseStatus status) {
        Instant now = Instant.parse("2026-06-21T00:00:00Z");
        return new ExpenseCase(
                id,
                "EF-20260621-001",
                "employee01",
                "费用员工",
                "IT",
                "差旅报销",
                new Money(new BigDecimal("600.00"), "CNY"),
                status,
                null,
                null,
                null,
                null,
                0,
                now,
                now);
    }

    private static final class InMemoryToolCallRepository
            implements ToolCallRepository {

        private final Map<String, ToolCall> calls = new HashMap<>();

        @Override
        public Optional<ToolCall> find(String toolName, String requestId) {
            return Optional.ofNullable(calls.get(key(toolName, requestId)));
        }

        @Override
        public java.util.List<ToolCallDetail> findByCaseId(UUID caseId) {
            return java.util.List.of();
        }

        @Override
        public ToolCall start(
                UUID caseId,
                String toolName,
                String requestId,
                String inputHash,
                String actorSubject,
                String approvalReference,
                Instant now) {
            ToolCall call =
                    new ToolCall(
                            calls.getOrDefault(
                                            key(toolName, requestId),
                                            new ToolCall(
                                                    UUID.randomUUID(),
                                                    caseId,
                                                    toolName,
                                                    requestId,
                                                    "RUNNING",
                                                    Map.of()))
                                    .id(),
                            caseId,
                            toolName,
                            requestId,
                            "RUNNING",
                            Map.of());
            calls.put(key(toolName, requestId), call);
            return call;
        }

        @Override
        public void succeed(
                UUID id,
                String outputHash,
                Map<String, Object> output,
                long durationMs,
                Instant now) {
            replace(id, "SUCCEEDED", output);
        }

        @Override
        public void fail(
                UUID id,
                String errorCode,
                long durationMs,
                Instant now) {
            replace(id, "FAILED", Map.of());
        }

        private void replace(
                UUID id, String status, Map<String, Object> output) {
            calls.replaceAll(
                    (key, value) ->
                            value.id().equals(id)
                                    ? new ToolCall(
                                            id,
                                            value.caseId(),
                                            value.toolName(),
                                            value.requestId(),
                                            status,
                                            output)
                                    : value);
        }

        private static String key(String tool, String request) {
            return tool + "|" + request;
        }
    }
}

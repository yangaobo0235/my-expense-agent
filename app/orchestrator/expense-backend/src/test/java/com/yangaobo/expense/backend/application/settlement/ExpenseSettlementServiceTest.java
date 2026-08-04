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
import com.yangaobo.expense.backend.domain.model.ExpenseDocument;
import com.yangaobo.expense.backend.domain.model.Money;
import com.yangaobo.expense.backend.domain.repository.ExpenseDocumentRepository;
import com.yangaobo.expense.backend.domain.repository.StoredExtractionResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangaobo.expense.common.domain.ExpenseCaseStatus;
import com.yangaobo.expense.common.error.MyExpenseAgentException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
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
    private final ExpenseDocumentRepository documentRepository =
            mock(ExpenseDocumentRepository.class);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final InMemoryToolCallRepository toolCalls =
            new InMemoryToolCallRepository();
    private final ExpenseSettlementService service =
            new ExpenseSettlementService(
                    caseService,
                    reviewRepository,
                    toolCalls,
                    documentRepository,
                    writer,
                    objectMapper,
                    Clock.fixed(
                            Instant.parse("2026-06-21T00:00:00Z"),
                            ZoneOffset.UTC));

    @Test
    void shouldUseHumanApprovedAmountForBothWrites() {
        UUID caseId = UUID.randomUUID();
        UUID reimbursementId = UUID.randomUUID();
        UUID postingId = UUID.randomUUID();
        UUID budgetDebitId = UUID.randomUUID();
        UUID historyId = UUID.randomUUID();
        approved(caseId, new BigDecimal("500.00"));
        when(writer.debitProjectBudget(any(), any(), any(), any(), any(), any()))
                .thenReturn(result("debitId", budgetDebitId, "status", "DEBITED"));
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
                                "postingId",
                                postingId,
                                "status",
                                "SIMULATED_PAID"));
        when(writer.recordReimbursementHistory(
                        any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(result("historyId", historyId, "status", "RECORDED"));

        var settlement =
                service.settle(caseId, "settle-1", "finance01");

        assertThat(settlement.amount())
                .isEqualByComparingTo("500.00");
        assertThat(settlement.reimbursementId())
                .isEqualTo(reimbursementId);
        assertThat(settlement.postingId()).isEqualTo(postingId);
        assertThat(settlement.budgetDebitId()).isEqualTo(budgetDebitId);
        assertThat(settlement.historyRecordIds()).containsExactly(historyId);
    }

    @Test
    void shouldResumeOnlyPaymentAfterPaymentFailure() {
        UUID caseId = UUID.randomUUID();
        UUID reimbursementId = UUID.randomUUID();
        UUID postingId = UUID.randomUUID();
        UUID budgetDebitId = UUID.randomUUID();
        UUID historyId = UUID.randomUUID();
        approved(caseId, new BigDecimal("500.00"));
        when(writer.debitProjectBudget(any(), any(), any(), any(), any(), any()))
                .thenReturn(result("debitId", budgetDebitId, "status", "DEBITED"));
        when(writer.submitReimbursement(any(), any(), any(), any(), any(), any()))
                .thenReturn(
                        result(
                                "reimbursementId",
                                reimbursementId,
                                "status",
                                "SUBMITTED"));
        when(writer.submitPayment(
                        any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new MyExpenseAgentException(
                        com.yangaobo.expense.common.error.MyExpenseAgentErrorCode
                                .DEPENDENCY_UNAVAILABLE,
                        "payment unavailable"))
                .thenReturn(
                        result(
                                "postingId",
                                postingId,
                                "status",
                                "SIMULATED_PAID"));
        when(writer.recordReimbursementHistory(
                        any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(result("historyId", historyId, "status", "RECORDED"));

        assertThatThrownBy(
                        () ->
                                service.settle(
                                        caseId, "settle-2", "finance01"))
                .isInstanceOf(MyExpenseAgentException.class);

        var resumed =
                service.settle(caseId, "settle-2", "finance01");

        assertThat(resumed.postingId()).isEqualTo(postingId);
        verify(writer, times(1))
                .debitProjectBudget(any(), any(), any(), any(), any(), any());
        verify(writer, times(1))
                .submitReimbursement(any(), any(), any(), any(), any(), any());
        verify(writer, times(2))
                .submitPayment(
                        any(), any(), any(), any(), any(), any(), any());
        verify(writer, times(1))
                .recordReimbursementHistory(
                        any(), any(), any(), any(), any(), any(), any(), any(), any());
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
                .isInstanceOf(MyExpenseAgentException.class)
                .hasMessageContaining("只有已批准");
        verify(writer, never())
                .submitReimbursement(any(), any(), any(), any(), any(), any());
        verify(writer, never())
                .debitProjectBudget(any(), any(), any(), any(), any(), any());
    }

    @Test
    void shouldNotReplayWriteThatRequiresManualRecovery() {
        UUID caseId = UUID.randomUUID();
        approved(caseId, new BigDecimal("500.00"));
        when(writer.debitProjectBudget(any(), any(), any(), any(), any(), any()))
                .thenReturn(result("debitId", UUID.randomUUID(), "status", "DEBITED"));
        when(writer.submitReimbursement(any(), any(), any(), any(), any(), any()))
                .thenReturn(
                        result(
                                "reimbursementId",
                                UUID.randomUUID(),
                                "status",
                                "SUBMITTED"));
        when(writer.submitPayment(any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("invalid downstream response"));

        assertThatThrownBy(() -> service.settle(caseId, "settle-manual", "finance01"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.settle(caseId, "settle-manual", "finance01"))
                .isInstanceOf(MyExpenseAgentException.class)
                .hasMessageContaining("不能自动重放");

        verify(writer, times(1))
                .submitPayment(any(), any(), any(), any(), any(), any(), any());
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
                                        "collegeReviewer01",
                                        "review-1",
                                        Instant.parse(
                                                "2026-06-21T00:00:00Z"))));
        UUID documentId = UUID.randomUUID();
        Instant now = Instant.parse("2026-06-21T00:00:00Z");
        ExpenseDocument document =
                new ExpenseDocument(
                        documentId,
                        caseId,
                        "invoice.txt",
                        "text/plain",
                        128,
                        "a".repeat(64),
                        "fund-applications/" + caseId + "/invoice.txt",
                        now,
                        now);
        when(documentRepository.findByCaseId(caseId)).thenReturn(List.of(document));
        when(documentRepository.findExtractionByDocumentId(documentId))
                .thenReturn(
                        Optional.of(
                                new StoredExtractionResult(
                                        "INVOICE",
                                        0.95,
                                        """
                                        {"documentType":"INVOICE","invoiceNumber":"INV-2026-001",
                                         "sellerName":"南京青奥酒店","buyerName":"江南大学",
                                         "issueDate":"2026-06-20","totalAmount":500.00,
                                         "currency":"CNY","items":[{"description":"竞赛住宿费",
                                         "quantity":1,"unitPrice":500.00,"amount":500.00}],
                                         "confidence":0.95,"warnings":[]}
                                        """,
                                        List.of(),
                                        "test-extractor",
                                        "v1",
                                        "b".repeat(64))));
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
                "CF-20260621-001",
                "student01",
                "李明",
                "CS-SRTP",
                "蓝桥杯竞赛差旅报销",
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
            ToolCall existing = calls.get(key(toolName, requestId));
            if (existing != null && "SUCCEEDED".equals(existing.status())) {
                return existing;
            }
            if (existing != null && !"FAILED_RETRYABLE".equals(existing.status())) {
                throw new MyExpenseAgentException(
                        com.yangaobo.expense.common.error.MyExpenseAgentErrorCode
                                .INVALID_STATE_TRANSITION,
                        "该写 Tool 已转人工或完成补偿，不能自动重放");
            }
            ToolCall call =
                    new ToolCall(
                            existing == null ? UUID.randomUUID() : existing.id(),
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
            replace(id, "FAILED_MANUAL", Map.of());
        }

        @Override
        public void fail(
                UUID id,
                String errorCode,
                boolean retryable,
                long durationMs,
                Instant now) {
            replace(id, retryable ? "FAILED_RETRYABLE" : "FAILED_MANUAL", Map.of());
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

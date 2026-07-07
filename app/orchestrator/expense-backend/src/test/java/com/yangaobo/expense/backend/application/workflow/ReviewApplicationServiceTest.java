package com.yangaobo.expense.backend.application.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangaobo.expense.backend.application.ExpenseCaseApplicationService;
import com.yangaobo.expense.backend.application.ai.ChatModelClient;
import com.yangaobo.expense.backend.application.prompt.PromptRenderService;
import com.yangaobo.expense.backend.domain.model.ExpenseCase;
import com.yangaobo.expense.backend.domain.model.Money;
import com.yangaobo.expense.backend.domain.model.RiskLevel;
import com.yangaobo.expense.common.domain.ExpenseCaseStatus;
import com.yangaobo.expense.common.error.ExpenseFlowException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReviewApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-21T08:00:00Z");

    private final ReviewRepository repository = mock(ReviewRepository.class);
    private final ExpenseCaseApplicationService caseService =
            mock(ExpenseCaseApplicationService.class);
    private final ReviewApplicationService service =
            new ReviewApplicationService(
                    repository,
                    caseService,
                    mock(PromptRenderService.class),
                    mock(ChatModelClient.class),
                    new ObjectMapper(),
                    Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void requestMoreInfoShouldKeepCaseWaitingAndAppendAudit() {
        UUID taskId = UUID.randomUUID();
        UUID caseId = UUID.randomUUID();
        ExpenseCase expenseCase = waitingCase(caseId);
        var task =
                new ReviewRepository.ReviewTask(
                        taskId,
                        caseId,
                        "OPEN",
                        null,
                        List.of("HIGH_RISK"),
                        "HIGH_RISK_ESCALATE_WITH_DEBATE_ASSIST",
                        "HIGH_RISK_REVIEW",
                        "FINANCE_ADMIN",
                        12,
                        List.of("风险信号", "制度引用"),
                        "该案例风险较高，已升级财务管理员复核。",
                        "ESCALATE_WITH_DEBATE_ASSIST",
                        true,
                        null,
                        NOW.plusSeconds(3600),
                        0,
                        NOW,
                        NOW);
        when(repository.findMoreInfoCaseId("request-1")).thenReturn(Optional.empty());
        when(repository.findById(taskId)).thenReturn(Optional.of(task));
        when(caseService.getById(caseId)).thenReturn(expenseCase);

        ExpenseCase result =
                service.requestMoreInfo(
                        taskId, 0, "请补充住宿清单", "reviewer-1", "request-1");

        assertThat(result).isSameAs(expenseCase);
        verify(repository)
                .requestMoreInfo(
                        taskId, 0, "reviewer-1", "请补充住宿清单", NOW);
        verify(repository)
                .appendAudit(
                        caseId,
                        "reviewer-1",
                        "REVIEW_MORE_INFO_REQUESTED",
                        "REVIEW_TASK",
                        taskId.toString(),
                        "request-1",
                        java.util.Map.of("comment", "请补充住宿清单"),
                        NOW);
    }

    @Test
    void requestMoreInfoShouldReplayByRequestId() {
        UUID caseId = UUID.randomUUID();
        ExpenseCase expenseCase = waitingCase(caseId);
        when(repository.findMoreInfoCaseId("request-1")).thenReturn(Optional.of(caseId));
        when(caseService.getById(caseId)).thenReturn(expenseCase);

        ExpenseCase result =
                service.requestMoreInfo(
                        UUID.randomUUID(), 0, "重复请求", "reviewer-1", "request-1");

        assertThat(result).isSameAs(expenseCase);
        verify(repository, never())
                .requestMoreInfo(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.any());
    }

    @Test
    void requestMoreInfoShouldRequireComment() {
        UUID taskId = UUID.randomUUID();
        UUID caseId = UUID.randomUUID();
        when(repository.findMoreInfoCaseId("request-1")).thenReturn(Optional.empty());
        when(repository.findById(taskId))
                .thenReturn(
                        Optional.of(
                                new ReviewRepository.ReviewTask(
                                        taskId,
                                        caseId,
                                        "OPEN",
                                        null,
                                        List.of(),
                                        "MEDIUM_RISK_HUMAN_REVIEW",
                                        "STANDARD_REVIEW",
                                        "REVIEWER",
                                        48,
                                        List.of(),
                                        "该案例需要人工复核后决定。",
                                        "HUMAN_REVIEW",
                                        false,
                                        null,
                                        null,
                                        0,
                                        NOW,
                                        NOW)));
        when(caseService.getById(caseId)).thenReturn(waitingCase(caseId));

        assertThatThrownBy(
                        () ->
                                service.requestMoreInfo(
                                        taskId, 0, " ", "reviewer-1", "request-1"))
                .isInstanceOf(ExpenseFlowException.class);
    }

    @Test
    void highRiskTaskShouldRequireFinanceAdminRole() {
        UUID taskId = UUID.randomUUID();
        UUID caseId = UUID.randomUUID();
        when(repository.findDecisionCaseId("request-1")).thenReturn(Optional.empty());
        when(repository.findById(taskId))
                .thenReturn(
                        Optional.of(
                                new ReviewRepository.ReviewTask(
                                        taskId,
                                        caseId,
                                        "OPEN",
                                        null,
                                        List.of("DUPLICATE_DOCUMENT"),
                                        "POSSIBLE_FRAUD_ESCALATE",
                                        "FRAUD_REVIEW",
                                        "FINANCE_ADMIN",
                                        8,
                                        List.of("重复票据检查"),
                                        "该案例存在疑似舞弊信号，已升级财务管理员复核。",
                                        "ESCALATE_FRAUD_REVIEW",
                                        true,
                                        null,
                                        NOW.plusSeconds(3600),
                                        0,
                                        NOW,
                                        NOW)));

        assertThatThrownBy(
                        () ->
                                service.approve(
                                        taskId,
                                        0,
                                        null,
                                        "同意",
                                        "reviewer-1",
                                        java.util.Set.of("REVIEWER"),
                                        "request-1"))
                .isInstanceOf(ExpenseFlowException.class);

        verify(caseService, never()).getById(caseId);
    }

    private static ExpenseCase waitingCase(UUID caseId) {
        return new ExpenseCase(
                caseId,
                "EF-20260621-0001",
                "employee-1",
                "测试员工",
                "IT",
                "测试报销",
                new Money(new BigDecimal("100.00"), "CNY"),
                ExpenseCaseStatus.WAITING_HUMAN,
                RiskLevel.MEDIUM,
                50,
                null,
                null,
                4,
                NOW,
                NOW);
    }
}

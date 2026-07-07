package com.yangaobo.expense.backend.application.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yangaobo.expense.backend.application.ExpenseCaseApplicationService;
import com.yangaobo.expense.common.error.ExpenseFlowException;
import com.yangaobo.expense.common.event.ExpenseWorkflowEvent;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ExpenseCaseEventServiceTest {

    private final ExpenseCaseApplicationService caseService =
            mock(ExpenseCaseApplicationService.class);
    private final ExpenseCaseEventRepository repository =
            mock(ExpenseCaseEventRepository.class);
    private final ExpenseCaseEventService service =
            new ExpenseCaseEventService(caseService, repository);

    @Test
    void shouldReplayAfterLastEventId() {
        UUID caseId = UUID.randomUUID();
        UUID lastEventId = UUID.randomUUID();
        ExpenseWorkflowEvent next =
                new ExpenseWorkflowEvent(
                        UUID.randomUUID(),
                        caseId,
                        "REVIEW_APPROVED",
                        11,
                        Instant.parse("2026-06-21T00:00:00Z"),
                        Map.of());
        when(repository.findSequence(caseId, lastEventId))
                .thenReturn(OptionalLong.of(10));
        when(repository.findAfter(caseId, 10, 200))
                .thenReturn(List.of(next));

        assertThat(
                        service.replay(
                                caseId,
                                "employee01",
                                false,
                                lastEventId.toString(),
                                200))
                .containsExactly(next);
        verify(caseService).getOwned(caseId, "employee01");
    }

    @Test
    void shouldRejectForeignLastEventId() {
        UUID caseId = UUID.randomUUID();
        UUID lastEventId = UUID.randomUUID();
        when(repository.findSequence(caseId, lastEventId))
                .thenReturn(OptionalLong.empty());

        assertThatThrownBy(
                        () ->
                                service.replay(
                                        caseId,
                                        "employee01",
                                        false,
                                        lastEventId.toString(),
                                        200))
                .isInstanceOf(ExpenseFlowException.class)
                .hasMessageContaining("不属于当前案例");
    }

    @Test
    void reviewerShouldUsePrivilegedCaseLookup() {
        UUID caseId = UUID.randomUUID();
        when(repository.findAfter(caseId, 0, 20))
                .thenReturn(List.of());

        service.replay(caseId, "reviewer01", true, null, 20);

        verify(caseService).getById(caseId);
    }
}

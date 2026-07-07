package com.yangaobo.expense.backend.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yangaobo.expense.backend.domain.model.ExpenseCase;
import com.yangaobo.expense.backend.domain.model.Money;
import com.yangaobo.expense.backend.domain.repository.ExpenseCaseRepository;
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
import org.mockito.ArgumentCaptor;

class ExpenseCaseApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-21T08:00:00Z");

    private final ExpenseCaseRepository repository = mock(ExpenseCaseRepository.class);
    private final ExpenseCaseApplicationService service =
            new ExpenseCaseApplicationService(
                    repository,
                    mock(CaseNumberGenerator.class),
                    Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void updateDraftShouldRequireOwnershipAndPersistEditableFields() {
        ExpenseCase draft = sampleCase("employee-1", ExpenseCaseStatus.DRAFT);
        when(repository.findById(draft.id())).thenReturn(Optional.of(draft));
        when(repository.update(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(0L)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ExpenseCase updated =
                service.updateDraft(
                        draft.id(),
                        new UpdateExpenseCaseCommand(
                                "employee-1",
                                " Bob ",
                                " FIN ",
                                "Corrected title",
                                new BigDecimal("256.80"),
                                "usd"));

        assertThat(updated.applicantName()).isEqualTo("Bob");
        assertThat(updated.departmentCode()).isEqualTo("FIN");
        assertThat(updated.title()).isEqualTo("Corrected title");
        assertThat(updated.claimedAmount().amount()).isEqualByComparingTo("256.80");
        assertThat(updated.claimedAmount().currency()).isEqualTo("USD");
        assertThat(updated.version()).isEqualTo(1);
        verify(repository).update(updated, draft.version());
    }

    @Test
    void updateDraftShouldRejectNonDraftCase() {
        ExpenseCase uploaded = sampleCase("employee-1", ExpenseCaseStatus.UPLOADED);
        when(repository.findById(uploaded.id())).thenReturn(Optional.of(uploaded));

        assertThatThrownBy(
                        () ->
                                service.updateDraft(
                                        uploaded.id(),
                                        new UpdateExpenseCaseCommand(
                                                "employee-1",
                                                "Bob",
                                                "FIN",
                                                "Corrected title",
                                                new BigDecimal("256.80"),
                                                "CNY")))
                .isInstanceOf(ExpenseFlowException.class);
        verify(repository, never())
                .update(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void updateDraftShouldRejectOtherOwner() {
        ExpenseCase draft = sampleCase("employee-1", ExpenseCaseStatus.DRAFT);
        when(repository.findById(draft.id())).thenReturn(Optional.of(draft));

        assertThatThrownBy(
                        () ->
                                service.updateDraft(
                                        draft.id(),
                                        new UpdateExpenseCaseCommand(
                                                "employee-2",
                                                "Bob",
                                                "FIN",
                                                "Corrected title",
                                                new BigDecimal("256.80"),
                                                "CNY")))
                .isInstanceOf(ExpenseFlowException.class);
        verify(repository, never())
                .update(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void deleteDraftShouldRequireOwnershipAndDraftStatus() {
        ExpenseCase draft = sampleCase("employee-1", ExpenseCaseStatus.DRAFT);
        when(repository.findById(draft.id())).thenReturn(Optional.of(draft));

        service.deleteDraft(draft.id(), "employee-1");

        verify(repository).deleteById(draft.id(), draft.version());
    }

    @Test
    void deleteDraftShouldRejectNonDraftCase() {
        ExpenseCase uploaded = sampleCase("employee-1", ExpenseCaseStatus.UPLOADED);
        when(repository.findById(uploaded.id())).thenReturn(Optional.of(uploaded));

        assertThatThrownBy(() -> service.deleteDraft(uploaded.id(), "employee-1"))
                .isInstanceOf(ExpenseFlowException.class);
        verify(repository, never())
                .deleteById(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void deleteDraftShouldRejectOtherOwner() {
        ExpenseCase draft = sampleCase("employee-1", ExpenseCaseStatus.DRAFT);
        when(repository.findById(draft.id())).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> service.deleteDraft(draft.id(), "employee-2"))
                .isInstanceOf(ExpenseFlowException.class);
        verify(repository, never())
                .deleteById(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void deleteAnyShouldAllowNonDraftCase() {
        ExpenseCase uploaded = sampleCase("employee-1", ExpenseCaseStatus.UPLOADED);
        when(repository.findById(uploaded.id())).thenReturn(Optional.of(uploaded));

        service.deleteAny(uploaded.id());

        verify(repository).deleteById(uploaded.id(), uploaded.version());
    }

    @Test
    void employeeSearchShouldApplyOwnerIsolationAndNormalizeFilters() {
        when(repository.search(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new ExpenseCaseRepository.ExpenseCasePage(List.of(), 0));

        var page =
                service.search(
                        new ExpenseCaseApplicationService.ExpenseCaseQuery(
                                "employee-1",
                                false,
                                ExpenseCaseStatus.APPROVED,
                                " HIGH ",
                                " 张三 ",
                                null,
                                null,
                                0,
                                20));

        assertThat(page.items()).isEmpty();
        ArgumentCaptor<ExpenseCaseRepository.ExpenseCaseSearchCriteria> captor =
                ArgumentCaptor.forClass(
                        ExpenseCaseRepository.ExpenseCaseSearchCriteria.class);
        verify(repository).search(captor.capture());
        assertThat(captor.getValue().ownerSubject()).isEqualTo("employee-1");
        assertThat(captor.getValue().riskLevel()).isEqualTo("HIGH");
        assertThat(captor.getValue().applicant()).isEqualTo("张三");
    }

    @Test
    void privilegedSearchShouldNotApplyOwnerFilter() {
        when(repository.search(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new ExpenseCaseRepository.ExpenseCasePage(List.of(), 0));

        service.search(
                new ExpenseCaseApplicationService.ExpenseCaseQuery(
                        "reviewer-1", true, null, null, null, null, null, 0, 20));

        ArgumentCaptor<ExpenseCaseRepository.ExpenseCaseSearchCriteria> captor =
                ArgumentCaptor.forClass(
                        ExpenseCaseRepository.ExpenseCaseSearchCriteria.class);
        verify(repository).search(captor.capture());
        assertThat(captor.getValue().ownerSubject()).isNull();
    }

    @Test
    void searchShouldRejectUnsafePageSize() {
        assertThatThrownBy(
                        () ->
                                service.search(
                                        new ExpenseCaseApplicationService.ExpenseCaseQuery(
                                                "employee-1",
                                                false,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                0,
                                                101)))
                .isInstanceOf(ExpenseFlowException.class);
    }

    private static ExpenseCase sampleCase(String ownerSubject, ExpenseCaseStatus status) {
        ExpenseCase draft =
                ExpenseCase.create(
                        UUID.randomUUID(),
                        "EF-20260621-0001",
                        ownerSubject,
                        "Alice",
                        "RD",
                        "Client visit",
                        new Money(new BigDecimal("128.50"), "CNY"),
                        NOW.minusSeconds(60));
        if (status == ExpenseCaseStatus.DRAFT) {
            return draft;
        }
        if (status == ExpenseCaseStatus.UPLOADED) {
            return draft.transitionTo(ExpenseCaseStatus.UPLOADED, NOW.minusSeconds(30));
        }
        throw new IllegalArgumentException("unsupported status " + status);
    }
}

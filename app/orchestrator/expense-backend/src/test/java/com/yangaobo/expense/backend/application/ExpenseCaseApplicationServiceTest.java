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
import com.yangaobo.expense.common.error.MyExpenseAgentException;
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
        ExpenseCase draft = sampleCase("student-1", ExpenseCaseStatus.DRAFT);
        when(repository.findById(draft.id())).thenReturn(Optional.of(draft));
        when(repository.update(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(0L)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ExpenseCase updated =
                service.updateDraft(
                        draft.id(),
                        new UpdateExpenseCaseCommand(
                                "student-1",
                                " Bob ",
                                " CS-SRTP-2 ",
                                "Corrected title",
                                new BigDecimal("256.80"),
                                "usd"));

        assertThat(updated.applicantName()).isEqualTo("Bob");
        assertThat(updated.projectCode()).isEqualTo("CS-SRTP-2");
        assertThat(updated.title()).isEqualTo("Corrected title");
        assertThat(updated.claimedAmount().amount()).isEqualByComparingTo("256.80");
        assertThat(updated.claimedAmount().currency()).isEqualTo("USD");
        assertThat(updated.version()).isEqualTo(1);
        verify(repository).update(updated, draft.version());
    }

    @Test
    void updateDraftShouldRejectNonDraftCase() {
        ExpenseCase uploaded = sampleCase("student-1", ExpenseCaseStatus.UPLOADED);
        when(repository.findById(uploaded.id())).thenReturn(Optional.of(uploaded));

        assertThatThrownBy(
                        () ->
                                service.updateDraft(
                                        uploaded.id(),
                                        new UpdateExpenseCaseCommand(
                                                "student-1",
                                                "Bob",
                                                "CS-SRTP-2",
                                                "Corrected title",
                                                new BigDecimal("256.80"),
                                                "CNY")))
                .isInstanceOf(MyExpenseAgentException.class);
        verify(repository, never())
                .update(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void updateDraftShouldRejectOtherOwner() {
        ExpenseCase draft = sampleCase("student-1", ExpenseCaseStatus.DRAFT);
        when(repository.findById(draft.id())).thenReturn(Optional.of(draft));

        assertThatThrownBy(
                        () ->
                                service.updateDraft(
                                        draft.id(),
                                        new UpdateExpenseCaseCommand(
                                                "student-2",
                                                "Bob",
                                                "CS-SRTP-2",
                                                "Corrected title",
                                                new BigDecimal("256.80"),
                                                "CNY")))
                .isInstanceOf(MyExpenseAgentException.class);
        verify(repository, never())
                .update(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void deleteDraftShouldRequireOwnershipAndDraftStatus() {
        ExpenseCase draft = sampleCase("student-1", ExpenseCaseStatus.DRAFT);
        when(repository.findById(draft.id())).thenReturn(Optional.of(draft));

        service.deleteDraft(draft.id(), "student-1");

        verify(repository).deleteById(draft.id(), draft.version());
    }

    @Test
    void deleteDraftShouldRejectNonDraftCase() {
        ExpenseCase uploaded = sampleCase("student-1", ExpenseCaseStatus.UPLOADED);
        when(repository.findById(uploaded.id())).thenReturn(Optional.of(uploaded));

        assertThatThrownBy(() -> service.deleteDraft(uploaded.id(), "student-1"))
                .isInstanceOf(MyExpenseAgentException.class);
        verify(repository, never())
                .deleteById(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void deleteDraftShouldRejectOtherOwner() {
        ExpenseCase draft = sampleCase("student-1", ExpenseCaseStatus.DRAFT);
        when(repository.findById(draft.id())).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> service.deleteDraft(draft.id(), "student-2"))
                .isInstanceOf(MyExpenseAgentException.class);
        verify(repository, never())
                .deleteById(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void deleteAnyShouldAllowNonDraftCase() {
        ExpenseCase uploaded = sampleCase("student-1", ExpenseCaseStatus.UPLOADED);
        when(repository.findById(uploaded.id())).thenReturn(Optional.of(uploaded));

        service.deleteAny(uploaded.id());

        verify(repository).deleteById(uploaded.id(), uploaded.version());
    }

    @Test
    void applicantSearchShouldApplyOwnerIsolationAndNormalizeFilters() {
        when(repository.search(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new ExpenseCaseRepository.ExpenseCasePage(List.of(), 0));

        var page =
                service.search(
                        new ExpenseCaseApplicationService.ExpenseCaseQuery(
                                "student-1",
                                false,
                                ExpenseCaseStatus.APPROVED,
                                " HIGH ",
                                " 李明 ",
                                null,
                                null,
                                0,
                                20));

        assertThat(page.items()).isEmpty();
        ArgumentCaptor<ExpenseCaseRepository.ExpenseCaseSearchCriteria> captor =
                ArgumentCaptor.forClass(
                        ExpenseCaseRepository.ExpenseCaseSearchCriteria.class);
        verify(repository).search(captor.capture());
        assertThat(captor.getValue().ownerSubject()).isEqualTo("student-1");
        assertThat(captor.getValue().riskLevel()).isEqualTo("HIGH");
        assertThat(captor.getValue().applicant()).isEqualTo("李明");
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
                                                "student-1",
                                                false,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                0,
                                                101)))
                .isInstanceOf(MyExpenseAgentException.class);
    }

    private static ExpenseCase sampleCase(String ownerSubject, ExpenseCaseStatus status) {
        ExpenseCase draft =
                ExpenseCase.create(
                        UUID.randomUUID(),
                        "CF-20260621-0001",
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

package com.yangaobo.expense.backend.application;

import com.yangaobo.expense.backend.domain.model.ExpenseCase;
import com.yangaobo.expense.backend.domain.model.Money;
import com.yangaobo.expense.backend.domain.repository.ExpenseCaseRepository;
import com.yangaobo.expense.backend.application.governance.AgentInputGuard;
import com.yangaobo.expense.backend.application.governance.AgentInputGuard.GuardMode;
import com.yangaobo.expense.backend.application.governance.SensitiveDataMasker;
import com.yangaobo.expense.common.domain.ExpenseCaseStatus;
import com.yangaobo.expense.common.error.CampusFundFlowErrorCode;
import com.yangaobo.expense.common.error.CampusFundFlowException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExpenseCaseApplicationService {

    private final ExpenseCaseRepository repository;
    private final CaseNumberGenerator caseNumberGenerator;
    private final AgentInputGuard inputGuard;
    private final SensitiveDataMasker masker;
    private final Clock clock;

    public ExpenseCaseApplicationService(
            ExpenseCaseRepository repository,
            CaseNumberGenerator caseNumberGenerator,
            Clock clock) {
        this(
                repository,
                caseNumberGenerator,
                new AgentInputGuard(new SensitiveDataMasker()),
                new SensitiveDataMasker(),
                clock);
    }

    @Autowired
    public ExpenseCaseApplicationService(
            ExpenseCaseRepository repository,
            CaseNumberGenerator caseNumberGenerator,
            AgentInputGuard inputGuard,
            SensitiveDataMasker masker,
            Clock clock) {
        this.repository = repository;
        this.caseNumberGenerator = caseNumberGenerator;
        this.inputGuard = inputGuard;
        this.masker = masker;
        this.clock = clock;
    }

    @Transactional
    public ExpenseCase create(CreateExpenseCaseCommand command) {
        UUID id = UUID.randomUUID();
        Instant now = now();
        ExpenseCase expenseCase =
                ExpenseCase.create(
                        id,
                        caseNumberGenerator.next(now, id),
                        command.ownerSubject(),
                        guardedText("applicantName", command.applicantName(), GuardMode.REPORT_ONLY),
                        guardedText("projectCode", command.projectCode(), GuardMode.REPORT_ONLY),
                        guardedText("title", command.title(), GuardMode.BLOCK),
                        new Money(command.claimedAmount(), command.currency()),
                        now);
        return repository.insert(expenseCase);
    }

    @Transactional
    public ExpenseCase updateDraft(UUID caseId, UpdateExpenseCaseCommand command) {
        ExpenseCase current = getOwned(caseId, command.ownerSubject());
        requireDraft(current, "只有草稿案例可以修改");
        ExpenseCase updated =
                current.reviseDraft(
                        guardedText("applicantName", command.applicantName(), GuardMode.REPORT_ONLY),
                        guardedText("projectCode", command.projectCode(), GuardMode.REPORT_ONLY),
                        guardedText("title", command.title(), GuardMode.BLOCK),
                        new Money(command.claimedAmount(), command.currency()),
                        now());
        return repository.update(updated, current.version());
    }

    @Transactional
    public void deleteDraft(UUID caseId, String ownerSubject) {
        ExpenseCase current = getOwned(caseId, ownerSubject);
        requireDraft(current, "只有草稿案例可以删除");
        repository.deleteById(caseId, current.version());
    }

    @Transactional
    public void deleteAny(UUID caseId) {
        ExpenseCase current = repository.findById(caseId).orElseThrow(() -> notFound(caseId));
        repository.deleteById(caseId, current.version());
    }

    @Transactional(readOnly = true)
    public ExpenseCase getOwned(UUID caseId, String ownerSubject) {
        ExpenseCase expenseCase =
                repository.findById(caseId).orElseThrow(() -> notFound(caseId));
        if (!expenseCase.ownerSubject().equals(ownerSubject)) {
            throw new CampusFundFlowException(
                    CampusFundFlowErrorCode.ACCESS_DENIED,
                    "The authenticated subject cannot access this expense case");
        }
        return expenseCase;
    }

    @Transactional(readOnly = true)
    public ExpenseCase getById(UUID caseId) {
        return repository.findById(caseId).orElseThrow(() -> notFound(caseId));
    }

    @Transactional(readOnly = true)
    public ExpenseCasePage search(ExpenseCaseQuery query) {
        if (query.page() < 0 || query.size() < 1 || query.size() > 100) {
            throw new CampusFundFlowException(
                    CampusFundFlowErrorCode.VALIDATION_FAILED,
                    "page必须大于等于0，size必须处于1到100之间");
        }
        var result =
                repository.search(
                        new ExpenseCaseRepository.ExpenseCaseSearchCriteria(
                                query.privileged() ? null : query.subject(),
                                query.status(),
                                blankToNull(query.riskLevel()),
                                blankToNull(query.applicant()),
                                query.createdFrom(),
                                query.createdTo(),
                                query.page(),
                                query.size()));
        return new ExpenseCasePage(result.items(), query.page(), query.size(), result.total());
    }

    @Transactional
    public ExpenseCase transition(UUID caseId, ExpenseCaseStatus target) {
        ExpenseCase current = repository.findById(caseId).orElseThrow(() -> notFound(caseId));
        ExpenseCase changed = current.transitionTo(target, now());
        return repository.update(changed, current.version());
    }

    @Transactional
    public ExpenseCase fail(UUID caseId, String stage, String reason) {
        ExpenseCase current = repository.findById(caseId).orElseThrow(() -> notFound(caseId));
        ExpenseCase changed = current.fail(stage, reason, now());
        return repository.update(changed, current.version());
    }

    @Transactional
    public ExpenseCase recordRisk(UUID caseId, int score) {
        ExpenseCase current = repository.findById(caseId).orElseThrow(() -> notFound(caseId));
        ExpenseCase changed = current.withRiskScore(score, now());
        return repository.update(changed, current.version());
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    private String guardedText(String field, String value, GuardMode mode) {
        AgentInputGuard.GuardResult result =
                inputGuard.inspect("expense-case." + field, value, mode);
        return masker.mask(result.sanitizedInput());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static CampusFundFlowException notFound(UUID caseId) {
        return new CampusFundFlowException(
                CampusFundFlowErrorCode.EXPENSE_CASE_NOT_FOUND,
                "Expense case %s was not found".formatted(caseId));
    }

    private static void requireDraft(ExpenseCase expenseCase, String message) {
        if (expenseCase.status() != ExpenseCaseStatus.DRAFT) {
            throw new CampusFundFlowException(CampusFundFlowErrorCode.VALIDATION_FAILED, message);
        }
    }

    public record ExpenseCaseQuery(
            String subject,
            boolean privileged,
            ExpenseCaseStatus status,
            String riskLevel,
            String applicant,
            Instant createdFrom,
            Instant createdTo,
            int page,
            int size) {}

    public record ExpenseCasePage(List<ExpenseCase> items, int page, int size, long total) {}
}

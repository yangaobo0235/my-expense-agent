package com.yangaobo.expense.account.interfaces.rest;

import com.yangaobo.expense.account.application.AccountApplicationService;
import com.yangaobo.expense.account.application.AccountApplicationService.ApplicantProfile;
import com.yangaobo.expense.account.application.AccountApplicationService.ProjectBudgetBalance;
import com.yangaobo.expense.account.application.AccountApplicationService.BudgetDebit;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/applicants")
public class AccountController {

    private final AccountApplicationService service;

    public AccountController(AccountApplicationService service) {
        this.service = service;
    }

    @GetMapping("/{applicantId}")
    public ApplicantProfile profile(
            @PathVariable String applicantId,
            @RequestParam(required = false) String projectCode) {
        return service.getApplicantProfile(applicantId, projectCode);
    }

    @GetMapping("/{applicantId}/reimbursement-accounts")
    public List<String> reimbursementAccounts(@PathVariable String applicantId) {
        return service.getReimbursementAccounts(applicantId);
    }

    @GetMapping("/{applicantId}/budget-balance")
    public ProjectBudgetBalance budgetBalance(
            @PathVariable String applicantId,
            @RequestParam(required = false) String projectCode) {
        return service.getProjectBudgetBalance(applicantId, projectCode);
    }

    @PostMapping("/budget-debits")
    public BudgetDebit debit(@RequestBody BudgetDebitRequest request) {
        return service.debitProjectBudget(
                request.requestId(),
                request.caseId(),
                request.projectCode(),
                request.applicantId(),
                request.amount(),
                request.currency());
    }

    public record BudgetDebitRequest(
            String requestId,
            UUID caseId,
            String projectCode,
            String applicantId,
            BigDecimal amount,
            String currency) {}
}

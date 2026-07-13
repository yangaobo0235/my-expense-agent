package com.yangaobo.expense.service.interfaces.rest;

import com.yangaobo.expense.service.application.ExpenseBusinessService;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/fund-tools")
public class ExpenseBusinessController {

    private final ExpenseBusinessService service;

    public ExpenseBusinessController(ExpenseBusinessService service) {
        this.service = service;
    }

    @GetMapping("/invoice-validation")
    public Object validate(@RequestParam String invoiceNumber) {
        return service.validateInvoiceNumber(invoiceNumber);
    }

    @GetMapping("/allowed-amount")
    public Object allowed(
            @RequestParam BigDecimal claimedAmount,
            @RequestParam BigDecimal policyLimit) {
        return service.calculateAllowedAmount(claimedAmount, policyLimit);
    }

    @PostMapping("/reimbursements")
    public Object submit(@RequestBody SubmissionRequest request) {
        return service.submitReimbursement(
                request.requestId(), request.caseId(), request.amount(), request.currency());
    }

    @PostMapping("/postings")
    public Object pay(@RequestBody PaymentRequest request) {
        return service.submitFundPosting(
                request.requestId(),
                request.reimbursementId(),
                request.amount(),
                request.currency());
    }

    public record SubmissionRequest(
            String requestId, UUID caseId, BigDecimal amount, String currency) {}
    public record PaymentRequest(
            String requestId, UUID reimbursementId, BigDecimal amount, String currency) {}
}

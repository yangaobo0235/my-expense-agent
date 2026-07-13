package com.yangaobo.expense.audithistory.interfaces.rest;

import com.yangaobo.expense.audithistory.application.AuditHistoryService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/audit-tools")
public class AuditHistoryController {

    private final AuditHistoryService service;

    public AuditHistoryController(AuditHistoryService service) {
        this.service = service;
    }

    @GetMapping("/duplicates")
    public Object duplicates(
            @RequestParam String sha256,
            @RequestParam(required = false) UUID excludeCaseId) {
        return service.checkDuplicateDocument(sha256, excludeCaseId);
    }

    @GetMapping("/history")
    public Object history(@RequestParam String applicantId) {
        return service.getFundReimbursementHistory(applicantId);
    }

    @PostMapping("/evidence")
    public Object evidence(@RequestBody EvidenceRequest request) {
        return service.saveReviewEvidence(
                request.requestId(), request.caseId(), request.evidenceType(), request.contentHash());
    }

    @PostMapping("/history")
    public Object recordHistory(@RequestBody HistoryRequest request) {
        return service.recordFundReimbursementHistory(
                request.requestId(),
                request.caseId(),
                request.applicantId(),
                request.sellerName(),
                request.amount(),
                request.currency(),
                request.expenseDate(),
                request.documentSha256());
    }

    public record EvidenceRequest(
            String requestId, UUID caseId, String evidenceType, String contentHash) {}

    public record HistoryRequest(
            String requestId,
            UUID caseId,
            String applicantId,
            String sellerName,
            BigDecimal amount,
            String currency,
            LocalDate expenseDate,
            String documentSha256) {}
}

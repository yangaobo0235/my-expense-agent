package com.yangaobo.expense.audithistory.interfaces.rest;

import com.yangaobo.expense.audithistory.application.AuditHistoryService;
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
    public Object history(@RequestParam String employeeId) {
        return service.getExpenseHistory(employeeId);
    }

    @PostMapping("/evidence")
    public Object evidence(@RequestBody EvidenceRequest request) {
        return service.saveReviewEvidence(
                request.requestId(), request.caseId(), request.evidenceType(), request.contentHash());
    }

    public record EvidenceRequest(
            String requestId, UUID caseId, String evidenceType, String contentHash) {}
}

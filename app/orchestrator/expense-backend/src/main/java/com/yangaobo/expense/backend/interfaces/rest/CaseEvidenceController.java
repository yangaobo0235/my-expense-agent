package com.yangaobo.expense.backend.interfaces.rest;

import com.yangaobo.expense.backend.application.workflow.CaseEvidenceService;
import java.security.Principal;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/expense-cases")
public class CaseEvidenceController {

    private final CaseEvidenceService evidenceService;

    public CaseEvidenceController(CaseEvidenceService evidenceService) {
        this.evidenceService = evidenceService;
    }

    @GetMapping("/{caseId}/evidence")
    public CaseEvidenceService.CaseEvidence evidence(
            @PathVariable UUID caseId, Principal principal) {
        return evidenceService.get(caseId, principal.getName(), privileged(principal));
    }

    private static boolean privileged(Principal principal) {
        return principal instanceof Authentication authentication
                && authentication.getAuthorities().stream()
                        .map(
                                org.springframework.security.core.GrantedAuthority
                                        ::getAuthority)
                        .anyMatch(
                                authority ->
                                        "ROLE_REVIEWER".equals(authority)
                                                || "ROLE_FINANCE_ADMIN".equals(authority));
    }
}

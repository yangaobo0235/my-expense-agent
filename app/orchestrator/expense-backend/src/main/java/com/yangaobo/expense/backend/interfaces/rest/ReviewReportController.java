package com.yangaobo.expense.backend.interfaces.rest;

import com.yangaobo.expense.backend.application.report.ReviewReport;
import com.yangaobo.expense.backend.application.report.ReviewReportService;
import java.security.Principal;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/fund-applications")
public class ReviewReportController {

    private final ReviewReportService service;

    public ReviewReportController(ReviewReportService service) {
        this.service = service;
    }

    @GetMapping("/{caseId}/review-report")
    public ReviewReport latest(@PathVariable UUID caseId, Principal principal) {
        return service
                .latest(caseId, principal.getName(), privileged(principal))
                .orElseThrow(
                        () ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND, "审核报告尚未生成"));
    }

    @PostMapping("/{caseId}/review-report")
    @ResponseStatus(HttpStatus.CREATED)
    public ReviewReport generate(@PathVariable UUID caseId, Principal principal) {
        return service.generate(caseId, principal.getName(), privileged(principal));
    }

    private static boolean privileged(Principal principal) {
        return principal instanceof Authentication authentication
                && authentication.getAuthorities().stream()
                        .map(org.springframework.security.core.GrantedAuthority::getAuthority)
                        .anyMatch(
                                authority ->
                                        "ROLE_COLLEGE_REVIEWER".equals(authority)
                                                || "ROLE_FINANCE_ADMIN".equals(authority)
                                                || "ROLE_AUDITOR".equals(authority));
    }
}

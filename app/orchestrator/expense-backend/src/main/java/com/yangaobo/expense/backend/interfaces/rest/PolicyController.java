package com.yangaobo.expense.backend.interfaces.rest;

import com.yangaobo.expense.backend.application.policy.ImportPolicyCommand;
import com.yangaobo.expense.backend.application.policy.PolicyRetrievalService;
import com.yangaobo.expense.backend.application.policy.PolicySearchQuery;
import com.yangaobo.expense.common.error.CampusFundFlowErrorCode;
import com.yangaobo.expense.common.error.CampusFundFlowException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.net.URI;
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/policies")
public class PolicyController {

    private final PolicyRetrievalService policyService;

    public PolicyController(PolicyRetrievalService policyService) {
        this.policyService = policyService;
    }

    @PostMapping
    public ResponseEntity<PolicyImportResponse> importPolicy(
            @Valid @RequestBody ImportPolicyRequest request, Principal principal) {
        authenticatedSubject(principal);
        var result =
                policyService.importPolicy(
                        new ImportPolicyCommand(
                                request.policyCode(),
                                request.name(),
                                request.category(),
                                request.region(),
                                request.applicantType(),
                                request.version(),
                                request.effectiveFrom(),
                                request.effectiveTo(),
                                request.status(),
                                request.sourceUri(),
                                request.markdownContent()));
        return ResponseEntity.created(URI.create("/api/v1/policies/" + result.policyId()))
                .body(PolicyImportResponse.from(result));
    }

    @GetMapping
    public List<PolicyCatalogResponse> list(Principal principal) {
        authenticatedSubject(principal);
        return policyService.listCatalog().stream().map(PolicyCatalogResponse::from).toList();
    }

    @GetMapping("/search")
    public List<PolicySearchResponse> search(
            @RequestParam @NotBlank String query,
            @RequestParam @NotBlank String category,
            @RequestParam @NotBlank String region,
            @RequestParam @NotBlank String applicantType,
            @RequestParam(required = false) LocalDate expenseDate,
            @RequestParam(defaultValue = "5") @Min(1) @Max(20) int limit,
            @RequestParam(defaultValue = "0.55") @Min(0) @Max(1) double minimumScore,
            Principal principal) {
        authenticatedSubject(principal);
        return policyService
                .search(
                        new PolicySearchQuery(
                                query,
                                category,
                                region,
                                applicantType,
                                expenseDate,
                                limit,
                                minimumScore))
                .stream()
                .map(PolicySearchResponse::from)
                .toList();
    }

    private static String authenticatedSubject(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new CampusFundFlowException(
                    CampusFundFlowErrorCode.ACCESS_DENIED, "需要先完成身份认证");
        }
        return principal.getName();
    }
}

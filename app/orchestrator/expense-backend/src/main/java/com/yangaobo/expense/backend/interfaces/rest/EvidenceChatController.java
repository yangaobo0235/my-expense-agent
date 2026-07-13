package com.yangaobo.expense.backend.interfaces.rest;

import com.yangaobo.expense.backend.application.chat.EvidenceChatService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.security.Principal;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/fund-applications")
public class EvidenceChatController {

    private final EvidenceChatService service;

    public EvidenceChatController(EvidenceChatService service) {
        this.service = service;
    }

    @PostMapping("/{caseId}/evidence-chat")
    public EvidenceChatService.EvidenceChatResponse chat(
            @PathVariable UUID caseId,
            @Valid @RequestBody EvidenceChatRequest request,
            Principal principal) {
        return service.answer(caseId, principal.getName(), privileged(principal), request.question());
    }

    private static boolean privileged(Principal principal) {
        return principal instanceof Authentication authentication
                && authentication.getAuthorities().stream()
                        .map(org.springframework.security.core.GrantedAuthority::getAuthority)
                        .anyMatch(
                                authority ->
                                        "ROLE_COLLEGE_REVIEWER".equals(authority)
                                                || "ROLE_FINANCE_ADMIN".equals(authority));
    }

    public record EvidenceChatRequest(@NotBlank String question) {}
}

package com.yangaobo.expense.backend.interfaces.rest;

import com.yangaobo.expense.backend.application.prompt.PromptGovernanceService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/prompts")
public class PromptGovernanceController {

    private final PromptGovernanceService service;

    public PromptGovernanceController(PromptGovernanceService service) {
        this.service = service;
    }

    @GetMapping
    public List<PromptTemplateResponse> list(@RequestParam(required = false) String promptKey) {
        return service.list(promptKey).stream().map(PromptTemplateResponse::from).toList();
    }

    @GetMapping("/{id}")
    public PromptTemplateResponse get(@PathVariable UUID id) {
        return PromptTemplateResponse.from(service.get(id));
    }

    @GetMapping("/{id}/review")
    public PromptGovernanceService.PromptVersionReview review(@PathVariable UUID id) {
        return service.review(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PromptTemplateResponse create(
            @Valid @RequestBody PromptTemplateRequest request, Principal principal) {
        return PromptTemplateResponse.from(
                service.createDraft(
                        request.promptKey(),
                        request.version(),
                        request.name(),
                        request.description(),
                        request.content(),
                        request.variableSchema(),
                        request.modelName(),
                        request.temperature(),
                        request.maxTokens(),
                        principal.getName()));
    }

    @PutMapping("/{id}")
    public PromptTemplateResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody PromptTemplateRequest request,
            Principal principal) {
        return PromptTemplateResponse.from(
                service.updateDraft(
                        id,
                        request.name(),
                        request.description(),
                        request.content(),
                        request.variableSchema(),
                        request.modelName(),
                        request.temperature(),
                        request.maxTokens(),
                        principal.getName()));
    }

    @PostMapping("/{id}/submit")
    public PromptChangeRequestResponse submit(
            @PathVariable UUID id,
            @RequestBody(required = false) PromptSubmitRequest request,
            Principal principal) {
        return PromptChangeRequestResponse.from(
                service.submit(id, request == null ? "" : request.diffSummary(), principal.getName()));
    }

    @GetMapping("/{id}/changes")
    public List<PromptChangeRequestResponse> changes(@PathVariable UUID id) {
        return service.changeRequests(id).stream().map(PromptChangeRequestResponse::from).toList();
    }

    @PostMapping("/changes/{id}/approve")
    public PromptChangeRequestResponse approve(
            @PathVariable UUID id,
            @RequestBody(required = false) PromptReviewRequest request,
            Principal principal) {
        return PromptChangeRequestResponse.from(
                service.approve(id, request == null ? "" : request.comment(), principal.getName()));
    }

    @PostMapping("/changes/{id}/reject")
    public PromptChangeRequestResponse reject(
            @PathVariable UUID id,
            @RequestBody(required = false) PromptReviewRequest request,
            Principal principal) {
        return PromptChangeRequestResponse.from(
                service.reject(id, request == null ? "" : request.comment(), principal.getName()));
    }

    @PostMapping("/{id}/activate")
    public PromptTemplateResponse activate(@PathVariable UUID id, Principal principal) {
        return PromptTemplateResponse.from(service.activate(id, principal.getName()));
    }
}

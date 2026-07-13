package com.yangaobo.expense.backend.interfaces.rest;

import com.yangaobo.expense.backend.application.workflow.ReviewApplicationService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/review-tasks")
public class ReviewController {

    private final ReviewApplicationService reviewService;

    public ReviewController(ReviewApplicationService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping
    public List<ReviewTaskResponse> openTasks(Principal principal) {
        return reviewService.openTasks(roles(principal)).stream()
                .map(ReviewTaskResponse::from)
                .toList();
    }

    @GetMapping("/{taskId}")
    public ReviewTaskResponse get(@PathVariable UUID taskId, Principal principal) {
        return ReviewTaskResponse.from(reviewService.get(taskId, roles(principal)));
    }

    @PostMapping("/{taskId}/more-info-suggestion")
    public ReviewApplicationService.MoreInfoSuggestion moreInfoSuggestion(
            @PathVariable UUID taskId, Principal principal) {
        return reviewService.suggestMoreInfo(taskId, roles(principal));
    }

    @PostMapping("/{taskId}/approve")
    public ExpenseCaseResponse approve(
            @PathVariable UUID taskId,
            @Valid @RequestBody ReviewDecisionRequest request,
            Principal principal) {
        return ExpenseCaseResponse.from(
                reviewService.approve(
                        taskId,
                        request.version(),
                        request.approvedAmount(),
                        request.comment(),
                        principal.getName(),
                        roles(principal),
                        request.requestId()));
    }

    @PostMapping("/{taskId}/reject")
    public ExpenseCaseResponse reject(
            @PathVariable UUID taskId,
            @Valid @RequestBody ReviewDecisionRequest request,
            Principal principal) {
        return ExpenseCaseResponse.from(
                reviewService.reject(
                        taskId,
                        request.version(),
                        request.comment(),
                        principal.getName(),
                        roles(principal),
                        request.requestId()));
    }

    @PostMapping("/{taskId}/request-more-info")
    public ExpenseCaseResponse requestMoreInfo(
            @PathVariable UUID taskId,
            @Valid @RequestBody ReviewDecisionRequest request,
            Principal principal) {
        return ExpenseCaseResponse.from(
                reviewService.requestMoreInfo(
                        taskId,
                        request.version(),
                        request.comment(),
                        principal.getName(),
                        roles(principal),
                        request.requestId()));
    }

    private static Set<String> roles(Principal principal) {
        if (!(principal instanceof Authentication authentication)) {
            return Set.of();
        }
        return authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority().replaceFirst("^ROLE_", ""))
                .collect(Collectors.toUnmodifiableSet());
    }
}

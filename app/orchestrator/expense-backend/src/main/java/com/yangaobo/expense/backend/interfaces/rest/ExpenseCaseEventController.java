package com.yangaobo.expense.backend.interfaces.rest;

import com.yangaobo.expense.backend.application.event.ExpenseCaseEventService;
import com.yangaobo.expense.common.event.ExpenseWorkflowEvent;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/expense-cases")
public class ExpenseCaseEventController {

    private final ExpenseCaseEventService eventService;

    public ExpenseCaseEventController(
            ExpenseCaseEventService eventService) {
        this.eventService = eventService;
    }

    @GetMapping(
            value = "/{caseId}/events",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(
            @PathVariable UUID caseId,
            @RequestHeader(
                            name = "Last-Event-ID",
                            required = false)
                    String lastEventId,
            @RequestParam(defaultValue = "200") int limit,
            Principal principal) {
        List<ExpenseWorkflowEvent> events =
                eventService.replay(
                        caseId,
                        principal.getName(),
                        privileged(principal),
                        lastEventId,
                        limit);
        SseEmitter emitter = new SseEmitter(30_000L);
        try {
            for (ExpenseWorkflowEvent event : events) {
                emitter.send(
                        SseEmitter.event()
                                .id(event.eventId().toString())
                                .name(event.type())
                                .data(event));
            }
            emitter.complete();
        } catch (Exception exception) {
            emitter.completeWithError(exception);
        }
        return emitter;
    }

    private static boolean privileged(Principal principal) {
        if (!(principal instanceof Authentication authentication)) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .map(
                        org.springframework.security.core.GrantedAuthority
                                ::getAuthority)
                .anyMatch(
                        authority ->
                                "ROLE_REVIEWER".equals(authority)
                                        || "ROLE_FINANCE_ADMIN"
                                                .equals(authority));
    }
}

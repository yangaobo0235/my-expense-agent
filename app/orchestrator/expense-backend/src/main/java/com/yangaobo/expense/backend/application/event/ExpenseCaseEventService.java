package com.yangaobo.expense.backend.application.event;

import com.yangaobo.expense.backend.application.ExpenseCaseApplicationService;
import com.yangaobo.expense.common.error.CampusFundFlowErrorCode;
import com.yangaobo.expense.common.error.CampusFundFlowException;
import com.yangaobo.expense.common.event.ExpenseWorkflowEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ExpenseCaseEventService {

    private final ExpenseCaseApplicationService caseService;
    private final ExpenseCaseEventRepository eventRepository;

    public ExpenseCaseEventService(
            ExpenseCaseApplicationService caseService,
            ExpenseCaseEventRepository eventRepository) {
        this.caseService = caseService;
        this.eventRepository = eventRepository;
    }

    public List<ExpenseWorkflowEvent> replay(
            UUID caseId,
            String ownerSubject,
            boolean privileged,
            String lastEventId,
            int limit) {
        if (privileged) {
            caseService.getById(caseId);
        } else {
            caseService.getOwned(caseId, ownerSubject);
        }
        long afterSequence = 0;
        if (lastEventId != null && !lastEventId.isBlank()) {
            UUID eventId;
            try {
                eventId = UUID.fromString(lastEventId.trim());
            } catch (IllegalArgumentException exception) {
                throw validation("Last-Event-ID 必须是 UUID");
            }
            afterSequence =
                    eventRepository
                            .findSequence(caseId, eventId)
                            .orElseThrow(
                                    () ->
                                            validation(
                                                    "Last-Event-ID 不属于当前案例"));
        }
        return eventRepository.findAfter(caseId, afterSequence, limit);
    }

    private static CampusFundFlowException validation(String message) {
        return new CampusFundFlowException(
                CampusFundFlowErrorCode.VALIDATION_FAILED, message);
    }
}

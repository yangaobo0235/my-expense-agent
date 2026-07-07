package com.yangaobo.expense.backend.application.event;

import com.yangaobo.expense.common.event.ExpenseWorkflowEvent;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;

public interface ExpenseCaseEventRepository {

    ExpenseWorkflowEvent append(
            UUID caseId,
            String type,
            Map<String, Object> payload,
            Instant occurredAt);

    List<ExpenseWorkflowEvent> findAfter(
            UUID caseId, long afterSequence, int limit);

    OptionalLong findSequence(UUID caseId, UUID eventId);
}

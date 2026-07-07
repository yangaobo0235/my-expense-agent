package com.yangaobo.expense.common.event;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ExpenseWorkflowEvent(
        UUID eventId,
        UUID caseId,
        String type,
        long sequence,
        Instant occurredAt,
        Map<String, Object> payload) {

    public ExpenseWorkflowEvent {
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }
}

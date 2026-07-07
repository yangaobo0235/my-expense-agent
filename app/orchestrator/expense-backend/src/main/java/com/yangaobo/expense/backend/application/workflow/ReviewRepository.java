package com.yangaobo.expense.backend.application.workflow;

import com.yangaobo.expense.backend.domain.model.ExpenseCase;
import com.yangaobo.expense.backend.domain.risk.RiskAssessment;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface ReviewRepository {

    ReviewTask createOpenTask(
            UUID caseId,
            List<String> reasonCodes,
            RiskRoutingDecision routing,
            Instant dueAt,
            Instant now);

    Optional<ReviewTask> findById(UUID taskId);

    List<ReviewTask> findOpenTasks();

    Optional<UUID> findDecisionCaseId(String requestId);

    Optional<UUID> findMoreInfoCaseId(String requestId);

    Optional<ExpenseDecision> findDecisionByCaseId(UUID caseId);

    void completeTask(
            UUID taskId,
            long expectedVersion,
            String status,
            String reviewerSubject,
            String comment,
            Instant now);

    void requestMoreInfo(
            UUID taskId,
            long expectedVersion,
            String reviewerSubject,
            String comment,
            Instant now);

    void saveDecision(
            ExpenseCase expenseCase,
            String decision,
            BigDecimal approvedAmount,
            RiskAssessment risk,
            List<Map<String, Object>> policyFindings,
            String decidedBy,
            String requestId,
            Instant now);

    void appendAudit(
            UUID caseId,
            String actorSubject,
            String action,
            String resourceType,
            String resourceId,
            String requestId,
            Map<String, Object> metadata,
            Instant now);

    record ReviewTask(
            UUID id,
            UUID caseId,
            String status,
            String assigneeSubject,
            List<String> reasonCodes,
            String routingAction,
            String routingQueue,
            String assigneeRole,
            Integer slaHours,
            List<String> requiredEvidence,
            String userFacingMessage,
            String fallbackStrategy,
            boolean debateAssistEnabled,
            String reviewerComment,
            Instant dueAt,
            long version,
            Instant createdAt,
            Instant updatedAt) {}

    record ExpenseDecision(
            UUID caseId,
            String decision,
            BigDecimal approvedAmount,
            String currency,
            String decidedBy,
            String requestId,
            Instant createdAt) {}
}

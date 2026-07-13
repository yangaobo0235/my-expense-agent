package com.yangaobo.expense.backend.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangaobo.expense.backend.application.workflow.ReviewRepository;
import com.yangaobo.expense.backend.application.workflow.RiskRoutingDecision;
import com.yangaobo.expense.backend.application.event.ExpenseCaseEventRepository;
import com.yangaobo.expense.backend.domain.model.ExpenseCase;
import com.yangaobo.expense.backend.domain.risk.RiskAssessment;
import com.yangaobo.expense.common.error.CampusFundFlowErrorCode;
import com.yangaobo.expense.common.error.CampusFundFlowException;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcReviewRepository implements ReviewRepository {

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;
    private final ExpenseCaseEventRepository eventRepository;

    public JdbcReviewRepository(
            JdbcClient jdbcClient,
            ObjectMapper objectMapper,
            ExpenseCaseEventRepository eventRepository) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
        this.eventRepository = eventRepository;
    }

    @Override
    public ReviewTask createOpenTask(
            UUID caseId,
            List<String> reasonCodes,
            RiskRoutingDecision routing,
            Instant dueAt,
            Instant now) {
        Optional<ReviewTask> existing =
                jdbcClient
                        .sql(
                                """
                                SELECT * FROM expense_review_task
                                WHERE case_id = :caseId
                                  AND status IN ('OPEN', 'ASSIGNED', 'MORE_INFO')
                                """)
                        .param("caseId", caseId)
                        .query(this::mapTask)
                        .optional();
        if (existing.isPresent()) {
            return existing.get();
        }
        UUID id = UUID.randomUUID();
        jdbcClient
                .sql(
                        """
                        INSERT INTO expense_review_task (
                            id, case_id, status, reason_codes,
                            routing_action, routing_queue, assignee_role, sla_hours,
                            required_evidence, user_facing_message, fallback_strategy,
                            debate_assist_enabled, due_at,
                            version, created_at, updated_at
                        ) VALUES (
                            :id, :caseId, 'OPEN', CAST(:reasons AS jsonb),
                            :routingAction, :routingQueue, :assigneeRole, :slaHours,
                            CAST(:requiredEvidence AS jsonb), :userFacingMessage, :fallbackStrategy,
                            :debateAssistEnabled, :dueAt,
                            0, :createdAt, :updatedAt
                        )
                        """)
                .param("id", id)
                .param("caseId", caseId)
                .param("reasons", json(reasonCodes))
                .param("routingAction", routing.action().name())
                .param("routingQueue", routing.queue())
                .param("assigneeRole", routing.assigneeRole())
                .param("slaHours", routing.slaHours())
                .param("requiredEvidence", json(routing.requiredEvidence()))
                .param("userFacingMessage", routing.userFacingMessage())
                .param("fallbackStrategy", routing.fallbackStrategy())
                .param("debateAssistEnabled", routing.debateAssistEnabled())
                .param("dueAt", Timestamp.from(dueAt))
                .param("createdAt", Timestamp.from(now))
                .param("updatedAt", Timestamp.from(now))
                .update();
        return findById(id).orElseThrow();
    }

    @Override
    public Optional<ReviewTask> findById(UUID taskId) {
        return jdbcClient
                .sql("SELECT * FROM expense_review_task WHERE id = :id")
                .param("id", taskId)
                .query(this::mapTask)
                .optional();
    }

    @Override
    public List<ReviewTask> findOpenTasks() {
        return jdbcClient
                .sql(
                        """
                        SELECT * FROM expense_review_task
                        WHERE status IN ('OPEN', 'ASSIGNED', 'MORE_INFO')
                        ORDER BY created_at
                        """)
                .query(this::mapTask)
                .list();
    }

    @Override
    public Optional<UUID> findDecisionCaseId(String requestId) {
        return jdbcClient
                .sql("SELECT case_id FROM expense_decision WHERE request_id = :requestId")
                .param("requestId", requestId)
                .query(UUID.class)
                .optional();
    }

    @Override
    public Optional<UUID> findMoreInfoCaseId(String requestId) {
        return jdbcClient
                .sql(
                        """
                        SELECT case_id
                        FROM campus_fund_audit_log
                        WHERE action = 'REVIEW_MORE_INFO_REQUESTED'
                          AND request_id = :requestId
                        """)
                .param("requestId", requestId)
                .query(UUID.class)
                .optional();
    }

    @Override
    public Optional<ExpenseDecision> findDecisionByCaseId(UUID caseId) {
        return jdbcClient
                .sql(
                        """
                        SELECT case_id, decision, approved_amount, currency,
                               decided_by, request_id, created_at
                        FROM expense_decision
                        WHERE case_id = :caseId
                        """)
                .param("caseId", caseId)
                .query(
                        (rs, row) ->
                                new ExpenseDecision(
                                        rs.getObject("case_id", UUID.class),
                                        rs.getString("decision"),
                                        rs.getBigDecimal("approved_amount"),
                                        rs.getString("currency"),
                                        rs.getString("decided_by"),
                                        rs.getString("request_id"),
                                        rs.getTimestamp("created_at").toInstant()))
                .optional();
    }

    @Override
    public void completeTask(
            UUID taskId,
            long expectedVersion,
            String status,
            String reviewerSubject,
            String comment,
            Instant now) {
        int updated =
                jdbcClient
                        .sql(
                                """
                                UPDATE expense_review_task
                                SET status = :status, assignee_subject = :reviewer,
                                    reviewer_comment = :comment, version = version + 1,
                                    updated_at = :updatedAt
                                WHERE id = :id AND version = :expectedVersion
                                  AND status IN ('OPEN', 'ASSIGNED', 'MORE_INFO')
                                """)
                        .param("status", status)
                        .param("reviewer", reviewerSubject)
                        .param("comment", comment)
                        .param("updatedAt", Timestamp.from(now))
                        .param("id", taskId)
                        .param("expectedVersion", expectedVersion)
                        .update();
        if (updated != 1) {
            throw new CampusFundFlowException(
                    CampusFundFlowErrorCode.OPTIMISTIC_LOCK_CONFLICT,
                    "审核任务已被其他审核员处理");
        }
    }

    @Override
    public void requestMoreInfo(
            UUID taskId,
            long expectedVersion,
            String reviewerSubject,
            String comment,
            Instant now) {
        int updated =
                jdbcClient
                        .sql(
                                """
                                UPDATE expense_review_task
                                SET status = 'MORE_INFO', assignee_subject = :reviewer,
                                    reviewer_comment = :comment, version = version + 1,
                                    updated_at = :updatedAt
                                WHERE id = :id AND version = :expectedVersion
                                  AND status IN ('OPEN', 'ASSIGNED', 'MORE_INFO')
                                """)
                        .param("reviewer", reviewerSubject)
                        .param("comment", comment)
                        .param("updatedAt", Timestamp.from(now))
                        .param("id", taskId)
                        .param("expectedVersion", expectedVersion)
                        .update();
        if (updated != 1) {
            throw new CampusFundFlowException(
                    CampusFundFlowErrorCode.OPTIMISTIC_LOCK_CONFLICT,
                    "审核任务已被其他审核员处理");
        }
    }

    @Override
    public void saveDecision(
            ExpenseCase expenseCase,
            String decision,
            BigDecimal approvedAmount,
            RiskAssessment risk,
            List<Map<String, Object>> policyFindings,
            String decidedBy,
            String requestId,
            Instant now) {
        jdbcClient
                .sql(
                        """
                        INSERT INTO expense_decision (
                            id, case_id, decision, total_amount, approved_amount,
                            currency, risk_level, risk_score, policy_findings,
                            risk_findings, evidence, decided_by, request_id, created_at
                        ) VALUES (
                            :id, :caseId, :decision, :totalAmount, :approvedAmount,
                            :currency, :riskLevel, :riskScore, CAST(:policy AS jsonb),
                            CAST(:risk AS jsonb), '{}'::jsonb, :decidedBy, :requestId, :createdAt
                        )
                        ON CONFLICT (case_id) DO NOTHING
                        """)
                .param("id", UUID.randomUUID())
                .param("caseId", expenseCase.id())
                .param("decision", decision)
                .param("totalAmount", expenseCase.claimedAmount().amount())
                .param("approvedAmount", approvedAmount)
                .param("currency", expenseCase.claimedAmount().currency())
                .param("riskLevel", risk.level().name())
                .param("riskScore", risk.score())
                .param("policy", json(policyFindings))
                .param("risk", json(risk.signals()))
                .param("decidedBy", decidedBy)
                .param("requestId", requestId)
                .param("createdAt", Timestamp.from(now))
                .update();
    }

    @Override
    public void appendAudit(
            UUID caseId,
            String actorSubject,
            String action,
            String resourceType,
            String resourceId,
            String requestId,
            Map<String, Object> metadata,
            Instant now) {
        jdbcClient
                .sql(
                        """
                        INSERT INTO campus_fund_audit_log (
                            id, case_id, actor_subject, actor_type, action,
                            resource_type, resource_id, request_id, metadata, occurred_at
                        ) VALUES (
                            :id, :caseId, :actor, 'USER', :action,
                            :resourceType, :resourceId, :requestId,
                            CAST(:metadata AS jsonb), :occurredAt
                        )
                        """)
                .param("id", UUID.randomUUID())
                .param("caseId", caseId)
                .param("actor", actorSubject)
                .param("action", action)
                .param("resourceType", resourceType)
                .param("resourceId", resourceId)
                .param("requestId", requestId)
                .param("metadata", json(metadata))
                .param("occurredAt", Timestamp.from(now))
                .update();
        Map<String, Object> eventPayload = new java.util.LinkedHashMap<>();
        eventPayload.put("actorSubject", actorSubject);
        eventPayload.put("resourceType", resourceType);
        eventPayload.put("resourceId", resourceId == null ? "" : resourceId);
        eventPayload.put("requestId", requestId == null ? "" : requestId);
        eventPayload.put("metadata", metadata == null ? Map.of() : metadata);
        eventRepository.append(caseId, action, eventPayload, now);
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("审核数据序列化失败", exception);
        }
    }

    private ReviewTask mapTask(ResultSet rs, int row) throws SQLException {
        Timestamp dueAt = rs.getTimestamp("due_at");
        return new ReviewTask(
                rs.getObject("id", UUID.class),
                rs.getObject("case_id", UUID.class),
                rs.getString("status"),
                rs.getString("assignee_subject"),
                readJsonList(rs.getString("reason_codes")),
                rs.getString("routing_action"),
                rs.getString("routing_queue"),
                rs.getString("assignee_role"),
                (Integer) rs.getObject("sla_hours"),
                readJsonList(rs.getString("required_evidence")),
                rs.getString("user_facing_message"),
                rs.getString("fallback_strategy"),
                rs.getBoolean("debate_assist_enabled"),
                rs.getString("reviewer_comment"),
                dueAt == null ? null : dueAt.toInstant(),
                rs.getLong("version"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }

    private List<String> readJsonList(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("审核任务列表字段不是合法 JSON", exception);
        }
    }
}

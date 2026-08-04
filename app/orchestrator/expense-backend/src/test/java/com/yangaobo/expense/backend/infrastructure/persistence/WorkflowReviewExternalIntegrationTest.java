package com.yangaobo.expense.backend.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangaobo.expense.backend.application.CreateExpenseCaseCommand;
import com.yangaobo.expense.backend.application.ExpenseCaseApplicationService;
import com.yangaobo.expense.backend.application.extraction.ExtractedExpenseDocument;
import com.yangaobo.expense.backend.application.extraction.ExtractedExpenseItem;
import com.yangaobo.expense.backend.application.workflow.ExpenseCoordinator;
import com.yangaobo.expense.backend.application.workflow.CaseEvidenceService;
import com.yangaobo.expense.backend.application.event.ExpenseCaseEventRepository;
import com.yangaobo.expense.backend.application.workflow.ExpenseWorkflowCommand;
import com.yangaobo.expense.backend.application.workflow.ReviewApplicationService;
import com.yangaobo.expense.backend.domain.model.ExpenseCase;
import com.yangaobo.expense.backend.domain.risk.RiskSignalCode;
import com.yangaobo.expense.common.domain.ExpenseCaseStatus;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "spring.datasource.hikari.maximum-pool-size=2")
@AutoConfigureMockMvc
@AutoConfigureObservability
@EnabledIfEnvironmentVariable(named = "EXPENSE_IT_DATABASE_URL", matches = ".+")
class WorkflowReviewExternalIntegrationTest {

    @Autowired private ExpenseCaseApplicationService caseService;
    @Autowired private ExpenseCoordinator coordinator;
    @Autowired private ReviewApplicationService reviewService;
    @Autowired private CaseEvidenceService evidenceService;
    @Autowired private JdbcClient jdbcClient;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ExpenseCaseEventRepository eventRepository;
    @Autowired private MockMvc mockMvc;

    private UUID caseId;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> required("EXPENSE_IT_DATABASE_URL"));
        registry.add("spring.datasource.username", () -> required("EXPENSE_IT_DATABASE_USERNAME"));
        registry.add("spring.datasource.password", () -> required("EXPENSE_IT_DATABASE_PASSWORD"));
        registry.add("expense.ai.embedding.provider", () -> "deterministic");
    }

    @AfterEach
    void cleanup() {
        if (caseId != null) {
            jdbcClient.sql("DELETE FROM expense_case WHERE id = :id").param("id", caseId).update();
        }
    }

    @Test
    void shouldPersistWorkflowCreateReviewAndApproveIdempotently() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("MyExpenseAgent API"))
                .andExpect(
                        jsonPath(
                                        "$.paths['/api/v1/fund-applications/{caseId}/events'].get.responses['422']")
                                .exists())
                .andExpect(
                        jsonPath(
                                        "$.components.securitySchemes.sessionCookie")
                                .exists());
        ExpenseCase expenseCase =
                caseService.create(
                        new CreateExpenseCaseCommand(
                                "student01",
                                "流程测试学生",
                                "CS-SRTP",
                                "竞赛差旅审核闭环",
                                new BigDecimal("600.00"),
                                "CNY"));
        caseId = expenseCase.id();
        insertExtractedDocument(caseId);
        caseService.transition(caseId, ExpenseCaseStatus.UPLOADED);
        caseService.transition(caseId, ExpenseCaseStatus.EXTRACTING);
        caseService.transition(caseId, ExpenseCaseStatus.EXTRACTED);

        ExpenseWorkflowCommand command =
                new ExpenseWorkflowCommand(
                        "workflow-it-" + UUID.randomUUID(),
                        "竞赛差旅费",
                        LocalDate.of(2026, 6, 18));
        var first = coordinator.analyze(caseId, "student01", command);
        var replay = coordinator.analyze(caseId, "student01", command);
        boolean expectMcpFailure =
                "true".equalsIgnoreCase(
                        System.getenv("EXPENSE_EXPECT_MCP_FAILURE"));

        assertThat(first.status()).isEqualTo(ExpenseCaseStatus.WAITING_HUMAN);
        assertThat(first.riskScore())
                .isEqualTo(expectMcpFailure ? 60 : 30);
        if (expectMcpFailure) {
            assertThat(first.riskSignals())
                    .anyMatch(
                            signal ->
                                    signal.code()
                                            == RiskSignalCode
                                                    .DEPENDENCY_UNAVAILABLE);
        } else {
            assertThat(first.riskSignals())
                    .extracting(signal -> signal.code())
                    .containsExactly(RiskSignalCode.POLICY_EVIDENCE_MISSING);
        }
        assertThat(first.reviewTaskId()).isNotNull();
        assertThat(replay.runId()).isEqualTo(first.runId());
        assertThat(replay.reviewTaskId()).isEqualTo(first.reviewTaskId());
        assertThat(reviewService.openTasks())
                .anyMatch(task -> task.id().equals(first.reviewTaskId()));
        var evidence =
                evidenceService.get(caseId, "student01", false);
        assertThat(evidence.run()).isNotNull();
        assertThat(evidence.run().status()).isEqualTo("SUCCEEDED");
        assertThat(evidence.risk().score()).isEqualTo(first.riskScore());
        assertThat(evidence.steps())
                .extracting(CaseEvidenceService.StepView::name)
                .contains(
                        "MCP_APPLICANT_CONTEXT",
                        "MCP_DUPLICATE_CHECK",
                        "POLICY_RETRIEVAL",
                        "RISK_ASSESSMENT",
                        "FINALIZE");
        assertThat(evidence.steps())
                .allMatch(step -> !step.evidence().containsKey("inputHash"));

        String reviewRequestId = "review-it-" + UUID.randomUUID();
        ExpenseCase approved =
                reviewService.approve(
                        first.reviewTaskId(),
                        0,
                        new BigDecimal("600.00"),
                        "凭证核验通过",
                        "reviewer-subject",
                        reviewRequestId);
        ExpenseCase replayedApproval =
                reviewService.approve(
                        first.reviewTaskId(),
                        0,
                        new BigDecimal("600.00"),
                        "重复请求不应再次执行",
                        "reviewer-subject",
                        reviewRequestId);

        assertThat(approved.status()).isEqualTo(ExpenseCaseStatus.APPROVED);
        assertThat(replayedApproval.id()).isEqualTo(approved.id());
        var events = eventRepository.findAfter(caseId, 0, 20);
        assertThat(events)
                .extracting(
                        com.yangaobo.expense.common.event
                                        .ExpenseWorkflowEvent
                                ::type)
                .containsExactly("WORKFLOW_COMPLETED", "REVIEW_APPROVED");
        assertThat(events)
                .extracting(
                        com.yangaobo.expense.common.event
                                        .ExpenseWorkflowEvent
                                ::sequence)
                .isSorted();
        assertThat(
                        eventRepository.findSequence(
                                caseId, events.getFirst().eventId()))
                .hasValue(events.getFirst().sequence());
        assertThat(
                        jdbcClient
                                .sql(
                                        "SELECT count(*)::int FROM expense_decision WHERE case_id = :id")
                                .param("id", caseId)
                                .query(Integer.class)
                                .single())
                .isEqualTo(1);
        var succeededSteps =
                jdbcClient
                        .sql(
                                """
                                SELECT step_name
                                FROM expense_agent_step
                                WHERE case_id = :id AND status = 'SUCCEEDED'
                                ORDER BY step_name
                                """)
                        .param("id", caseId)
                        .query(String.class)
                        .list();
        assertThat(
                        jdbcClient
                                .sql(
                                        """
                                        SELECT count(*)::int
                                        FROM expense_workflow_checkpoint
                                        WHERE case_id = :id
                                        """)
                                .param("id", caseId)
                                .query(Integer.class)
                                .single())
                .isEqualTo(succeededSteps.size());
        if (expectMcpFailure) {
            assertThat(succeededSteps)
                    .containsExactly(
                            "EVIDENCE_QUALITY_GATE",
                            "EXECUTION_POLICY",
                            "FINALIZE",
                            "PARALLEL_EVIDENCE_COLLECTION",
                            "POLICY_RETRIEVAL",
                            "RISK_ASSESSMENT",
                            "RISK_ROUTING");
            assertThat(
                            jdbcClient
                                    .sql(
                                            """
                                            SELECT count(*)::int
                                            FROM expense_agent_step
                                            WHERE case_id = :id
                                              AND status = 'FAILED'
                                              AND error_code = 'DEPENDENCY_UNAVAILABLE'
                                            """)
                                    .param("id", caseId)
                                    .query(Integer.class)
                                    .single())
                    .isEqualTo(5);
        } else {
            assertThat(succeededSteps)
                    .containsExactly(
                            "EVIDENCE_QUALITY_GATE",
                            "EXECUTION_POLICY",
                            "FINALIZE",
                            "MCP_APPLICANT_CONTEXT",
                            "MCP_DUPLICATE_CHECK",
                            "MCP_PROJECT_BUDGET",
                            "MCP_REIMBURSEMENT_HISTORY",
                            "MCP_REVIEW_EVIDENCE",
                            "PARALLEL_EVIDENCE_COLLECTION",
                            "POLICY_RETRIEVAL",
                            "RISK_ASSESSMENT",
                            "RISK_ROUTING");
        }
    }

    @Test
    void shouldPersistMoreInfoRequestIdempotently() throws Exception {
        ExpenseCase expenseCase =
                caseService.create(
                        new CreateExpenseCaseCommand(
                                "student01",
                                "流程测试学生",
                                "CS-SRTP",
                                "补件审核闭环",
                                new BigDecimal("600.00"),
                                "CNY"));
        caseId = expenseCase.id();
        insertExtractedDocument(caseId);
        caseService.transition(caseId, ExpenseCaseStatus.UPLOADED);
        caseService.transition(caseId, ExpenseCaseStatus.EXTRACTING);
        caseService.transition(caseId, ExpenseCaseStatus.EXTRACTED);

        var workflow =
                coordinator.analyze(
                        caseId,
                        "student01",
                        new ExpenseWorkflowCommand(
                                "more-info-workflow-it-" + UUID.randomUUID(),
                                "竞赛差旅费",
                                LocalDate.of(2026, 6, 18)));
        String requestId = "more-info-it-" + UUID.randomUUID();
        ExpenseCase moreInfo =
                reviewService.requestMoreInfo(
                        workflow.reviewTaskId(),
                        0,
                        "请补充竞赛通知和住宿入住人清单",
                        "reviewer-subject",
                        requestId);
        ExpenseCase replay =
                reviewService.requestMoreInfo(
                        workflow.reviewTaskId(),
                        0,
                        "重复请求不应再次执行",
                        "reviewer-subject",
                        requestId);

        assertThat(moreInfo.status()).isEqualTo(ExpenseCaseStatus.WAITING_MORE_INFO);
        assertThat(replay.id()).isEqualTo(moreInfo.id());
        assertThat(reviewService.get(workflow.reviewTaskId()).status()).isEqualTo("MORE_INFO");
        assertThat(
                        jdbcClient
                                .sql(
                                        """
                                        SELECT count(*)::int
                                        FROM expense_more_info_task
                                        WHERE case_id = :id AND status = 'OPEN'
                                        """)
                                .param("id", caseId)
                                .query(Integer.class)
                                .single())
                .isEqualTo(1);
        assertThat(
                        jdbcClient
                                .sql(
                                        """
                                        SELECT count(*)::int
                                        FROM my_expense_agent_audit_log
                                        WHERE case_id = :id
                                          AND action = 'REVIEW_MORE_INFO_REQUESTED'
                                        """)
                                .param("id", caseId)
                                .query(Integer.class)
                                .single())
                .isEqualTo(1);
        assertThat(eventRepository.findAfter(caseId, 0, 20))
                .extracting(
                        com.yangaobo.expense.common.event
                                        .ExpenseWorkflowEvent
                                ::type)
                .containsExactly("WORKFLOW_COMPLETED", "REVIEW_MORE_INFO_REQUESTED");
    }

    private void insertExtractedDocument(UUID targetCaseId) throws Exception {
        UUID documentId = UUID.randomUUID();
        String documentFingerprint =
                documentId.toString().replace("-", "").repeat(2);
        Instant now = Instant.now();
        ExtractedExpenseDocument extraction =
                new ExtractedExpenseDocument(
                        "HOTEL_INVOICE",
                        "INV",
                        documentId.toString().replace("-", ""),
                        "测试酒店",
                        "江南大学",
                        LocalDate.of(2026, 6, 18),
                        new BigDecimal("600.00"),
                        "CNY",
                        List.of(
                                new ExtractedExpenseItem(
                                        "竞赛住宿一晚",
                                        BigDecimal.ONE,
                                        new BigDecimal("600.00"),
                                        new BigDecimal("600.00"))),
                        0.95,
                        List.of());
        jdbcClient
                .sql(
                        """
                        INSERT INTO expense_document (
                            id, case_id, original_filename, content_type, file_size,
                            sha256, object_key, document_type, extraction_confidence,
                            extraction_result, validation_errors, model_name,
                            prompt_version, raw_response_hash, created_at, updated_at
                        ) VALUES (
                            :id, :caseId, 'workflow-test.pdf', 'application/pdf', 100,
                            :sha256, :objectKey, 'HOTEL_INVOICE', 0.95,
                            CAST(:result AS jsonb), '[]'::jsonb, 'integration-test',
                            'integration-v1', :responseHash, :createdAt, :updatedAt
                        )
                        """)
                .param("id", documentId)
                .param("caseId", targetCaseId)
                .param("sha256", documentFingerprint)
                .param("objectKey", targetCaseId + "/" + documentId + ".pdf")
                .param("result", objectMapper.writeValueAsString(extraction))
                .param("responseHash", "b".repeat(64))
                .param("createdAt", Timestamp.from(now))
                .param("updatedAt", Timestamp.from(now))
                .update();
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required");
        }
        return value;
    }
}

package com.yangaobo.expense.backend.infrastructure.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yangaobo.expense.backend.application.ExpenseCaseApplicationService;
import com.yangaobo.expense.backend.application.evaluation.AgentSecurityEvaluationService;
import com.yangaobo.expense.backend.application.evaluation.PolicyRagEvaluationService;
import com.yangaobo.expense.backend.application.evaluation.RiskEvaluationService;
import com.yangaobo.expense.backend.application.observability.CaseAuditRepository;
import com.yangaobo.expense.backend.application.observability.ModelCallRepository;
import com.yangaobo.expense.backend.application.policy.PolicyImportResult;
import com.yangaobo.expense.backend.application.policy.PolicyRetrievalService;
import com.yangaobo.expense.backend.application.prompt.PromptChangeRequest;
import com.yangaobo.expense.backend.application.prompt.PromptChangeRequestStatus;
import com.yangaobo.expense.backend.application.prompt.PromptChangeRequestType;
import com.yangaobo.expense.backend.application.prompt.PromptGovernanceService;
import com.yangaobo.expense.backend.application.prompt.PromptStatus;
import com.yangaobo.expense.backend.application.prompt.PromptTemplate;
import com.yangaobo.expense.backend.application.settlement.ExpenseSettlementService;
import com.yangaobo.expense.backend.application.workflow.WorkflowRunRepository;
import com.yangaobo.expense.backend.domain.model.PolicyStatus;
import com.yangaobo.expense.backend.domain.repository.PolicyCatalogEntry;
import com.yangaobo.expense.backend.interfaces.rest.PolicyController;
import com.yangaobo.expense.backend.interfaces.rest.EvaluationController;
import com.yangaobo.expense.backend.interfaces.rest.ObservabilityController;
import com.yangaobo.expense.backend.interfaces.rest.PromptGovernanceController;
import com.yangaobo.expense.backend.interfaces.rest.SettlementController;
import com.yangaobo.expense.backend.interfaces.rest.SystemController;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({
    SystemController.class,
    PolicyController.class,
    EvaluationController.class,
    ObservabilityController.class,
    PromptGovernanceController.class,
    SettlementController.class
})
@Import({SecurityConfiguration.class, SecurityErrorWriter.class})
class SecurityAuthorizationTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private JwtDecoder jwtDecoder;
    @MockitoBean private PolicyRetrievalService policyService;
    @MockitoBean private PolicyRagEvaluationService policyRagEvaluationService;
    @MockitoBean private AgentSecurityEvaluationService agentSecurityEvaluationService;
    @MockitoBean private RiskEvaluationService riskEvaluationService;
    @MockitoBean private ModelCallRepository modelCallRepository;
    @MockitoBean private CaseAuditRepository caseAuditRepository;
    @MockitoBean private WorkflowRunRepository workflowRunRepository;
    @MockitoBean private ExpenseCaseApplicationService expenseCaseApplicationService;
    @MockitoBean private ExpenseSettlementService settlementService;
    @MockitoBean private PromptGovernanceService promptGovernanceService;

    @Test
    void systemEndpointShouldRemainPublic() throws Exception {
        mockMvc.perform(get("/api/v1/system")).andExpect(status().isOk());
    }

    @Test
    void policySearchShouldRequireAuthentication() throws Exception {
        mockMvc.perform(
                        get("/api/v1/policies/search")
                                .param("query", "住宿上限")
                                .param("category", "住宿费")
                                .param("region", "CN")
                                .param("employeeGrade", "G6"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void reviewerShouldListPolicyCatalog() throws Exception {
        when(policyService.listCatalog())
                .thenReturn(
                        java.util.List.of(
                                new PolicyCatalogEntry(
                                        UUID.fromString(
                                                "00000000-0000-0000-0000-000000000001"),
                                        "HOTEL-CN",
                                        "住宿费制度",
                                        "住宿费",
                                        "CN",
                                        "ALL",
                                        "1.0",
                                        java.time.LocalDate.of(2026, 1, 1),
                                        null,
                                        PolicyStatus.ACTIVE,
                                        "policy://expense-flow/HOTEL-CN-V1",
                                        3,
                                        3,
                                        java.time.Instant.parse("2026-06-22T00:00:00Z"))));

        mockMvc.perform(
                        get("/api/v1/policies")
                                .with(
                                        jwt()
                                                .authorities(
                                                        new SimpleGrantedAuthority(
                                                                "ROLE_REVIEWER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].policyCode").value("HOTEL-CN"))
                .andExpect(jsonPath("$[0].indexedChunkCount").value(3));
    }

    @Test
    void employeeShouldNotReadEvaluationReport() throws Exception {
        mockMvc.perform(
                        get("/api/v1/evaluation/risk-report")
                                .with(
                                        jwt()
                                                .authorities(
                                                        new SimpleGrantedAuthority(
                                                                "ROLE_EMPLOYEE"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void employeeShouldNotReadObservableRuns() throws Exception {
        mockMvc.perform(
                        get("/api/v1/observability/runs")
                                .with(
                                        jwt()
                                                .authorities(
                                                        new SimpleGrantedAuthority(
                                                                "ROLE_EMPLOYEE"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void employeeShouldNotImportPolicy() throws Exception {
        mockMvc.perform(
                        post("/api/v1/policies")
                                .with(
                                        jwt()
                                                .authorities(
                                                        new SimpleGrantedAuthority(
                                                                "ROLE_EMPLOYEE")))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(validPolicyJson()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void financeAdminShouldImportPolicy() throws Exception {
        when(policyService.importPolicy(any()))
                .thenReturn(
                        new PolicyImportResult(
                                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                                "HOTEL-CN",
                                "1.0",
                                2,
                                "test-embedding",
                                "a".repeat(64)));

        mockMvc.perform(
                        post("/api/v1/policies")
                                .with(
                                        jwt()
                                                .authorities(
                                                        new SimpleGrantedAuthority(
                                                                "ROLE_FINANCE_ADMIN")))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(validPolicyJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.policyCode").value("HOTEL-CN"));
    }

    @Test
    void reviewerShouldNotSettleApprovedCase() throws Exception {
        mockMvc.perform(
                        post(
                                        "/api/v1/expense-cases/{caseId}/settlement",
                                        UUID.randomUUID())
                                .with(
                                        jwt()
                                                .authorities(
                                                        new SimpleGrantedAuthority(
                                                                "ROLE_REVIEWER")))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"requestId\":\"settle-1\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void financeAdminShouldSettleApprovedCase() throws Exception {
        UUID caseId = UUID.randomUUID();
        when(settlementService.settle(
                        eq(caseId), eq("settle-1"), any()))
                .thenReturn(
                        new ExpenseSettlementService.SettlementResult(
                                caseId,
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                new java.math.BigDecimal("500.00"),
                                "CNY",
                                "SIMULATED_PAID"));

        mockMvc.perform(
                        post(
                                        "/api/v1/expense-cases/{caseId}/settlement",
                                        caseId)
                                .with(
                                        jwt()
                                                .authorities(
                                                        new SimpleGrantedAuthority(
                                                                "ROLE_FINANCE_ADMIN")))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"requestId\":\"settle-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SIMULATED_PAID"));
    }

    @Test
    void promptAuthorShouldCreateButNotApprovePrompt() throws Exception {
        when(promptGovernanceService.createDraft(
                        any(), any(), any(), any(), any(), any(), any(), any(), anyInt(), any()))
                .thenReturn(promptTemplate(PromptStatus.DRAFT));

        mockMvc.perform(
                        post("/api/v1/prompts")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_PROMPT_AUTHOR")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validPromptJson()))
                .andExpect(status().isCreated());

        mockMvc.perform(
                        post("/api/v1/prompts/changes/{id}/approve", UUID.randomUUID())
                                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_PROMPT_AUTHOR")))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"comment\":\"通过\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void promptReviewerShouldApprovePromptChange() throws Exception {
        when(promptGovernanceService.approve(any(), any(), any()))
                .thenReturn(promptChange(PromptChangeRequestStatus.APPROVED));

        mockMvc.perform(
                        post("/api/v1/prompts/changes/{id}/approve", UUID.randomUUID())
                                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_PROMPT_REVIEWER")))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"comment\":\"通过\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void promptPublisherShouldActivatePrompt() throws Exception {
        when(promptGovernanceService.activate(any(), any()))
                .thenReturn(promptTemplate(PromptStatus.ACTIVE));

        mockMvc.perform(
                        post("/api/v1/prompts/{id}/activate", UUID.randomUUID())
                                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_PROMPT_PUBLISHER"))))
                .andExpect(status().isOk());
    }

    private static String validPolicyJson() {
        return """
                {
                  "policyCode": "HOTEL-CN",
                  "name": "住宿费制度",
                  "category": "住宿费",
                  "region": "CN",
                  "employeeGrade": "ALL",
                  "version": "1.0",
                  "effectiveFrom": "2026-01-01",
                  "status": "ACTIVE",
                  "sourceUri": "policy://expense-flow/HOTEL-CN-V1",
                  "markdownContent": "# 住宿费制度\\n\\n## 金额上限\\n\\n每晚六百元。"
                }
                """;
    }

    private static String validPromptJson() {
        return """
                {
                  "promptKey": "review-report",
                  "version": "review-report-v9",
                  "name": "审核报告 V9",
                  "description": "测试",
                  "content": "Summarize {{evidence}}.",
                  "variableSchema": {},
                  "modelName": "qwen-plus",
                  "temperature": 0,
                  "maxTokens": 1024
                }
                """;
    }

    private static PromptTemplate promptTemplate(PromptStatus status) {
        java.time.Instant now = java.time.Instant.parse("2026-06-26T00:00:00Z");
        return new PromptTemplate(
                UUID.randomUUID(),
                "review-report",
                "review-report-v9",
                "审核报告 V9",
                "测试",
                "Summarize {{evidence}}.",
                java.util.Map.of(),
                "qwen-plus",
                java.math.BigDecimal.ZERO,
                1024,
                status,
                "a".repeat(64),
                "author",
                "author",
                status == PromptStatus.ACTIVE ? "reviewer" : null,
                now,
                now,
                status == PromptStatus.ACTIVE ? now : null,
                status == PromptStatus.ACTIVE ? now : null,
                null);
    }

    private static PromptChangeRequest promptChange(PromptChangeRequestStatus status) {
        java.time.Instant now = java.time.Instant.parse("2026-06-26T00:00:00Z");
        return new PromptChangeRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                PromptChangeRequestType.UPDATE,
                status,
                "测试变更",
                "LOW",
                java.util.Map.of("passed", true),
                "通过",
                "author",
                "reviewer",
                now,
                now);
    }
}

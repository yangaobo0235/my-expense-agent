package com.yangaobo.expense.backend.infrastructure.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yangaobo.expense.backend.application.ExpenseCaseApplicationService;
import com.yangaobo.expense.backend.application.document.DocumentQueryService;
import com.yangaobo.expense.backend.application.document.DocumentUploadService;
import com.yangaobo.expense.backend.application.evaluation.RiskEvaluationService;
import com.yangaobo.expense.backend.application.extraction.ExpenseExtractionService;
import com.yangaobo.expense.backend.application.observability.CaseAuditRepository;
import com.yangaobo.expense.backend.application.observability.ModelCallRepository;
import com.yangaobo.expense.backend.application.policy.PolicyImportResult;
import com.yangaobo.expense.backend.application.policy.PolicyRetrievalService;
import com.yangaobo.expense.backend.application.settlement.ExpenseSettlementService;
import com.yangaobo.expense.backend.application.settlement.ToolCallRepository;
import com.yangaobo.expense.backend.application.workflow.ExpenseCoordinator;
import com.yangaobo.expense.backend.application.workflow.MoreInfoApplicationService;
import com.yangaobo.expense.backend.application.workflow.WorkflowRunRepository;
import com.yangaobo.expense.backend.domain.model.PolicyStatus;
import com.yangaobo.expense.backend.domain.repository.PolicyCatalogEntry;
import com.yangaobo.expense.backend.interfaces.rest.EvaluationController;
import com.yangaobo.expense.backend.interfaces.rest.ExpenseCaseController;
import com.yangaobo.expense.backend.interfaces.rest.MoreInfoController;
import com.yangaobo.expense.backend.interfaces.rest.ObservabilityController;
import com.yangaobo.expense.backend.interfaces.rest.PolicyController;
import com.yangaobo.expense.backend.interfaces.rest.SettlementController;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({
    PolicyController.class,
    EvaluationController.class,
    ObservabilityController.class,
    SettlementController.class,
    ExpenseCaseController.class,
    MoreInfoController.class
})
@Import({SecurityConfiguration.class, SecurityErrorWriter.class})
class SecurityAuthorizationTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private UserDetailsService userDetailsService;
    @MockitoBean private PolicyRetrievalService policyService;
    @MockitoBean private RiskEvaluationService riskEvaluationService;
    @MockitoBean private ModelCallRepository modelCallRepository;
    @MockitoBean private CaseAuditRepository caseAuditRepository;
    @MockitoBean private WorkflowRunRepository workflowRunRepository;
    @MockitoBean private ExpenseCaseApplicationService expenseCaseApplicationService;
    @MockitoBean private DocumentUploadService documentUploadService;
    @MockitoBean private DocumentQueryService documentQueryService;
    @MockitoBean private ExpenseExtractionService expenseExtractionService;
    @MockitoBean private ExpenseCoordinator expenseCoordinator;
    @MockitoBean private MoreInfoApplicationService moreInfoApplicationService;
    @MockitoBean private ToolCallRepository toolCallRepository;
    @MockitoBean private ExpenseSettlementService settlementService;

    @Test
    void policySearchShouldRequireAuthentication() throws Exception {
        mockMvc.perform(
                        get("/api/v1/policies/search")
                                .param("query", "竞赛住宿上限")
                                .param("category", "竞赛差旅费")
                                .param("region", "CN")
                                .param("applicantType", "STUDENT"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void reviewerShouldListPolicyCatalog() throws Exception {
        when(policyService.listCatalog())
                .thenReturn(
                        List.of(
                                new PolicyCatalogEntry(
                                        UUID.fromString("00000000-0000-0000-0000-000000000001"),
                                        "COMPETITION-TRAVEL",
                                        "学生竞赛差旅经费管理办法",
                                        "竞赛差旅费",
                                        "CN",
                                        "ALL",
                                        "1.0",
                                        LocalDate.of(2026, 1, 1),
                                        null,
                                        PolicyStatus.ACTIVE,
                                        "policy://expense/COMPETITION-TRAVEL-V1",
                                        3,
                                        3,
                                        Instant.parse("2026-06-22T00:00:00Z"))));

        mockMvc.perform(
                        get("/api/v1/policies")
                                .with(user("reviewer01").roles("COLLEGE_REVIEWER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].policyCode").value("COMPETITION-TRAVEL"));
    }

    @Test
    void studentShouldNotReadEvaluationReport() throws Exception {
        mockMvc.perform(
                        get("/api/v1/evaluations/risk/latest")
                                .with(user("student01").roles("STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    void stateChangingRequestShouldRequireCsrf() throws Exception {
        mockMvc.perform(
                        post("/api/v1/policies")
                                .with(user("finance01").roles("FINANCE_ADMIN"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(validPolicyJson()))
                .andExpect(status().isForbidden());
    }

    @Test
    void studentShouldNotImportPolicy() throws Exception {
        mockMvc.perform(
                        post("/api/v1/policies")
                                .with(user("student01").roles("STUDENT"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(validPolicyJson()))
                .andExpect(status().isForbidden());
    }

    @Test
    void financeAdminShouldImportPolicy() throws Exception {
        when(policyService.importPolicy(any()))
                .thenReturn(
                        new PolicyImportResult(
                                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                                "COMPETITION-TRAVEL",
                                "1.0",
                                2,
                                "test-embedding",
                                "a".repeat(64)));

        mockMvc.perform(
                        post("/api/v1/policies")
                                .with(user("finance01").roles("FINANCE_ADMIN"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(validPolicyJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.policyCode").value("COMPETITION-TRAVEL"));
    }

    @Test
    void onlyFinanceAdminShouldPostApprovedFunds() throws Exception {
        UUID caseId = UUID.randomUUID();
        mockMvc.perform(
                        post("/api/v1/fund-applications/{caseId}/posting", caseId)
                                .with(user("advisor01").roles("ADVISOR"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"requestId\":\"posting-advisor-1\"}"))
                .andExpect(status().isForbidden());

        when(settlementService.settle(any(), any(), any()))
                .thenReturn(
                        new ExpenseSettlementService.SettlementResult(
                                caseId,
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                List.of(UUID.randomUUID()),
                                new java.math.BigDecimal("500.00"),
                                "CNY",
                                "SIMULATED_PAID"));
        mockMvc.perform(
                        post("/api/v1/fund-applications/{caseId}/posting", caseId)
                                .with(user("finance01").roles("FINANCE_ADMIN"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"requestId\":\"settle-1\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void onlyReviewersShouldRequestMoreInfo() throws Exception {
        UUID caseId = UUID.randomUUID();
        String body =
                """
                {"requiredMaterials":["住宿明细"],"reasonCodes":["MISSING_REQUIRED_DOCUMENT"]}
                """;

        mockMvc.perform(
                        post("/api/v1/expense-cases/{caseId}/more-info-requests", caseId)
                                .with(user("student01").roles("STUDENT"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isForbidden());

        mockMvc.perform(
                        post("/api/v1/expense-cases/{caseId}/more-info-requests", caseId)
                                .with(user("reviewer01").roles("COLLEGE_REVIEWER"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isOk());
    }

    private static String validPolicyJson() {
        return """
                {
                  "policyCode": "COMPETITION-TRAVEL",
                  "name": "学生竞赛差旅经费管理办法",
                  "category": "竞赛差旅费",
                  "region": "CN",
                  "applicantType": "ALL",
                  "version": "1.0",
                  "effectiveFrom": "2026-01-01",
                  "status": "ACTIVE",
                  "sourceUri": "policy://expense/COMPETITION-TRAVEL-V1",
                  "markdownContent": "# 学生竞赛差旅经费管理办法\\n\\n每晚三百五十元。"
                }
                """;
    }
}

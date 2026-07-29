package com.yangaobo.expense.backend.interfaces.rest;

import com.yangaobo.expense.backend.application.evaluation.AgentSecurityEvaluationReport;
import com.yangaobo.expense.backend.application.evaluation.AgentSecurityEvaluationService;
import com.yangaobo.expense.backend.application.evaluation.PolicyRagEvaluationReport;
import com.yangaobo.expense.backend.application.evaluation.PolicyRagEvaluationService;
import com.yangaobo.expense.backend.application.evaluation.RiskEvaluationReport;
import com.yangaobo.expense.backend.application.evaluation.RiskEvaluationService;
import com.yangaobo.expense.backend.application.evaluation.ExtractionEvaluationReport;
import com.yangaobo.expense.backend.application.evaluation.ExtractionEvaluationService;
import java.security.Principal;
import java.util.Optional;
import com.yangaobo.expense.common.error.MyExpenseAgentErrorCode;
import com.yangaobo.expense.common.error.MyExpenseAgentException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/v1/evaluation", "/api/v1/evaluations"})
public class EvaluationController {

    private final RiskEvaluationService evaluationService;
    private final PolicyRagEvaluationService policyRagEvaluationService;
    private final AgentSecurityEvaluationService agentSecurityEvaluationService;
    private final Optional<ExtractionEvaluationService> extractionEvaluationService;

    public EvaluationController(
            RiskEvaluationService evaluationService,
            PolicyRagEvaluationService policyRagEvaluationService,
            AgentSecurityEvaluationService agentSecurityEvaluationService,
            Optional<ExtractionEvaluationService> extractionEvaluationService) {
        this.evaluationService = evaluationService;
        this.policyRagEvaluationService = policyRagEvaluationService;
        this.agentSecurityEvaluationService = agentSecurityEvaluationService;
        this.extractionEvaluationService = extractionEvaluationService;
    }

    @GetMapping("/risk-report")
    public RiskEvaluationReport riskReport(Principal principal) {
        return evaluationService.evaluate();
    }

    @GetMapping("/policy-rag-report")
    public PolicyRagEvaluationReport policyRagReport(Principal principal) {
        return policyRagEvaluationService.evaluate();
    }

    @GetMapping("/agent-security-report")
    public AgentSecurityEvaluationReport agentSecurityReport(Principal principal) {
        return agentSecurityEvaluationService.evaluate();
    }

    @GetMapping({"/extraction/latest", "/extraction-report"})
    public ExtractionEvaluationReport extractionReport(Principal principal) {
        return extractionEvaluationService
                .orElseThrow(
                        () ->
                                new MyExpenseAgentException(
                                        MyExpenseAgentErrorCode.DEPENDENCY_UNAVAILABLE,
                                        "抽取评测服务当前不可用"))
                .evaluate();
    }
}

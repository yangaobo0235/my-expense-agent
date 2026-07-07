package com.yangaobo.expense.backend.interfaces.rest;

import com.yangaobo.expense.backend.application.evaluation.AgentSecurityEvaluationReport;
import com.yangaobo.expense.backend.application.evaluation.AgentSecurityEvaluationService;
import com.yangaobo.expense.backend.application.evaluation.PolicyRagEvaluationReport;
import com.yangaobo.expense.backend.application.evaluation.PolicyRagEvaluationService;
import com.yangaobo.expense.backend.application.evaluation.RiskEvaluationReport;
import com.yangaobo.expense.backend.application.evaluation.RiskEvaluationService;
import java.security.Principal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/evaluation")
public class EvaluationController {

    private final RiskEvaluationService evaluationService;
    private final PolicyRagEvaluationService policyRagEvaluationService;
    private final AgentSecurityEvaluationService agentSecurityEvaluationService;

    public EvaluationController(
            RiskEvaluationService evaluationService,
            PolicyRagEvaluationService policyRagEvaluationService,
            AgentSecurityEvaluationService agentSecurityEvaluationService) {
        this.evaluationService = evaluationService;
        this.policyRagEvaluationService = policyRagEvaluationService;
        this.agentSecurityEvaluationService = agentSecurityEvaluationService;
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
}

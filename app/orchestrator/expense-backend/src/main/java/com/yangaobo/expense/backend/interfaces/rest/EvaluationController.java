package com.yangaobo.expense.backend.interfaces.rest;

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
@RequestMapping("/api/v1/evaluations")
public class EvaluationController {

    private final RiskEvaluationService evaluationService;
    private final Optional<ExtractionEvaluationService> extractionEvaluationService;

    public EvaluationController(
            RiskEvaluationService evaluationService,
            Optional<ExtractionEvaluationService> extractionEvaluationService) {
        this.evaluationService = evaluationService;
        this.extractionEvaluationService = extractionEvaluationService;
    }

    @GetMapping("/risk/latest")
    public RiskEvaluationReport riskReport(Principal principal) {
        return evaluationService.evaluate();
    }

    @GetMapping("/extraction/latest")
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

package com.yangaobo.expense.backend.application.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangaobo.expense.agents.AgentFailurePolicy;
import com.yangaobo.expense.agents.AgentRole;
import com.yangaobo.expense.agents.ExpenseMultiAgentPlan;
import com.yangaobo.expense.agents.ExpenseMultiAgentPlanner;
import com.yangaobo.expense.backend.domain.risk.DeterministicRiskEngine;
import com.yangaobo.expense.backend.domain.risk.RiskAssessment;
import com.yangaobo.expense.backend.domain.risk.RiskAssessmentInput;
import com.yangaobo.expense.backend.domain.risk.RiskSignal;
import com.yangaobo.expense.common.error.MyExpenseAgentErrorCode;
import com.yangaobo.expense.common.error.MyExpenseAgentException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class RiskEvaluationService {

    static final String ENGINE_VERSION = "deterministic-risk-v1";

    private final ObjectMapper objectMapper;
    private final DeterministicRiskEngine engine;
    private final Clock clock;
    private final String datasetLocation;
    private final ExpenseMultiAgentPlanner agentPlanner;

    public RiskEvaluationService(
            ObjectMapper objectMapper,
            DeterministicRiskEngine engine,
            Clock clock,
            @Value("${expense.evaluation.risk-dataset:classpath:evaluation/cases/risk-golden-v2.json}")
                    String datasetLocation) {
        this.objectMapper = objectMapper;
        this.engine = engine;
        this.clock = clock;
        this.datasetLocation = datasetLocation;
        this.agentPlanner = new ExpenseMultiAgentPlanner();
    }

    public RiskEvaluationReport evaluate() {
        byte[] bytes = readDataset();
        RiskEvaluationDataset dataset;
        try {
            dataset = objectMapper.readValue(bytes, RiskEvaluationDataset.class);
        } catch (java.io.IOException exception) {
            throw unavailable("风险评测数据集格式无效", exception);
        }
        if (dataset.cases() == null || dataset.cases().isEmpty()) {
            throw unavailable("风险评测数据集不能为空", null);
        }

        int truePositive = 0;
        int falsePositive = 0;
        int falseNegative = 0;
        int correctLevels = 0;
        int correctReview = 0;
        int expectedHigh = 0;
        int missedHigh = 0;
        int reviewTriggered = 0;
        Map<String, Integer> categoryCounts = new LinkedHashMap<>();
        List<RiskEvaluationReport.Failure> failures = new ArrayList<>();

        for (RiskEvaluationDataset.RiskEvaluationCase testCase : dataset.cases()) {
            RiskAssessment assessment = engine.assess(input(testCase));
            Set<String> expected = new LinkedHashSet<>(testCase.expectedSignals());
            Set<String> actual =
                    assessment.signals().stream()
                            .map(RiskSignal::code)
                            .map(Enum::name)
                            .collect(
                                    java.util.stream.Collectors.toCollection(
                                            LinkedHashSet::new));
            truePositive += intersectionSize(expected, actual);
            falsePositive += differenceSize(actual, expected);
            falseNegative += differenceSize(expected, actual);
            boolean levelCorrect = assessment.level().name().equals(testCase.expectedRiskLevel());
            boolean reviewCorrect =
                    assessment.requiresHumanReview() == testCase.expectedHumanReview();
            correctLevels += levelCorrect ? 1 : 0;
            correctReview += reviewCorrect ? 1 : 0;
            reviewTriggered += assessment.requiresHumanReview() ? 1 : 0;
            if ("HIGH".equals(testCase.expectedRiskLevel())) {
                expectedHigh++;
                missedHigh += assessment.level().name().equals("HIGH") ? 0 : 1;
            }
            categoryCounts.merge(testCase.category(), 1, Integer::sum);
            if (!expected.equals(actual) || !levelCorrect || !reviewCorrect) {
                failures.add(
                        new RiskEvaluationReport.Failure(
                                testCase.id(),
                                List.copyOf(expected),
                                List.copyOf(actual),
                                testCase.expectedRiskLevel(),
                                assessment.level().name(),
                                testCase.expectedHumanReview(),
                                assessment.requiresHumanReview()));
            }
        }

        int count = dataset.cases().size();
        double precision = ratio(truePositive, truePositive + falsePositive);
        double recall = ratio(truePositive, truePositive + falseNegative);
        double f1 = precision + recall == 0 ? 0 : 2 * precision * recall / (precision + recall);
        return new RiskEvaluationReport(
                dataset.datasetVersion(),
                sha256(bytes),
                ENGINE_VERSION,
                clock.instant(),
                count,
                Map.copyOf(categoryCounts),
                new RiskEvaluationReport.Metrics(
                        precision,
                        recall,
                        f1,
                        ratio(correctLevels, count),
                        ratio(correctReview, count),
                        ratio(missedHigh, expectedHigh),
                        ratio(reviewTriggered, count)),
                agentGovernance(),
                List.copyOf(failures));
    }

    private RiskEvaluationReport.AgentGovernance agentGovernance() {
        ExpenseMultiAgentPlan plan = agentPlanner.plan("evaluation-baseline", "evaluation-request");
        int totalAgents = plan.steps().size();
        int writeAgents =
                (int) plan.steps().stream().filter(step -> step.writeOperationAllowed()).count();
        int idempotentWriteAgents =
                (int)
                        plan.steps().stream()
                                .filter(step -> step.writeOperationAllowed())
                                .filter(
                                        step ->
                                                step.failurePolicy()
                                                        == AgentFailurePolicy
                                                                .IDEMPOTENT_WRITE_RETRY)
                                .count();
        boolean writeToolIsolationPassed =
                writeAgents == 1
                        && plan.steps().stream()
                                .filter(step -> step.writeOperationAllowed())
                                .allMatch(step -> step.role() == AgentRole.APPROVED_SETTLEMENT_AGENT);
        boolean settlementWriteRetryProtected =
                plan.steps().stream()
                        .filter(step -> step.role() == AgentRole.APPROVED_SETTLEMENT_AGENT)
                        .allMatch(
                                step ->
                                        step.writeOperationAllowed()
                                                && step.failurePolicy()
                                                        == AgentFailurePolicy
                                                                .IDEMPOTENT_WRITE_RETRY
                                                && step.maxAttempts() >= 3);
        long handoffCovered =
                plan.steps().stream()
                        .filter(step -> !step.handoffTarget().isBlank())
                        .count();
        long retryableAgents =
                plan.steps().stream()
                        .filter(
                                step ->
                                        step.failurePolicy()
                                                        == AgentFailurePolicy
                                                                .RETRY_THEN_HUMAN_REVIEW
                                                || step.failurePolicy()
                                                        == AgentFailurePolicy
                                                                .IDEMPOTENT_WRITE_RETRY)
                        .count();
        return new RiskEvaluationReport.AgentGovernance(
                plan.planVersion(),
                totalAgents,
                writeAgents,
                idempotentWriteAgents,
                writeToolIsolationPassed,
                settlementWriteRetryProtected,
                ratio((int) handoffCovered, totalAgents),
                ratio((int) retryableAgents, totalAgents));
    }

    private byte[] readDataset() {
        return EvaluationDatasetLoader.datasetBytes(
                objectMapper,
                datasetLocation,
                "risk",
                "无法读取风险评测数据集");
    }

    private static RiskAssessmentInput input(
            RiskEvaluationDataset.RiskEvaluationCase testCase) {
        return new RiskAssessmentInput(
                testCase.claimedAmount(),
                testCase.extractedAmount(),
                testCase.extractionConfidence(),
                testCase.duplicateDocument(),
                testCase.dateAnomaly(),
                testCase.sellerAnomaly(),
                testCase.policyLimitExceeded(),
                testCase.missingRequiredDocument(),
                testCase.forbiddenExpenseItem(),
                testCase.projectBudgetExceeded(),
                testCase.policyEvidenceMissing(),
                testCase.promptInjectionDetected());
    }

    private static int intersectionSize(Set<String> left, Set<String> right) {
        return (int) left.stream().filter(right::contains).count();
    }

    private static int differenceSize(Set<String> left, Set<String> right) {
        return (int) left.stream().filter(value -> !right.contains(value)).count();
    }

    private static double ratio(int numerator, int denominator) {
        return denominator == 0 ? 0 : (double) numerator / denominator;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK 缺少 SHA-256 算法", exception);
        }
    }

    private static MyExpenseAgentException unavailable(String message, Exception cause) {
        return new MyExpenseAgentException(
                MyExpenseAgentErrorCode.DEPENDENCY_UNAVAILABLE,
                cause == null ? message : message + "：" + cause.getMessage());
    }
}

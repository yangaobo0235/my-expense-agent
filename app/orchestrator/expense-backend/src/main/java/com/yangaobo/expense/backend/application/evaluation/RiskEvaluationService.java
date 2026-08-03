package com.yangaobo.expense.backend.application.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
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

    public RiskEvaluationService(
            ObjectMapper objectMapper,
            DeterministicRiskEngine engine,
            Clock clock,
            @Value("${expense.evaluation.risk-dataset:classpath:evaluation/cases/risk-golden-v3.json}")
                    String datasetLocation) {
        this.objectMapper = objectMapper;
        this.engine = engine;
        this.clock = clock;
        this.datasetLocation = datasetLocation;
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

        int correctLevels = 0;
        int correctReview = 0;
        int expectedHigh = 0;
        int matchedHigh = 0;
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
            boolean levelCorrect = assessment.level().name().equals(testCase.expectedRiskLevel());
            boolean reviewCorrect =
                    assessment.requiresHumanReview() == testCase.expectedHumanReview();
            correctLevels += levelCorrect ? 1 : 0;
            correctReview += reviewCorrect ? 1 : 0;
            if ("HIGH".equals(testCase.expectedRiskLevel())) {
                expectedHigh++;
                matchedHigh += assessment.level().name().equals("HIGH") ? 1 : 0;
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
        return new RiskEvaluationReport(
                dataset.datasetVersion(),
                sha256(bytes),
                ENGINE_VERSION,
                clock.instant(),
                count,
                Map.copyOf(categoryCounts),
                new RiskEvaluationReport.Metrics(
                        ratio(correctLevels, count),
                        ratio(correctReview, count),
                        ratio(matchedHigh, expectedHigh)),
                List.copyOf(failures));
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
